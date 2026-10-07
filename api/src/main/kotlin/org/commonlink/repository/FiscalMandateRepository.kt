package org.commonlink.repository

import org.commonlink.entity.FiscalMandate
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.Instant
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

    /**
     * Returns the mandate in force for [associationId] at instant [at] — signed before that date
     * and either still active or revoked after it.
     *
     * Distinct from [findByAssociationIdAndRevokedAtIsNull], which answers "now". A donation made
     * in 2024 opened a right to a tax reduction under the mandate in force *then*; taking the
     * current mandate would produce wrong figures on exactly the years the donor has already
     * declared. The partial unique index guarantees at most one active mandate at a time, so at
     * most one row can match any given instant.
     */
    @Query("""
        SELECT m FROM FiscalMandate m
        WHERE m.association.id = :associationId
          AND m.signedAt <= :at
          AND (m.revokedAt IS NULL OR m.revokedAt > :at)
    """)
    fun findActiveAt(
        @Param("associationId") associationId: UUID,
        @Param("at") at: Instant,
    ): FiscalMandate?

    /**
     * Draws the next value from the [fiscal_mandate_ref_seq] PostgreSQL sequence.
     * Used to generate unique mandate references in the format MND-<year>-<seq %04d>.
     */
    @Query(value = "SELECT nextval('fiscal_mandate_ref_seq')", nativeQuery = true)
    fun nextSequenceValue(): Long
}
