package org.commonlink.repository

import org.assertj.core.api.Assertions.assertThat
import org.commonlink.entity.AssociationProfile
import org.commonlink.entity.HelloAssoConnection
import org.commonlink.entity.HelloAssoConnectionState
import org.commonlink.payment.PaymentProviderId
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager
import org.springframework.boot.testcontainers.context.ImportTestcontainers
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.TestPropertySource
import java.time.Instant
import java.util.UUID

/**
 * Pins [HelloAssoConnection] on the test database: mapping of a linked row, unique notification token, tokens
 * encrypted at rest, and the "active link of a HELLOASSO association" probe used by the scheduled tasks.
 */
@ImportTestcontainers(TestcontainersConfig::class)
@TestPropertySource(properties = ["app.mollie.token-enc-key=AAECAwQFBgcICQoLDA0ODxAREhMUFRYXGBkaGxwdHh8="])
class HelloAssoConnectionRepositoryTest(
    @Autowired private val userRepository: UserRepository,
    @Autowired private val associationProfileRepository: AssociationProfileRepository,
    @Autowired private val repository: HelloAssoConnectionRepository,
    @Autowired private val em: TestEntityManager,
    @Autowired private val jdbc: JdbcTemplate,
) : AbstractRepositoryTest() {

    private fun association(provider: PaymentProviderId = PaymentProviderId.HELLOASSO): AssociationProfile {
        val user = userRepository.save(TestFixtures.associationUser(email = "ha-${System.nanoTime()}@example.com"))
        return associationProfileRepository.save(
            TestFixtures.associationProfile(user).apply { paymentProvider = provider }
        )
    }

    private fun connection(
        association: AssociationProfile,
        state: HelloAssoConnectionState = HelloAssoConnectionState.ACTIVE,
        token: String = "wh_${UUID.randomUUID()}",
    ) = repository.save(
        HelloAssoConnection(
            association = association,
            webhookToken = token,
            state = state,
            organizationSlug = "asso-test",
            clientId = "client_123",
            refreshToken = "refresh-secret-value",
            accessToken = "access-secret-value",
            accessTokenExpiresAt = Instant.parse("2026-11-02T10:30:00Z"),
            linkedAt = Instant.parse("2026-11-02T10:00:00Z"),
        )
    )

    private fun flushAndClear() {
        em.flush()
        em.clear()
    }

    @Test
    fun `a NOT_LINKED row carries only its notification token`() {
        val association = association()
        repository.save(HelloAssoConnection(association = association, webhookToken = "wh_not_linked"))
        flushAndClear()

        val reloaded = repository.findByWebhookToken("wh_not_linked")!!

        assertThat(reloaded.state).isEqualTo(HelloAssoConnectionState.NOT_LINKED)
        assertThat(reloaded.clientId).isNull()
        assertThat(reloaded.refreshToken).isNull()
        assertThat(reloaded.canCollect).isFalse
    }

    @Test
    fun `a linked row round-trips by association`() {
        val association = association()
        connection(association)
        flushAndClear()

        val reloaded = repository.findByAssociationId(association.id!!)!!

        assertThat(reloaded.organizationSlug).isEqualTo("asso-test")
        assertThat(reloaded.refreshToken).isEqualTo("refresh-secret-value")
        assertThat(reloaded.accessToken).isEqualTo("access-secret-value")
        assertThat(reloaded.accessTokenExpiresAt).isEqualTo(Instant.parse("2026-11-02T10:30:00Z"))
    }

    @Test
    fun `tokens are encrypted at rest`() {
        val association = association()
        connection(association)
        flushAndClear()

        val raw = jdbc.queryForMap(
            "SELECT refresh_token, access_token FROM helloasso_connections WHERE association_id = ?", association.id,
        )

        assertThat(raw["refresh_token"] as String).isNotEqualTo("refresh-secret-value").doesNotContain("refresh-secret")
        assertThat(raw["access_token"] as String).isNotEqualTo("access-secret-value").doesNotContain("access-secret")
    }

    @Test
    fun `the notification token is unique`() {
        connection(association(), token = "wh_dup")
        flushAndClear()

        assertThrows<DataIntegrityViolationException> {
            repository.saveAndFlush(HelloAssoConnection(association = association(), webhookToken = "wh_dup"))
        }
    }

    @Test
    fun `existsActiveForHelloAssoAssociations is true for an ACTIVE link of a HELLOASSO association`() {
        connection(association())
        flushAndClear()

        assertThat(repository.existsActiveForHelloAssoAssociations()).isTrue
    }

    @Test
    fun `existsActiveForHelloAssoAssociations ignores links not ACTIVE and links kept after a move to Mollie`() {
        connection(association(), state = HelloAssoConnectionState.BROKEN)
        connection(association(), state = HelloAssoConnectionState.DISCONNECTED)
        connection(association(PaymentProviderId.MOLLIE))
        flushAndClear()

        assertThat(repository.existsActiveForHelloAssoAssociations()).isFalse
    }

    @Test
    fun `pending reminders - started, never linked, HELLOASSO, D+7 not sent yet`() {
        val pending = association()
        repository.save(HelloAssoConnection(association = pending, webhookToken = "wh_p", onboardingStartedAt = Instant.now()))
        val linked = association()
        repository.save(
            HelloAssoConnection(
                association = linked, webhookToken = "wh_l", state = HelloAssoConnectionState.ACTIVE,
                onboardingStartedAt = Instant.now(), linkedAt = Instant.now(),
            )
        )
        val mollie = association(PaymentProviderId.MOLLIE)
        repository.save(HelloAssoConnection(association = mollie, webhookToken = "wh_m", onboardingStartedAt = Instant.now()))
        val done = association()
        repository.save(
            HelloAssoConnection(association = done, webhookToken = "wh_d", onboardingStartedAt = Instant.now(), reminderJ7SentAt = Instant.now())
        )
        flushAndClear()

        assertThat(repository.existsPendingHelloAssoReminder()).isTrue
        val found = repository.findPendingHelloAssoReminders()
        assertThat(found.map { it.association.id }).containsExactly(pending.id)
        // Fetched with its association: usable outside a transaction (scheduled task).
        assertThat(found.single().association.name).isNotBlank
    }

    @Test
    fun `refresh and eligibility candidates follow their cadence`() {
        val now = Instant.now()
        val stale = association()
        connection(stale)
        val fresh = association()
        repository.save(
            HelloAssoConnection(
                association = fresh, webhookToken = "wh_f", state = HelloAssoConnectionState.ACTIVE, organizationSlug = "f",
                lastRefreshAt = now, lastEligibilityCheckAt = now, canCollect = true,
            )
        )
        flushAndClear()

        assertThat(repository.findActiveHelloAssoRefreshCandidates(now.minusSeconds(3600))).contains(stale.id).doesNotContain(fresh.id)
        assertThat(repository.findActiveHelloAssoEligibilityCandidates(now.minusSeconds(7200), now.minusSeconds(86400)))
            .contains(stale.id).doesNotContain(fresh.id)
    }
}
