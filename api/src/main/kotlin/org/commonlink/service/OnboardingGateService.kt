package org.commonlink.service

import org.commonlink.entity.MollieOnboardingStatus
import org.commonlink.exception.ConflictException
import org.commonlink.exception.UserNotFoundException
import org.commonlink.repository.AssociationProfileRepository
import org.commonlink.repository.FiscalMandateRepository
import org.commonlink.repository.MollieConnectionRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

/**
 * Server-side enforcement of the association onboarding chain, mirroring the frontend tab lock
 * (`tabUnlocked` in the association profile page): information → verification → fiscal mandate →
 * bank account (Mollie) → donation widget.
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
    private val mollieConnectionRepository: MollieConnectionRepository,
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
        val associationId = resolveAssociationId(userId)
        val activeMandate = fiscalMandateRepository.findByAssociationIdAndRevokedAtIsNull(associationId)
        if (activeMandate == null) {
            throw ConflictException("A signed fiscal mandate is required before connecting a bank account")
        }
    }

    /**
     * Returns true if the association's Mollie Connect account can collect donations.
     * Mirrors the predicate used by [requireBankReady] without throwing — for use in
     * read-path guards where a boolean check is more appropriate than a thrown exception.
     */
    @Transactional(readOnly = true)
    fun isBankReady(userId: UUID): Boolean {
        val associationId = resolveAssociationId(userId)
        return mollieConnectionRepository.findByAssociationId(associationId)?.canCollectDonations() == true
    }

    /**
     * Returns true if Mollie KYC is fully completed for this association.
     *
     * Used to lock contact-identity fields ([org.commonlink.entity.AssociationProfile.contactName]
     * and [org.commonlink.entity.AssociationProfile.contactEmail]) that were submitted to Mollie
     * during client-link creation. Changing them after KYC completion would create inconsistency
     * with Mollie's records.
     */
    @Transactional(readOnly = true)
    fun isMollieKycCompleted(userId: UUID): Boolean {
        val associationId = resolveAssociationId(userId)
        val connection = mollieConnectionRepository.findByAssociationId(associationId)
        return connection?.onboardingStatus == MollieOnboardingStatus.COMPLETED
    }

    /**
     * Requires the association's Mollie Connect account to be able to collect donations.
     *
     * Direct prerequisite for enabling the donation widget (token generation, widget config, and
     * setting a destination campaign).
     *
     * Uses [org.commonlink.entity.MollieConnection.canCollectDonations] — the same predicate that
     * gates campaign publication. A widget cannot work on an unpublished campaign, so this gate must
     * never be laxer than the publish gate: completed Mollie KYC **and** `canReceivePayments`.
     *
     * @throws ConflictException if there is no Mollie connection, or Mollie KYC does not yet authorise payments.
     */
    @Transactional(readOnly = true)
    fun requireBankReady(userId: UUID) {
        val associationId = resolveAssociationId(userId)
        val connection = mollieConnectionRepository.findByAssociationId(associationId)
        if (connection?.canCollectDonations() != true) {
            throw ConflictException("A bank account (Mollie) authorised to receive payments is required before enabling the donation widget")
        }
    }

    private fun resolveAssociationId(userId: UUID): UUID =
        associationProfileRepository.findByUserId(userId)
            .orElseThrow { UserNotFoundException("Association profile not found for user $userId") }
            .id!!
}
