package org.commonlink.repository

import org.commonlink.entity.HelloAssoConnection
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.util.UUID

/** Persistence of [HelloAssoConnection] — one row per association, never deleted on disconnection. */
interface HelloAssoConnectionRepository : JpaRepository<HelloAssoConnection, UUID> {

    fun findByAssociationId(associationId: UUID): HelloAssoConnection?

    /**
     * Same as [findByAssociationId] with a row lock (`SELECT … FOR UPDATE`) held until the transaction ends — the
     * single-renewal guarantee of [org.commonlink.service.HelloAssoTokenManager].
     */
    @Query(
        value = "SELECT * FROM helloasso_connections WHERE association_id = :associationId FOR UPDATE",
        nativeQuery = true,
    )
    fun findByAssociationIdForUpdate(@Param("associationId") associationId: UUID): HelloAssoConnection?

    /** Resolves the association of a notification URL; null for an unknown token. */
    fun findByWebhookToken(webhookToken: String): HelloAssoConnection?

    /**
     * Whether at least one ACTIVE link belongs to an association whose payment provider is HELLOASSO — the HelloAsso
     * scheduled tasks have nothing to do otherwise. A link kept after a move to another provider does not count.
     */
    @Query(
        "SELECT COUNT(c) > 0 FROM HelloAssoConnection c " +
            "WHERE c.state = org.commonlink.entity.HelloAssoConnectionState.ACTIVE " +
            "AND c.association.paymentProvider = org.commonlink.payment.PaymentProviderId.HELLOASSO",
    )
    fun existsActiveForHelloAssoAssociations(): Boolean
}
