package org.commonlink.service

import org.commonlink.config.OnchainConfig
import org.commonlink.dto.OnchainControlEntryDto
import org.commonlink.dto.OnchainPauseResultDto
import org.commonlink.dto.OnchainRegistryStatusDto
import org.commonlink.dto.OnchainUnpauseResultDto
import org.commonlink.entity.ComplianceAuditSubjectType
import org.commonlink.exception.BadGatewayException
import org.commonlink.exception.ConflictException
import org.commonlink.exception.UnprocessableEntityException
import org.commonlink.onchain.ExplorerLinkBuilder
import org.commonlink.onchain.OnchainPauser
import org.commonlink.onchain.OnchainRegistry
import org.commonlink.repository.UserRepository
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.ObjectProvider
import org.springframework.stereotype.Service
import org.web3j.protocol.core.methods.response.TransactionReceipt
import java.util.UUID
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/**
 * Emergency freeze and resume of the on-chain registry by a compliance officer (decision 31, revised on 2026-10-27).
 *
 * Both are signed by the dedicated pauser key and sent **directly**, never through the outbox: a stuck worker
 * (nonce, RPC) must not delay them. Every request is written to the compliance audit log with its rationale —
 * successful, idempotent, or failed. Resuming needs `UNPAUSER_ROLE` on that key, which the registry admin may revoke
 * at any time without redeploying: resuming then becomes the admin's alone and is refused here.
 *
 * Pause and resume share one key, hence one nonce stream: they are serialized by a lock so that two officers acting
 * at once never send two transactions with the same nonce (single backend instance).
 *
 * Not `@Transactional`: no database transaction is held open while waiting for the chain; each journal entry
 * commits on its own ([ComplianceAuditLogService.append]).
 */
@Service
class OnchainPauseService(
    private val cfg: OnchainConfig,
    private val registry: OnchainRegistry,
    private val pauser: ObjectProvider<OnchainPauser>,
    private val auditLog: ComplianceAuditLogService,
    private val explorer: ExplorerLinkBuilder,
    private val users: UserRepository,
) {
    private val logger = LoggerFactory.getLogger(javaClass)
    private val pauserKeyLock = ReentrantLock()

    /**
     * Reads the registry state for the back-office. Never fails on an unreachable RPC: [OnchainRegistryStatusDto.paused]
     * is then null and [OnchainRegistryStatusDto.canUnpause] false (logged at WARN, without the RPC URL).
     */
    fun status(): OnchainRegistryStatusDto {
        if (cfg.mock) {
            return OnchainRegistryStatusDto(mock = true, chainId = cfg.currentChainId(), registryAddress = null,
                paused = false, canUnpause = false, explorerAddressUrl = null)
        }
        val paused = runCatching { registry.isPaused() }
            .onFailure { logger.warn("Cannot read the registry pause state ({})", it.javaClass.simpleName) }
            .getOrNull()
        val canUnpause = runCatching { pauser.getIfAvailable()?.canUnpause() ?: false }
            .onFailure { logger.warn("Cannot read the platform unpauser role ({})", it.javaClass.simpleName) }
            .getOrDefault(false)
        return OnchainRegistryStatusDto(mock = false, chainId = cfg.chainId, registryAddress = cfg.registryAddress,
            paused = paused, canUnpause = canUnpause, explorerAddressUrl = explorer.addressUrl(cfg.registryAddress))
    }

    /**
     * Freezes the registry: every on-chain write reverts until it is resumed. Idempotent: an already paused registry
     * gets no transaction, but the request is still journaled.
     *
     * @param actorUserId the compliance officer (user id, i.e. `principal.username`).
     * @param rationale mandatory reason, journaled verbatim.
     * @return the transaction coordinates, or `alreadyPaused = true`.
     * @throws UnprocessableEntityException when [rationale] is blank.
     * @throws ConflictException in simulated mode: there is no registry to freeze (nothing is journaled).
     * @throws BadGatewayException when the pause could not be read or sent (journaled as
     *   [ComplianceAuditLogService.ONCHAIN_REGISTRY_PAUSE_FAILED]).
     */
    fun pause(actorUserId: UUID, rationale: String): OnchainPauseResultDto {
        val pauserClient = requireRealMode(rationale, "La motivation du gel du registre est obligatoire",
            "Mode simulé : aucun registre on-chain n'est déployé, il n'y a rien à geler")

        val receipt = pauserKeyLock.withLock {
            // Chain part only inside the try: a journal failure after a successful pause must never be reported as a
            // failed pause.
            try {
                if (registry.isPaused()) null else pauserClient.pause().also { check(it.isStatusOK) { "pause() reverted" } }
            } catch (ex: Exception) {
                throw journalFailure(ComplianceAuditLogService.ONCHAIN_REGISTRY_PAUSE_FAILED, actorUserId, rationale,
                    ex.javaClass.simpleName, "Le gel du registre on-chain a échoué")
            }
        }

        if (receipt == null) {
            journal(ComplianceAuditLogService.ONCHAIN_REGISTRY_PAUSED, actorUserId, rationale, null, "alreadyPaused" to true)
            logger.warn("Registry pause requested by compliance officer {}: already paused, no transaction", actorUserId)
            return OnchainPauseResultDto(paused = true, alreadyPaused = true, txHash = null, blockNumber = null,
                explorerTxUrl = null)
        }
        logger.warn("Registry PAUSED by compliance officer {}: tx {} on chain {}", actorUserId,
            receipt.transactionHash, cfg.chainId)
        journal(ComplianceAuditLogService.ONCHAIN_REGISTRY_PAUSED, actorUserId, rationale, receipt, "alreadyPaused" to false)
        return OnchainPauseResultDto(paused = true, alreadyPaused = false, txHash = receipt.transactionHash,
            blockNumber = receipt.blockNumber.toLong(), explorerTxUrl = explorer.txUrl(receipt.transactionHash))
    }

    /**
     * Resumes the registry: writes are accepted again and the outbox worker sends the jobs it held. Idempotent: a
     * registry that is not paused gets no transaction, but the request is still journaled.
     *
     * @param actorUserId the compliance officer (user id, i.e. `principal.username`).
     * @param rationale mandatory reason, journaled verbatim.
     * @return the transaction coordinates, or `alreadyActive = true`.
     * @throws UnprocessableEntityException when [rationale] is blank.
     * @throws ConflictException in simulated mode (nothing is journaled), or when the platform key no longer holds
     *   `UNPAUSER_ROLE` (journaled as [ComplianceAuditLogService.ONCHAIN_REGISTRY_UNPAUSE_FAILED]).
     * @throws BadGatewayException when the resume could not be read or sent (journaled as
     *   [ComplianceAuditLogService.ONCHAIN_REGISTRY_UNPAUSE_FAILED]).
     */
    fun unpause(actorUserId: UUID, rationale: String): OnchainUnpauseResultDto {
        val pauserClient = requireRealMode(rationale, "La motivation de la réactivation du registre est obligatoire",
            "Mode simulé : aucun registre on-chain n'est déployé, il n'y a rien à réactiver")

        val outcome = pauserKeyLock.withLock {
            try {
                when {
                    !registry.isPaused() -> UnpauseOutcome.AlreadyActive
                    !pauserClient.canUnpause() -> UnpauseOutcome.RoleRevoked
                    else -> UnpauseOutcome.Sent(
                        pauserClient.unpause().also { check(it.isStatusOK) { "unpause() reverted" } },
                    )
                }
            } catch (ex: Exception) {
                throw journalFailure(ComplianceAuditLogService.ONCHAIN_REGISTRY_UNPAUSE_FAILED, actorUserId, rationale,
                    ex.javaClass.simpleName, "La réactivation du registre on-chain a échoué")
            }
        }

        return when (outcome) {
            UnpauseOutcome.AlreadyActive -> {
                journal(ComplianceAuditLogService.ONCHAIN_REGISTRY_UNPAUSED, actorUserId, rationale, null,
                    "alreadyActive" to true)
                logger.warn("Registry resume requested by compliance officer {}: not paused, no transaction", actorUserId)
                OnchainUnpauseResultDto(paused = false, alreadyActive = true, txHash = null, blockNumber = null,
                    explorerTxUrl = null)
            }
            UnpauseOutcome.RoleRevoked -> {
                journal(ComplianceAuditLogService.ONCHAIN_REGISTRY_UNPAUSE_FAILED, actorUserId, rationale, null,
                    "errorType" to UNPAUSER_ROLE_MISSING)
                logger.warn("Registry resume refused for compliance officer {}: the platform key lacks UNPAUSER_ROLE",
                    actorUserId)
                throw ConflictException("La plateforme n'a pas le droit de réactiver le registre : seul " +
                    "l'administrateur du registre peut lever la pause")
            }
            is UnpauseOutcome.Sent -> {
                val receipt = outcome.receipt
                logger.warn("Registry RESUMED by compliance officer {}: tx {} on chain {}", actorUserId,
                    receipt.transactionHash, cfg.chainId)
                journal(ComplianceAuditLogService.ONCHAIN_REGISTRY_UNPAUSED, actorUserId, rationale, receipt,
                    "alreadyActive" to false)
                OnchainUnpauseResultDto(paused = false, alreadyActive = false, txHash = receipt.transactionHash,
                    blockNumber = receipt.blockNumber.toLong(), explorerTxUrl = explorer.txUrl(receipt.transactionHash))
            }
        }
    }

    /**
     * The fifty most recent freezes and resumes requested from the platform, newest first, each with the email of the
     * officer who asked (one batch lookup of the actors).
     */
    fun history(): List<OnchainControlEntryDto> {
        val entries = auditLog.findOnchainRegistryControlHistory()
        val emails = users.findAllById(entries.mapNotNull { it.actorUserId }.toSet()).associate { it.id to it.email }
        return entries.map {
            OnchainControlEntryDto(sequenceNo = it.sequenceNo, eventType = it.eventType, actorUserId = it.actorUserId,
                actorEmail = it.actorUserId?.let(emails::get), payload = it.payload, occurredAt = it.occurredAt)
        }
    }

    /**
     * Common preconditions of [pause] and [unpause].
     *
     * @return the pauser client (real mode only).
     * @throws UnprocessableEntityException when [rationale] is blank ([blankMessage]).
     * @throws ConflictException in simulated mode ([mockMessage]).
     */
    private fun requireRealMode(rationale: String, blankMessage: String, mockMessage: String): OnchainPauser {
        if (rationale.isBlank()) throw UnprocessableEntityException(blankMessage)
        if (cfg.mock) throw ConflictException(mockMessage)
        return checkNotNull(pauser.getIfAvailable()) { "onchain.mock=false but no OnchainPauser is configured" }
    }

    /**
     * Journals a failed chain call and builds the 502 to throw. Only the exception class is kept: messages from web3j
     * may embed the RPC URL.
     */
    private fun journalFailure(
        eventType: String,
        actorUserId: UUID,
        rationale: String,
        errorType: String,
        message: String,
    ): BadGatewayException {
        journal(eventType, actorUserId, rationale, null, "errorType" to errorType)
        logger.error("{} requested by compliance officer {} ({})", eventType, actorUserId, errorType)
        return BadGatewayException("$message ($errorType) : réessayer, ou contacter l'administrateur")
    }

    /** Appends one journal entry: rationale, chain, registry, transaction (when sent) and [extra]. */
    private fun journal(
        eventType: String,
        actorUserId: UUID,
        rationale: String,
        receipt: TransactionReceipt?,
        extra: Pair<String, Any>,
    ) {
        val payload = buildMap<String, Any?> {
            put("rationale", rationale)
            put("chainId", cfg.chainId)
            put("registryAddress", cfg.registryAddress)
            if (extra.first != "errorType") put("txHash", receipt?.transactionHash)
            put(extra.first, extra.second)
        }
        auditLog.append(
            eventType = eventType,
            subjectType = ComplianceAuditSubjectType.SYSTEM,
            actorUserId = actorUserId,
            payload = payload,
        )
    }

    /** What the resume found on the chain, decided under the key lock. */
    private sealed interface UnpauseOutcome {
        data object AlreadyActive : UnpauseOutcome
        data object RoleRevoked : UnpauseOutcome
        data class Sent(val receipt: TransactionReceipt) : UnpauseOutcome
    }

    private companion object {
        /** Journal error type when the platform key lost `UNPAUSER_ROLE` (not an exception class). */
        const val UNPAUSER_ROLE_MISSING = "UNPAUSER_ROLE_MISSING"
    }
}
