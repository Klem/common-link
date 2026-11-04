package org.commonlink.repository

import org.commonlink.entity.HelloAssoConnection
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID

/**
 * Persistence of [HelloAssoConnection] — one row per association, never deleted on disconnection.
 *
 * The entity is saved whole (no `@DynamicUpdate`): a full-row `save` is only safe under the row lock
 * ([findByAssociationIdForUpdate], [HelloAssoConnectionLocking]), otherwise it can write back a refresh token rotated
 * meanwhile. Writers that only record a timestamp without the lock use the targeted `mark…` updates below.
 */
interface HelloAssoConnectionRepository : JpaRepository<HelloAssoConnection, UUID>, HelloAssoConnectionLocking {

    /** The association's HelloAsso row, or null when it has none yet. Read only: never save it back without the lock. */
    fun findByAssociationId(associationId: UUID): HelloAssoConnection?

    /** Records a notification recognised as CommonLink's on [connectionId]; touches no other column. */
    @Transactional
    @Modifying
    @Query("UPDATE HelloAssoConnection c SET c.webhookLastReceivedAt = :at, c.updatedAt = :at WHERE c.id = :connectionId")
    fun markWebhookReceived(@Param("connectionId") connectionId: UUID, @Param("at") at: Instant): Int

    /** Records that the reconciler confirmed a payment of [associationId] without its notification; touches no other column. */
    @Transactional
    @Modifying
    @Query(
        "UPDATE HelloAssoConnection c SET c.webhookMissingAt = :at, c.updatedAt = :at " +
            "WHERE c.association.id = :associationId",
    )
    fun markWebhookMissing(@Param("associationId") associationId: UUID, @Param("at") at: Instant): Int

    /**
     * Records the start of the "no HelloAsso account yet" journey of [associationId], only the first time
     * (`onboarding_started_at` still null); touches no other column.
     *
     * @return 1 when recorded now, 0 when already started (or no row).
     */
    @Transactional
    @Modifying
    @Query(
        "UPDATE HelloAssoConnection c SET c.onboardingStartedAt = :at, c.updatedAt = :at " +
            "WHERE c.association.id = :associationId AND c.onboardingStartedAt IS NULL",
    )
    fun markOnboardingStarted(@Param("associationId") associationId: UUID, @Param("at") at: Instant): Int

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
