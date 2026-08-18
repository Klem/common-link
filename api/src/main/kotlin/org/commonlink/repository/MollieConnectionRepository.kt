package org.commonlink.repository

import org.commonlink.entity.MollieConnection
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.Instant
import java.util.UUID

/**
 * Exactly what the scheduled token refresh needs about a candidate connection.
 *
 * A projection rather than the entity, because the sweep reads outside any transaction:
 * `MollieConnection.association` is a LAZY `@OneToOne`, so handing back detached entities would
 * make the sweep depend on Hibernate returning an id from an uninitialised proxy. Selecting the
 * id directly removes the question. [refreshToken] still passes through
 * [org.commonlink.security.MoneriumTokenConverter], so the mock sentinel is comparable in clear.
 */
data class MollieRefreshCandidate(
    val associationId: UUID,
    val refreshToken: String,
)

interface MollieConnectionRepository : JpaRepository<MollieConnection, UUID> {

    /**
     * Returns the Mollie connection for the given association, or null.
     */
    @Query("SELECT c FROM MollieConnection c WHERE c.association.id = :associationId")
    fun findByAssociationId(associationId: UUID): MollieConnection?

    /**
     * Like [findByAssociationId] but acquires a row-level pessimistic write lock (SELECT … FOR UPDATE).
     *
     * Uses a native query to emit plain FOR UPDATE, avoiding Hibernate 6's PostgreSQL-dialect
     * FOR NO KEY UPDATE variant which H2 (used in tests) does not recognise.
     *
     * Used during access-token refresh so that two concurrent callers don't both POST
     * grant_type=refresh_token and invalidate each other's resulting refresh token.
     * Must be called within a transaction.
     */
    @Query(
        value = "SELECT * FROM mollie_connections WHERE association_id = :associationId FOR UPDATE",
        nativeQuery = true,
    )
    fun findByAssociationIdForUpdate(@Param("associationId") associationId: UUID): MollieConnection?

    /**
     * Returns every ACTIVE connection whose access token expires before [threshold] — the
     * candidate set for the scheduled proactive refresh ([org.commonlink.service.MollieTokenRefreshExecutor]).
     *
     * BROKEN connections are excluded: they can only be recovered by a user-driven re-authorisation,
     * and `getValidAccessToken` throws on them without attempting anything.
     *
     * No lock and no mock filtering here. Mock rows cannot be excluded in SQL because
     * [org.commonlink.security.MoneriumTokenConverter] stores tokens as AES-GCM with a random IV
     * in production, so the ciphertext differs on every write and `refresh_token <> 'mock'` would
     * silently match everything. The caller filters decrypted values in Kotlin instead.
     */
    @Query(
        "SELECT new org.commonlink.repository.MollieRefreshCandidate(c.association.id, c.refreshToken) " +
            "FROM MollieConnection c " +
            "WHERE c.state = org.commonlink.entity.MollieConnectionState.ACTIVE " +
            "AND c.expiresAt < :threshold",
    )
    fun findActiveExpiringBefore(@Param("threshold") threshold: Instant): List<MollieRefreshCandidate>

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
