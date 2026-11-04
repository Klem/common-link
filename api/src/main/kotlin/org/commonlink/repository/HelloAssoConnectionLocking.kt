package org.commonlink.repository

import jakarta.persistence.EntityManager
import org.commonlink.entity.HelloAssoConnection
import java.util.UUID

/**
 * Row-locked read of a [HelloAssoConnection], mixed into [HelloAssoConnectionRepository] as a Spring Data fragment
 * (implemented by [HelloAssoConnectionLockingImpl]).
 */
interface HelloAssoConnectionLocking {

    /**
     * The association's HelloAsso row with a row lock (`SELECT … FOR UPDATE`) held until the transaction ends — the
     * single-renewal guarantee of [org.commonlink.service.HelloAssoTokenManager] and the only safe base for a full-row
     * save. Must run in a transaction.
     *
     * The returned state is always the one read under the lock: when the row is already managed in the persistence
     * context (read unlocked earlier in the same request, open-in-view), it is refreshed, so a refresh token rotated
     * meanwhile is never read stale nor written back.
     */
    fun findByAssociationIdForUpdate(associationId: UUID): HelloAssoConnection?
}

/** Implementation of [HelloAssoConnectionLocking]: native `FOR UPDATE`, then a refresh of the managed instance. */
class HelloAssoConnectionLockingImpl(private val entityManager: EntityManager) : HelloAssoConnectionLocking {

    /** See [HelloAssoConnectionLocking.findByAssociationIdForUpdate]. */
    override fun findByAssociationIdForUpdate(associationId: UUID): HelloAssoConnection? {
        val connection = entityManager
            .createNativeQuery(
                "SELECT * FROM helloasso_connections WHERE association_id = :associationId FOR UPDATE",
                HelloAssoConnection::class.java,
            )
            .setParameter("associationId", associationId)
            .resultList
            .firstOrNull() as? HelloAssoConnection?
            ?: return null
        entityManager.refresh(connection)
        return connection
    }
}
