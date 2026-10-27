package org.commonlink.service

import org.commonlink.config.OnchainConfig
import org.commonlink.dto.OnchainPauseResultDto
import org.commonlink.dto.OnchainRegistryStatusDto
import org.commonlink.entity.ComplianceAuditSubjectType
import org.commonlink.exception.BadGatewayException
import org.commonlink.exception.ConflictException
import org.commonlink.exception.UnprocessableEntityException
import org.commonlink.onchain.ExplorerLinkBuilder
import org.commonlink.onchain.OnchainPauser
import org.commonlink.onchain.OnchainRegistry
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.ObjectProvider
import org.springframework.stereotype.Service
import java.util.UUID

/**
 * Emergency freeze of the on-chain registry by a compliance officer (decision 31).
 *
 * The pause is signed by the dedicated pauser key and sent **directly**, never through the outbox: a stuck worker
 * (nonce, RPC) must not delay a freeze. Every request is written to the compliance audit log with its rationale —
 * successful, already paused, or failed. There is deliberately no resume here: `unpause()` is reserved to the registry
 * admin (a Safe multisig in production), a key the backend never holds.
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
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    /**
     * Reads the registry state for the back-office. Never fails on an unreachable RPC: [OnchainRegistryStatusDto.paused]
     * is then null (logged at WARN, without the RPC URL).
     */
    fun status(): OnchainRegistryStatusDto {
        if (cfg.mock) {
            return OnchainRegistryStatusDto(mock = true, chainId = cfg.currentChainId(), registryAddress = null,
                paused = false, explorerAddressUrl = null)
        }
        val paused = runCatching { registry.isPaused() }
            .onFailure { logger.warn("Cannot read the registry pause state ({})", it.javaClass.simpleName) }
            .getOrNull()
        return OnchainRegistryStatusDto(mock = false, chainId = cfg.chainId, registryAddress = cfg.registryAddress,
            paused = paused, explorerAddressUrl = explorer.addressUrl(cfg.registryAddress))
    }

    /**
     * Freezes the registry: every on-chain write reverts until the admin resumes it. Idempotent: an already paused
     * registry gets no transaction, but the request is still journaled.
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
        if (rationale.isBlank()) throw UnprocessableEntityException("La motivation du gel du registre est obligatoire")
        if (cfg.mock) {
            throw ConflictException("Mode simulé : aucun registre on-chain n'est déployé, il n'y a rien à geler")
        }
        val pauserClient = checkNotNull(pauser.getIfAvailable()) { "onchain.mock=false but no OnchainPauser is configured" }

        // Chain part only inside the try: a journal failure after a successful pause must never be reported as a
        // failed pause.
        val receipt = try {
            if (registry.isPaused()) null else pauserClient.pause().also { check(it.isStatusOK) { "pause() reverted" } }
        } catch (ex: Exception) {
            // Exception class only: messages from web3j may embed the RPC URL.
            val errorType = ex.javaClass.simpleName
            auditLog.append(
                eventType = ComplianceAuditLogService.ONCHAIN_REGISTRY_PAUSE_FAILED,
                subjectType = ComplianceAuditSubjectType.SYSTEM,
                actorUserId = actorUserId,
                payload = mapOf("rationale" to rationale, "chainId" to cfg.chainId,
                    "registryAddress" to cfg.registryAddress, "errorType" to errorType),
            )
            logger.error("Registry pause requested by compliance officer {} FAILED ({})", actorUserId, errorType)
            throw BadGatewayException("Le gel du registre on-chain a échoué ($errorType) : réessayer, ou contacter l'administrateur")
        }

        if (receipt == null) {
            journalPaused(actorUserId, rationale, txHash = null, alreadyPaused = true)
            logger.warn("Registry pause requested by compliance officer {}: already paused, no transaction", actorUserId)
            return OnchainPauseResultDto(paused = true, alreadyPaused = true, txHash = null, blockNumber = null,
                explorerTxUrl = null)
        }
        logger.warn("Registry PAUSED by compliance officer {}: tx {} on chain {}", actorUserId,
            receipt.transactionHash, cfg.chainId)
        journalPaused(actorUserId, rationale, txHash = receipt.transactionHash, alreadyPaused = false)
        return OnchainPauseResultDto(paused = true, alreadyPaused = false, txHash = receipt.transactionHash,
            blockNumber = receipt.blockNumber.toLong(), explorerTxUrl = explorer.txUrl(receipt.transactionHash))
    }

    private fun journalPaused(actorUserId: UUID, rationale: String, txHash: String?, alreadyPaused: Boolean) {
        auditLog.append(
            eventType = ComplianceAuditLogService.ONCHAIN_REGISTRY_PAUSED,
            subjectType = ComplianceAuditSubjectType.SYSTEM,
            actorUserId = actorUserId,
            payload = mapOf("rationale" to rationale, "chainId" to cfg.chainId, "registryAddress" to cfg.registryAddress,
                "txHash" to txHash, "alreadyPaused" to alreadyPaused),
        )
    }
}
