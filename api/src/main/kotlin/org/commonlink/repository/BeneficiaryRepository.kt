package org.commonlink.repository

import org.commonlink.entity.Beneficiary
import org.springframework.data.jpa.repository.JpaRepository
import java.util.Optional
import java.util.UUID

interface BeneficiaryRepository : JpaRepository<Beneficiary, UUID> {
    /**
     * Returns all beneficiaries belonging to the given association, ordered by persistence order.
     *
     * @param associationId the UUID of the [org.commonlink.entity.AssociationProfile]
     */
    fun findAllByAssociationId(associationId: UUID): List<Beneficiary>

    /**
     * Finds a beneficiary by its own ID and the owning association's ID.
     *
     * Used to verify ownership before returning or mutating data — prevents cross-association access.
     *
     * @param id the UUID of the [Beneficiary]
     * @param associationId the UUID of the [org.commonlink.entity.AssociationProfile]
     */
    fun findByIdAndAssociationId(id: UUID, associationId: UUID): Optional<Beneficiary>

    /**
     * Checks whether a beneficiary with the given SIREN already exists under the association.
     *
     * Used to enforce the unique constraint before persisting, returning a friendly error instead
     * of a database constraint violation.
     *
     * @param associationId the UUID of the [org.commonlink.entity.AssociationProfile]
     * @param identifier1 the 9-digit SIREN to check
     */
    fun existsByAssociationIdAndIdentifier1(associationId: UUID, identifier1: String): Boolean
}
