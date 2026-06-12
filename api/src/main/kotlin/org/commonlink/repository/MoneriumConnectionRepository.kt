package org.commonlink.repository

import jakarta.persistence.LockModeType
import org.commonlink.entity.MoneriumConnection
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.util.UUID

interface MoneriumConnectionRepository : JpaRepository<MoneriumConnection, UUID> {

    /**
     * Like [findByAssociationId] but acquires a row-level pessimistic write lock.
     *
     * Used during access-token refresh so that two concurrent callers don't both POST
     * `grant_type=refresh_token` and invalidate each other's resulting refresh token.
     * Must be called within a transaction.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT c FROM MoneriumConnection c WHERE c.association.id = :associationId")
    fun findByAssociationIdForUpdate(associationId: UUID): MoneriumConnection?

    /**
     * Returns the connection that already binds the given Monerium profile id, or null.
     *
     * Used at callback time to reject "this Monerium account is already linked to another
     * association" — the failure mode where someone reconnects the wrong account because
     * the OAuth popup was prefilled with a different Monerium session.
     */
    fun findByMoneriumProfileId(moneriumProfileId: String): MoneriumConnection?

    /**
     * Returns the Monerium connection for the given association id, or null.
     */
    @Query("SELECT c FROM MoneriumConnection c WHERE c.association.id = :associationId")
    fun findByAssociationId(associationId: UUID): MoneriumConnection?
}
