package org.commonlink.repository

import org.commonlink.entity.FiscalMandate
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface FiscalMandateRepository : JpaRepository<FiscalMandate, UUID> {

    /**
     * Returns the single active (non-revoked) mandate for an association, or null if none exists.
     * Backed by [uidx_fiscal_mandate_active] partial unique index.
     */
    fun findByAssociationIdAndRevokedAtIsNull(associationId: UUID): FiscalMandate?

    /**
     * Returns all mandates for an association, including revoked ones, for history.
     * Backed by [idx_fiscal_mandate_association_id].
     */
    fun findAllByAssociationId(associationId: UUID): List<FiscalMandate>
}
