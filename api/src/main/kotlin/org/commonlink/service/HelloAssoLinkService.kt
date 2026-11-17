package org.commonlink.service

import org.commonlink.dto.HelloAssoConnectRequest
import org.commonlink.dto.HelloAssoDisconnectPreviewResponse
import org.commonlink.entity.AssociationProfile
import org.commonlink.entity.ComplianceAuditSubjectType
import org.commonlink.entity.HelloAssoConnectionState
import org.commonlink.exception.HelloAssoMandateRequiredException
import org.commonlink.exception.HelloAssoVerificationExpiredException
import org.commonlink.exception.UserNotFoundException
import org.commonlink.repository.AssociationProfileRepository
import org.commonlink.repository.HelloAssoConnectionRepository
import org.commonlink.repository.HelloAssoDonationFundsRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

/**
 * Confirms a verified HelloAsso link with the association's mandate, and disconnects it.
 *
 * The link is persisted on the association's existing row (its notification token never changes), **without the
 * secret**: only the `client_id`, the slug and the encrypted tokens obtained at verification. The mandate — who, when,
 * which wording version — goes to the compliance journal, with neither IP nor user agent (same GDPR stance as
 * [ComplianceAuditLogService.appendMollieSelfCompletionAttestedOnce]).
 */
@Service
class HelloAssoLinkService(
    private val associationProfileRepository: AssociationProfileRepository,
    private val connectionRepository: HelloAssoConnectionRepository,
    private val pendingLinks: HelloAssoPendingLinkStore,
    private val rows: HelloAssoConnectionRows,
    private val complianceAuditLogService: ComplianceAuditLogService,
    private val fundsRepository: HelloAssoDonationFundsRepository,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    companion object {
        /**
         * Current wording of the mandate (CGU Associations 4.5, wording v2 of 2026-10-28): « Au nom de l'association,
         * j'autorise CommonLink à utiliser l'accès ouvert par cette clé uniquement pour créer les paiements de nos
         * campagnes CommonLink, suivre ces paiements et vérifier que HelloAsso accepte les paiements pour notre
         * association. Je peux retirer cet accès à tout moment. » Bump it with any wording change.
         */
        const val MANDATE_VERSION = "helloasso-mandate-v2-2026-10-28"

        /** `abcd…wxyz`: enough to recognise a key, never enough to use it (the client id is not a secret anyway). */
        fun maskClientId(clientId: String?): String? = clientId?.let {
            if (it.length <= 8) "…" else "${it.take(4)}…${it.takeLast(4)}"
        }
    }

    /**
     * Links the HelloAsso account verified under [HelloAssoConnectRequest.verifyId]: also re-links a BROKEN or
     * DISCONNECTED account (or replaces an ACTIVE one) with fresh keys.
     *
     * @throws org.commonlink.exception.HelloAssoNotAssociationProviderException when the association is not on HELLOASSO.
     * @throws HelloAssoMandateRequiredException when the mandate is not accepted in [MANDATE_VERSION].
     * @throws HelloAssoVerificationExpiredException when the verification is unknown, expired, used or another's.
     */
    @Transactional
    fun connect(userId: UUID, request: HelloAssoConnectRequest) {
        val association = resolveAssociation(userId)
        rows.requireHelloAssoProvider(association)
        if (!request.mandate.accepted || request.mandate.version != MANDATE_VERSION) throw HelloAssoMandateRequiredException()
        val associationId = association.id!!
        val pending = pendingLinks.take(request.verifyId, associationId) ?: throw HelloAssoVerificationExpiredException()

        val connection = rows.findOrCreate(association)
        val now = Instant.now()
        connection.apply {
            state = HelloAssoConnectionState.ACTIVE
            organizationSlug = pending.slug
            clientId = pending.clientId
            refreshToken = pending.tokens.refreshToken
            accessToken = pending.tokens.accessToken
            accessTokenExpiresAt = pending.verifiedAt.plusSeconds(pending.tokens.expiresIn)
            // The secret was discarded right after the verification's token call.
            secretWipedAt = pending.verifiedAt
            canCollect = pending.canCollect
            everCompliant = everCompliant || pending.canCollect
            lastEligibilityCheckAt = pending.verifiedAt
            lastRefreshAt = pending.verifiedAt
            linkedAt = now
            linkedBy = userId
            updatedAt = now
        }
        connectionRepository.save(connection)
        complianceAuditLogService.append(
            eventType = ComplianceAuditLogService.HELLOASSO_MANDATE_ACCEPTED,
            subjectType = ComplianceAuditSubjectType.ASSOCIATION,
            payload = mapOf(
                "version" to MANDATE_VERSION,
                "organizationSlug" to pending.slug,
                "clientIdMasked" to maskClientId(pending.clientId),
            ),
            subjectId = associationId,
            actorUserId = userId,
        )
        log.info("HelloAsso account {} linked to association {}", pending.slug, associationId)
    }

    /**
     * What a disconnection would leave behind (CEO decision Q5): the association's donations HelloAsso has not paid
     * out yet, every campaign, shown before it confirms. They can no longer be tracked once disconnected and will need
     * a curator release ([HelloAssoFundsReleaseService]). Never blocks the disconnection.
     */
    @Transactional(readOnly = true)
    fun disconnectPreview(userId: UUID): HelloAssoDisconnectPreviewResponse {
        val total = fundsRepository.sumNotPaidOutByAssociationId(resolveAssociation(userId).id!!)
        return HelloAssoDisconnectPreviewResponse(total.count, (total.amount ?: BigDecimal.ZERO).setScale(2))
    }

    /**
     * Disconnects the association's HelloAsso account: tokens erased, state DISCONNECTED, row and notification token
     * kept. Idempotent; allowed whatever the association's provider (withdrawing an access must always work), even with
     * donations HelloAsso has not paid out yet (CEO decision Q5): their number and amount at that instant go to the
     * journal entry, and they then need a curator release. Campaigns are untouched: the readiness gate pauses
     * donations.
     */
    @Transactional
    fun disconnect(userId: UUID) {
        val association = resolveAssociation(userId)
        val associationId = association.id!!
        val connection = connectionRepository.findByAssociationIdForUpdate(associationId) ?: return
        if (connection.state != HelloAssoConnectionState.ACTIVE && connection.state != HelloAssoConnectionState.BROKEN) return
        val now = Instant.now()
        connection.apply {
            state = HelloAssoConnectionState.DISCONNECTED
            refreshToken = null
            accessToken = null
            accessTokenExpiresAt = null
            canCollect = false
            updatedAt = now
        }
        connectionRepository.save(connection)
        val notPaidOut = fundsRepository.sumNotPaidOutByAssociationId(associationId)
        complianceAuditLogService.append(
            eventType = ComplianceAuditLogService.HELLOASSO_DISCONNECTED,
            subjectType = ComplianceAuditSubjectType.ASSOCIATION,
            payload = mapOf(
                "organizationSlug" to connection.organizationSlug,
                "notYetPaidOutCount" to notPaidOut.count,
                "notYetPaidOutAmount" to (notPaidOut.amount ?: BigDecimal.ZERO).setScale(2).toPlainString(),
            ),
            subjectId = associationId,
            actorUserId = userId,
        )
        log.info("HelloAsso account disconnected from association {}", associationId)
    }

    private fun resolveAssociation(userId: UUID): AssociationProfile =
        associationProfileRepository.findByUserId(userId)
            .orElseThrow { UserNotFoundException("Association profile not found for user $userId") }
}
