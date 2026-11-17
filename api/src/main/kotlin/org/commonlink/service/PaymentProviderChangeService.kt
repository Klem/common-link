package org.commonlink.service

import org.commonlink.config.DonationCapProperties
import org.commonlink.dto.PaymentProviderOptionDto
import org.commonlink.dto.PaymentProviderPreviewDto
import org.commonlink.entity.AssociationProfile
import org.commonlink.entity.CampaignStatus
import org.commonlink.entity.ComplianceAuditSubjectType
import org.commonlink.event.PaymentProviderChangedEvent
import org.commonlink.exception.NotFoundException
import org.commonlink.exception.UnprocessableEntityException
import org.commonlink.payment.PaymentProviderId
import org.commonlink.payment.PaymentProviderRegistry
import org.commonlink.repository.AssociationProfileRepository
import org.commonlink.repository.CampaignRepository
import org.commonlink.repository.DonationRepository
import org.slf4j.LoggerFactory
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID

/**
 * Curator-driven change of an association's payment provider ([AssociationProfile.paymentProvider]).
 *
 * A change only affects **new** payments: the previous provider's connection is kept untouched (this service has no
 * dependency on any provider's connection store), and in-flight
 * payments keep being settled by the provider that took them (routed by `providerRef` prefix). Every effective change
 * is journaled ([ComplianceAuditLogService.PAYMENT_PROVIDER_CHANGED]) with the curator and a mandatory reason, then
 * announced by a [PaymentProviderChangedEvent].
 */
@Service
class PaymentProviderChangeService(
    private val associationProfileRepository: AssociationProfileRepository,
    private val campaignRepository: CampaignRepository,
    private val donationRepository: DonationRepository,
    private val paymentProviderRegistry: PaymentProviderRegistry,
    private val complianceAuditLogService: ComplianceAuditLogService,
    private val eventPublisher: ApplicationEventPublisher,
    private val donationCapProperties: DonationCapProperties,
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    companion object {
        /** Maximum reason length, after trim. Mirrored by the request `@Size` and the admin panel validation. */
        const val REASON_MAX_LENGTH = 500
    }

    /**
     * Returns the association's current provider and the impact of changing it. Database reads only, no network call.
     *
     * @throws NotFoundException when the association does not exist.
     */
    @Transactional(readOnly = true)
    fun preview(associationId: UUID): PaymentProviderPreviewDto = preview(findAssociation(associationId))

    /**
     * Moves the association to [target]. Same provider → no-op (no journal entry, no event).
     *
     * @param reason mandatory, non-blank after trim, at most [REASON_MAX_LENGTH] characters; journaled, never sent to
     *   the association.
     * @return the preview after the change.
     * @throws UnprocessableEntityException when the reason is blank or too long (HTTP 422, like the request's bean
     *   validation).
     * @throws NotFoundException when the association does not exist.
     */
    @Transactional
    fun change(associationId: UUID, target: PaymentProviderId, reason: String, curatorUserId: UUID): PaymentProviderPreviewDto {
        val trimmedReason = reason.trim()
        if (trimmedReason.isEmpty()) {
            throw UnprocessableEntityException("A reason is required to change the payment provider")
        }
        if (trimmedReason.length > REASON_MAX_LENGTH) {
            throw UnprocessableEntityException("Reason exceeds $REASON_MAX_LENGTH characters")
        }

        val association = findAssociation(associationId)
        val from = association.paymentProvider
        if (from == target) return preview(association)

        association.paymentProvider = target
        associationProfileRepository.save(association)
        complianceAuditLogService.append(
            eventType = ComplianceAuditLogService.PAYMENT_PROVIDER_CHANGED,
            subjectType = ComplianceAuditSubjectType.ASSOCIATION,
            payload = mapOf("from" to from.name, "to" to target.name, "reason" to trimmedReason),
            subjectId = associationId,
            actorUserId = curatorUserId,
        )
        eventPublisher.publishEvent(PaymentProviderChangedEvent(associationId, from, target))
        logger.info("Association {} moved from payment provider {} to {} by curator {}", associationId, from, target, curatorUserId)
        return preview(association)
    }

    private fun preview(association: AssociationProfile): PaymentProviderPreviewDto {
        val associationId = association.id!!
        val since = Instant.now().minus(donationCapProperties.reservationTtl)
        return PaymentProviderPreviewDto(
            associationId = associationId,
            currentProvider = association.paymentProvider,
            providers = PaymentProviderId.entries.map { id ->
                PaymentProviderOptionDto(id, paymentProviderRegistry.find(id)?.readiness(associationId)?.status)
            },
            liveCampaigns = campaignRepository.countByAssociationIdAndStatus(associationId, CampaignStatus.LIVE),
            widgetEnabled = association.widgetToken != null,
            pendingDonations = donationRepository.countPendingByAssociationIdAndProviderRefPrefixSince(
                associationId, association.paymentProvider.refPrefix, since,
            ),
        )
    }

    private fun findAssociation(associationId: UUID): AssociationProfile =
        associationProfileRepository.findById(associationId)
            .orElseThrow { NotFoundException("Association not found: $associationId") }
}
