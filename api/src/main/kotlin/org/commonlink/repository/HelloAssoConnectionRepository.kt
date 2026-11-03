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

    /**
     * Associations (HELLOASSO, link ACTIVE) whose token pair was last renewed before [threshold], or never — the
     * candidates of the weekly scheduled renewal.
     */
    @Query(
        "SELECT c.association.id FROM HelloAssoConnection c " +
            "WHERE c.state = org.commonlink.entity.HelloAssoConnectionState.ACTIVE " +
            "AND c.association.paymentProvider = org.commonlink.payment.PaymentProviderId.HELLOASSO " +
            "AND (c.lastRefreshAt IS NULL OR c.lastRefreshAt < :threshold)",
    )
    fun findActiveHelloAssoRefreshCandidates(@Param("threshold") threshold: java.time.Instant): List<UUID>

    /**
     * Associations (HELLOASSO, link ACTIVE) whose eligibility is due: never read, read before [pendingBefore] while
     * they cannot collect, or before [readyBefore] once they can.
     */
    @Query(
        "SELECT c.association.id FROM HelloAssoConnection c " +
            "WHERE c.state = org.commonlink.entity.HelloAssoConnectionState.ACTIVE " +
            "AND c.association.paymentProvider = org.commonlink.payment.PaymentProviderId.HELLOASSO " +
            "AND (c.lastEligibilityCheckAt IS NULL " +
            "OR (c.canCollect = false AND c.lastEligibilityCheckAt < :pendingBefore) " +
            "OR (c.canCollect = true AND c.lastEligibilityCheckAt < :readyBefore))",
    )
    fun findActiveHelloAssoEligibilityCandidates(
        @Param("pendingBefore") pendingBefore: java.time.Instant,
        @Param("readyBefore") readyBefore: java.time.Instant,
    ): List<UUID>

    /**
     * Whether any HELLOASSO association started the "no account yet" journey, never linked, and still awaits a
     * reminder — the reminder task has nothing to do otherwise.
     */
    @Query(
        "SELECT COUNT(c) > 0 " +
            "FROM HelloAssoConnection c " +
            "WHERE c.onboardingStartedAt IS NOT NULL " +
            "AND c.linkedAt IS NULL " +
            "AND c.state = org.commonlink.entity.HelloAssoConnectionState.NOT_LINKED " +
            "AND c.association.paymentProvider = org.commonlink.payment.PaymentProviderId.HELLOASSO " +
            "AND c.reminderJ7SentAt IS NULL",
    )
    fun existsPendingHelloAssoReminder(): Boolean

    /** The links of [existsPendingHelloAssoReminder], association fetched for the email. */
    @Query(
        "SELECT c " +
            "FROM HelloAssoConnection c JOIN FETCH c.association " +
            "WHERE c.onboardingStartedAt IS NOT NULL " +
            "AND c.linkedAt IS NULL " +
            "AND c.state = org.commonlink.entity.HelloAssoConnectionState.NOT_LINKED " +
            "AND c.association.paymentProvider = org.commonlink.payment.PaymentProviderId.HELLOASSO " +
            "AND c.reminderJ7SentAt IS NULL",
    )
    fun findPendingHelloAssoReminders(): List<HelloAssoConnection>
}
