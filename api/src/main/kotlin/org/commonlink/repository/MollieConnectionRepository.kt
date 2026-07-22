package org.commonlink.repository

import jakarta.persistence.LockModeType
import org.commonlink.entity.MollieConnection
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import java.util.UUID

interface MollieConnectionRepository : JpaRepository<MollieConnection, UUID> {

    /**
     * Returns the Mollie connection for the given association, or null.
     */
    @Query("SELECT c FROM MollieConnection c WHERE c.association.id = :associationId")
    fun findByAssociationId(associationId: UUID): MollieConnection?

    /**
     * Like [findByAssociationId] but acquires a row-level pessimistic write lock.
     *
     * Used during access-token refresh so that two concurrent callers don't both POST
     * grant_type=refresh_token and invalidate each other's resulting refresh token.
     * Must be called within a transaction.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT c FROM MollieConnection c WHERE c.association.id = :associationId")
    fun findByAssociationIdForUpdate(associationId: UUID): MollieConnection?

    /**
     * Returns true if the given association already has a Mollie connection.
     */
    @Query("SELECT COUNT(c) > 0 FROM MollieConnection c WHERE c.association.id = :associationId")
    fun existsByAssociationId(associationId: UUID): Boolean

    /**
     * Returns the connection bound to the given Mollie organization id, or null.
     *
     * Used at callback time to enforce the uniqueness guard: one Mollie organization
     * can be linked to at most one association.
     */
    fun findByMollieOrganizationId(organizationId: String): MollieConnection?
}
