package org.commonlink.service

import org.commonlink.entity.AssociationProfile
import org.commonlink.entity.MandateEligibility
import org.commonlink.repository.FiscalMandateRepository
import org.springframework.stereotype.Service

/**
 * Computes the fiscal tax reduction rate applicable to donations for a given association.
 *
 * Returns 75 if the association holds an active OIG-75 Coluche mandate, 66 otherwise.
 */
@Service
class TaxRateService(private val mandateRepository: FiscalMandateRepository) {

    fun taxReductionRate(association: AssociationProfile): Int {
        val mandate = mandateRepository.findByAssociationIdAndRevokedAtIsNull(association.id!!) ?: return 66
        return when (mandate.eligibility) {
            MandateEligibility.OIG_75_COLUCHE -> 75
            else -> 66
        }
    }
}
