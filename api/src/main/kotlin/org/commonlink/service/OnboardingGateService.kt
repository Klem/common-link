package org.commonlink.service

import org.commonlink.entity.AssociationProfile
import org.commonlink.exception.ConflictException
import org.commonlink.exception.UserNotFoundException
import org.commonlink.payment.PaymentProviderRegistry
import org.commonlink.repository.AssociationProfileRepository
import org.commonlink.repository.FiscalMandateRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

/**
 * Server-side enforcement of the association onboarding chain, mirroring the frontend tab lock
 * (`tabUnlocked` in the association profile page): information → verification → fiscal mandate →
 * bank account (payment provider) → donation widget.
 *
 * Every step behind a locked tab is replayable, so each prerequisite must be re-checked on the API
 * regardless of what the UI allows. This service is the single source of truth for the *direct*
 * prerequisite of each step; callers invoke the matching guard before performing the guarded action.
 *
 * All guards throw [ConflictException] (HTTP 409) when the prerequisite is not met — consistent with
 * the existing mandate guards (e.g. `MandateService.signMandate`).
 */
@Service
class OnboardingGateService(
    private val associationProfileRepository: AssociationProfileRepository,
    private val fiscalMandateRepository: FiscalMandateRepository,
    private val paymentProviderRegistry: PaymentProviderRegistry,
) {

    /**
     * Requires the association to have an active (non-revoked) signed fiscal mandate.
     *
     * Direct prerequisite for connecting a bank account (Mollie Connect). A signed mandate already
     * implies [VerificationStatus.VERIFIED] (see `MandateService.signMandate`), so this single check
     * covers the whole chain up to the bank step.
     *
     * @throws ConflictException if no active mandate exists for the association.
     */
    @Transactional(readOnly = true)
    fun requireMandateSigned(userId: UUID) {
        val associationId = resolveAssociation(userId).id!!
        val activeMandate = fiscalMandateRepository.findByAssociationIdAndRevokedAtIsNull(associationId)
        if (activeMandate == null) {
            throw ConflictException("A signed fiscal mandate is required before connecting a bank account")
        }
    }

    /**
     * Returns true if the association can collect donations through its payment provider
     * ([org.commonlink.payment.ProviderReadinessStatus.READY]). Mirrors the predicate used by
     * [requireBankReady] without throwing — for use in read-path guards where a boolean check is more
     * appropriate than a thrown exception.
     */
    @Transactional(readOnly = true)
    fun isBankReady(userId: UUID): Boolean = isReady(resolveAssociation(userId))

    /**
     * Returns true once the association has *initiated* linking an account with its payment provider —
     * not merely once the link is completed (see [org.commonlink.payment.PaymentProvider.isLinkStarted]).
     *
     * Used to lock contact-identity fields ([org.commonlink.entity.AssociationProfile.contactName]
     * and [org.commonlink.entity.AssociationProfile.contactEmail]): the provider may have captured them
     * as soon as the link was started (for Mollie, when the client-link is created in
     * `MollieConnectService.buildAuthorizationUrl`, before the OAuth callback). Editing them mid-flow
     * would desynchronise CommonLink from the provider's records.
     *
     * When a flow is abandoned (for Mollie, once its OAuth state expires), the lock lifts on purpose:
     * the next attempt carries whatever contact data is current, so no inconsistency can arise.
     *
     * False when the association's provider has no registered implementation (defensive; every provider has one
     * today): no link can have been started.
     */
    @Transactional(readOnly = true)
    fun isPaymentLinkStarted(userId: UUID): Boolean {
        val association = resolveAssociation(userId)
        return paymentProviderRegistry.findForAssociation(association)?.isLinkStarted(association.id!!) ?: false
    }

    /**
     * Requires the association to be able to collect donations through its payment provider.
     *
     * Direct prerequisite for enabling the donation widget (token generation, widget config, and
     * setting a destination campaign).
     *
     * Uses the provider's [org.commonlink.payment.PaymentProvider.readiness] — the same predicate that
     * gates campaign publication (for Mollie, [org.commonlink.entity.MollieConnection.canCollectDonations]).
     * A widget cannot work on an unpublished campaign, so this gate must never be laxer than the publish
     * gate.
     *
     * @throws ConflictException if the association's payment provider is not ready to collect.
     */
    @Transactional(readOnly = true)
    fun requireBankReady(userId: UUID) {
        if (!isReady(resolveAssociation(userId))) {
            throw ConflictException("A payment provider account authorised to receive payments is required before enabling the donation widget")
        }
    }

    /**
     * Not ready when the association's provider has no registered implementation (defensive; every provider has one
     * today).
     */
    private fun isReady(association: AssociationProfile): Boolean =
        paymentProviderRegistry.findForAssociation(association)?.readiness(association.id!!)?.isReady ?: false

    private fun resolveAssociation(userId: UUID): AssociationProfile =
        associationProfileRepository.findByUserId(userId)
            .orElseThrow { UserNotFoundException("Association profile not found for user $userId") }
}
