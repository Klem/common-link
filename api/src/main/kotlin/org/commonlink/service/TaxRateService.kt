package org.commonlink.service

import org.commonlink.entity.AssociationProfile
import org.commonlink.entity.FiscalMandate
import org.commonlink.entity.MandateEligibility
import org.commonlink.repository.FiscalMandateRepository
import org.springframework.stereotype.Service
import java.time.Instant
import java.util.UUID

/**
 * Computes the fiscal tax reduction rate applicable to donations for a given association.
 *
 * Returns 75 if the association holds an OIG-75 Coluche mandate, 66 otherwise.
 */
@Service
class TaxRateService(private val mandateRepository: FiscalMandateRepository) {

    fun taxReductionRate(association: AssociationProfile): Int =
        rateOf(mandateRepository.findByAssociationIdAndRevokedAtIsNull(association.id!!))

    /**
     * Rate applicable to a donation made at [at] — the mandate in force **then**, not today.
     *
     * A donation confirmed in 2024 to an association whose mandate has since been revoked did open
     * a right to a reduction at the time, and the donor has already declared it. Resolving against
     * the current mandate would produce wrong figures on exactly the years that matter.
     *
     * @param associationId beneficiary of the donation.
     * @param at instant the donation was confirmed.
     * @return 75 or 66; 66 when no mandate was in force, matching [taxReductionRate]'s fallback.
     */
    fun taxReductionRateAt(associationId: UUID, at: Instant): Int =
        rateOf(mandateRepository.findActiveAt(associationId, at))

    /**
     * Same resolution as [taxReductionRateAt], from an already-loaded mandate history.
     *
     * Lets a caller holding many donations for one association load that association's mandates
     * once instead of issuing a query per donation.
     *
     * @param mandates every mandate of the association, revoked ones included.
     * @param at instant the donation was confirmed.
     */
    fun taxReductionRateAt(mandates: List<FiscalMandate>, at: Instant): Int =
        rateOf(mandates.firstOrNull { it.signedAt <= at && (it.revokedAt?.isAfter(at) ?: true) })

    private fun rateOf(mandate: FiscalMandate?): Int = when (mandate?.eligibility) {
        MandateEligibility.OIG_75_COLUCHE -> 75
        else -> 66
    }
}
