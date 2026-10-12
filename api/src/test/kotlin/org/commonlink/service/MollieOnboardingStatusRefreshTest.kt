package org.commonlink.service

import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.commonlink.config.MollieConnectConfig
import org.commonlink.config.MollieProperties
import org.commonlink.entity.AssociationProfile
import org.commonlink.entity.MollieConnection
import org.commonlink.entity.MollieConnectionState
import org.commonlink.entity.MollieOnboardingStatus
import org.commonlink.event.MollieOnboardingStatusChangedEvent
import org.commonlink.repository.AssociationProfileRepository
import org.commonlink.repository.MollieConnectionRepository
import org.commonlink.repository.MollieOAuthStateRepository
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.context.ApplicationEventPublisher
import org.springframework.http.HttpMethod
import org.springframework.http.MediaType
import org.springframework.test.web.client.ExpectedCount
import org.springframework.test.web.client.MockRestServiceServer
import org.springframework.test.web.client.match.MockRestRequestMatchers.method
import org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo
import org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess
import org.springframework.web.client.RestTemplate
import java.time.Duration
import java.time.Instant
import java.util.Optional
import java.util.UUID

/**
 * Unit tests (no Docker) for the sprint 20 / S2 status paths of [MollieConnectService] and the
 * shared [MollieOnboardingSnapshotWriter]: the narrowed early return (Q1), the forced refresh and
 * its 30-second limit, `complementRequested`, and event publication.
 *
 * Built by hand with mockk repositories and a plain [RestTemplate] (the production bean is a plain
 * `RestTemplate()` too) bound to [MockRestServiceServer]: an unexpected call to Mollie fails the
 * test with an AssertionError, which the service's `catch (Exception)` does not swallow.
 */
class MollieOnboardingStatusRefreshTest {

    private val connectionRepo: MollieConnectionRepository = mockk()
    private val stateRepo: MollieOAuthStateRepository = mockk()
    private val associationRepo: AssociationProfileRepository = mockk()
    private val tokenManager: MollieConnectTokenManager = mockk()
    private val eventPublisher: ApplicationEventPublisher = mockk(relaxed = true)
    private val restTemplate = RestTemplate()
    private lateinit var server: MockRestServiceServer

    private val onboardingUrl = "https://api.mollie.com/v2/onboarding/me"

    private val connections = mutableMapOf<UUID, MollieConnection>()
    private val usersToAssociation = mutableMapOf<UUID, UUID>()

    private fun config(disabled: Boolean = false, forcedIntervalSeconds: Long = 30) = MollieConnectConfig(
        clientId = "app_test",
        clientSecret = "secret",
        redirectUri = "https://api.test/callback",
        scopes = "onboarding.read",
        disableOnboardingSync = disabled,
        onboardingSync = MollieConnectConfig.OnboardingSync(forcedRefreshMinIntervalSeconds = forcedIntervalSeconds),
    )

    private fun service(config: MollieConnectConfig = config()) = MollieConnectService(
        config = config,
        connectionRepo = connectionRepo,
        stateRepo = stateRepo,
        associationRepo = associationRepo,
        restTemplate = restTemplate,
        tokenManager = tokenManager,
        snapshotWriter = MollieOnboardingSnapshotWriter(connectionRepo, eventPublisher),
        onboardingGate = mockk(),
        frontendUrl = "http://localhost:3000",
        mollieProperties = MollieProperties(),
    )

    @BeforeEach
    fun setUp() {
        server = MockRestServiceServer.bindTo(restTemplate).build()
        every { stateRepo.existsByAssociationIdAndExpiresAtAfter(any(), any()) } returns false
        every { tokenManager.getValidAccessToken(any()) } returns "access_token"
        every { connectionRepo.findByAssociationId(any()) } answers { connections[firstArg()] }
        every { connectionRepo.findByAssociationIdForUpdate(any()) } answers { connections[firstArg()] }
        every { connectionRepo.save(any<MollieConnection>()) } answers { firstArg() }
        every { associationRepo.findByUserId(any()) } answers {
            val associationId = usersToAssociation[firstArg()]
            Optional.ofNullable(associationId?.let { id -> mockk<AssociationProfile> { every { this@mockk.id } returns id } })
        }
    }

    @AfterEach
    fun tearDown() {
        server.verify()
    }

    /** Registers an association owned by a fresh user, with a connection; returns the user id. */
    private fun givenConnection(
        status: MollieOnboardingStatus,
        canReceivePayments: Boolean = false,
        syncedAgo: Duration? = Duration.ofMinutes(6),
        state: MollieConnectionState = MollieConnectionState.ACTIVE,
    ): Pair<UUID, MollieConnection> {
        val userId = UUID.randomUUID()
        val associationId = UUID.randomUUID()
        val association = mockk<AssociationProfile> { every { id } returns associationId }
        val connection = MollieConnection(
            association = association,
            accessToken = "access_token",
            refreshToken = "refresh_token",
            expiresAt = Instant.now().plusSeconds(3600),
            state = state,
            onboardingStatus = status,
            canReceivePayments = canReceivePayments,
            lastSyncedAt = syncedAgo?.let { Instant.now().minus(it) },
        )
        connections[associationId] = connection
        usersToAssociation[userId] = associationId
        return userId to connection
    }

    private fun expectMollie(status: String, canReceivePayments: Boolean = false, count: ExpectedCount = ExpectedCount.once()) {
        server.expect(count, requestTo(onboardingUrl))
            .andExpect(method(HttpMethod.GET))
            .andRespond(
                withSuccess(
                    """{"status":"$status","canReceivePayments":$canReceivePayments,"canReceiveSettlements":false}""",
                    MediaType.APPLICATION_JSON,
                )
            )
    }

    private fun publishedEvents(): List<MollieOnboardingStatusChangedEvent> {
        val events = mutableListOf<Any>()
        verify(atLeast = 0) { eventPublisher.publishEvent(capture(events)) }
        return events.filterIsInstance<MollieOnboardingStatusChangedEvent>()
    }

    // ── Q1: a connection that collects is never re-polled ─────────────────────

    @Test
    fun `COMPLETED with payments is never polled - neither status read nor forced refresh`() {
        val (userId, _) = givenConnection(MollieOnboardingStatus.COMPLETED, canReceivePayments = true, syncedAgo = Duration.ofDays(3))
        val service = service()

        service.getConnectionStatus(userId)
        val dto = service.refreshConnectionStatus(userId)

        assertEquals("COMPLETED", dto.onboardingStatus)
        assertEquals(true, dto.canReceivePayments)
        verify(exactly = 0) { tokenManager.getValidAccessToken(any()) }
    }

    @Test
    fun `COMPLETED without payments synced 6 min ago is polled - payments flip publishes one event`() {
        val (userId, connection) = givenConnection(MollieOnboardingStatus.COMPLETED, canReceivePayments = false)
        expectMollie("completed", canReceivePayments = true)

        val dto = service().getConnectionStatus(userId)

        assertEquals(true, dto.canReceivePayments)
        assertTrue(connection.canReceivePayments)
        val events = publishedEvents()
        assertEquals(1, events.size)
        assertEquals(MollieOnboardingStatus.COMPLETED, events.single().previousStatus)
        assertEquals(MollieOnboardingStatus.COMPLETED, events.single().newStatus)
    }

    @Test
    fun `status read keeps its 5-minute throttle`() {
        val (userId, _) = givenConnection(MollieOnboardingStatus.NEEDS_DATA, syncedAgo = Duration.ofMinutes(4))

        service().getConnectionStatus(userId)

        verify(exactly = 0) { tokenManager.getValidAccessToken(any()) }
    }

    // ── Forced refresh ────────────────────────────────────────────────────────

    @Test
    fun `forced refresh bypasses the 5-minute throttle`() {
        val (userId, _) = givenConnection(MollieOnboardingStatus.NEEDS_DATA, syncedAgo = Duration.ofSeconds(60))
        expectMollie("in-review")

        val dto = service().refreshConnectionStatus(userId)

        assertEquals("IN_REVIEW", dto.onboardingStatus)
    }

    @Test
    fun `two forced refreshes within seconds call Mollie once and both answer the status`() {
        val (userId, _) = givenConnection(MollieOnboardingStatus.NEEDS_DATA)
        expectMollie("in-review", count = ExpectedCount.once())
        val service = service()

        val first = service.refreshConnectionStatus(userId)
        val second = service.refreshConnectionStatus(userId)

        assertEquals("IN_REVIEW", first.onboardingStatus)
        assertEquals("IN_REVIEW", second.onboardingStatus)
        verify(exactly = 1) { tokenManager.getValidAccessToken(any()) }
    }

    @Test
    fun `the forced-refresh limit is per association`() {
        val (userA, _) = givenConnection(MollieOnboardingStatus.NEEDS_DATA)
        val (userB, _) = givenConnection(MollieOnboardingStatus.NEEDS_DATA)
        expectMollie("needs-data", count = ExpectedCount.twice())
        val service = service()

        service.refreshConnectionStatus(userA)
        service.refreshConnectionStatus(userB)

        verify(exactly = 2) { tokenManager.getValidAccessToken(any()) }
    }

    @Test
    fun `association A cannot refresh association B - the target comes from the principal only`() {
        val (userA, connectionA) = givenConnection(MollieOnboardingStatus.NEEDS_DATA)
        val (_, connectionB) = givenConnection(MollieOnboardingStatus.NEEDS_DATA)
        expectMollie("in-review")

        // Resolved outside verify blocks: the association is itself a mock.
        val idA = connectionA.association.id!!
        val idB = connectionB.association.id!!

        service().refreshConnectionStatus(userA)

        verify(exactly = 1) { tokenManager.getValidAccessToken(idA) }
        verify(exactly = 0) { tokenManager.getValidAccessToken(idB) }
        verify(exactly = 0) { connectionRepo.findByAssociationId(idB) }
        assertEquals(MollieOnboardingStatus.IN_REVIEW, connectionA.onboardingStatus)
        assertEquals(MollieOnboardingStatus.NEEDS_DATA, connectionB.onboardingStatus)
    }

    @Test
    fun `forced refresh never calls Mollie when sync is disabled or the connection is BROKEN`() {
        val (userDisabled, _) = givenConnection(MollieOnboardingStatus.NEEDS_DATA)
        val (userBroken, _) = givenConnection(MollieOnboardingStatus.NEEDS_DATA, state = MollieConnectionState.BROKEN)

        service(config(disabled = true)).refreshConnectionStatus(userDisabled)
        val brokenDto = service().refreshConnectionStatus(userBroken)

        assertTrue(brokenDto.broken)
        verify(exactly = 0) { tokenManager.getValidAccessToken(any()) }
    }

    @Test
    fun `forced refresh returns the stored status when Mollie fails`() {
        val (userId, _) = givenConnection(MollieOnboardingStatus.IN_REVIEW)
        every { tokenManager.getValidAccessToken(any()) } throws IllegalStateException("boom")

        val dto = service().refreshConnectionStatus(userId)

        assertEquals("IN_REVIEW", dto.onboardingStatus)
    }

    // ── complementRequested ───────────────────────────────────────────────────

    @Test
    fun `IN_REVIEW then NEEDS_DATA exposes complementRequested`() {
        val (userId, connection) = givenConnection(MollieOnboardingStatus.NEEDS_DATA)
        expectMollie("in-review")
        expectMollie("needs-data")
        val service = service(config(forcedIntervalSeconds = 0))

        val inReview = service.refreshConnectionStatus(userId)
        val backToNeedsData = service.refreshConnectionStatus(userId)

        assertFalse(inReview.complementRequested)
        assertTrue(backToNeedsData.complementRequested)
        assertTrue(connection.inReviewObserved)
    }

    @Test
    fun `initial NEEDS_DATA does not expose complementRequested`() {
        val (userId, _) = givenConnection(MollieOnboardingStatus.NEEDS_DATA)
        expectMollie("needs-data")

        val dto = service().getConnectionStatus(userId)

        assertEquals("NEEDS_DATA", dto.onboardingStatus)
        assertFalse(dto.complementRequested)
    }

    // ── Events ────────────────────────────────────────────────────────────────

    @Test
    fun `status change publishes exactly one event`() {
        val (userId, _) = givenConnection(MollieOnboardingStatus.NEEDS_DATA)
        expectMollie("in-review")

        service().getConnectionStatus(userId)

        val events = publishedEvents()
        assertEquals(1, events.size)
        assertEquals(MollieOnboardingStatus.NEEDS_DATA, events.single().previousStatus)
        assertEquals(MollieOnboardingStatus.IN_REVIEW, events.single().newStatus)
    }

    @Test
    fun `identical status publishes no event`() {
        val (userId, _) = givenConnection(MollieOnboardingStatus.IN_REVIEW)
        expectMollie("in-review")

        service().getConnectionStatus(userId)

        assertTrue(publishedEvents().isEmpty())
    }

    // ── Shared writer ─────────────────────────────────────────────────────────

    @Test
    fun `writer updates status-since only when the status changes and never overwrites a collecting row`() {
        val (_, connection) = givenConnection(MollieOnboardingStatus.NEEDS_DATA)
        val associationId = connection.association.id!!
        val writer = MollieOnboardingSnapshotWriter(connectionRepo, eventPublisher)
        val initialSince = Instant.now().minus(Duration.ofDays(10))
        connection.onboardingStatusSince = initialSince

        writer.writePolled(associationId, OnboardingSnapshot(MollieOnboardingStatus.NEEDS_DATA, false, false, null))
        assertEquals(initialSince, connection.onboardingStatusSince)

        writer.writePolled(associationId, OnboardingSnapshot(MollieOnboardingStatus.COMPLETED, true, true, null))
        assertTrue(connection.onboardingStatusSince.isAfter(Instant.now().minusSeconds(60)))
        assertEquals(MollieOnboardingStatus.COMPLETED, connection.onboardingStatus)

        // Collecting now: a later (regressed) snapshot must not be written.
        writer.writePolled(associationId, OnboardingSnapshot(MollieOnboardingStatus.NEEDS_DATA, false, false, null))
        assertEquals(MollieOnboardingStatus.COMPLETED, connection.onboardingStatus)
        assertTrue(connection.canReceivePayments)
        assertEquals(1, publishedEvents().size)
    }

    @Test
    fun `writer apply without publication sets the IN_REVIEW flag (OAuth callback path)`() {
        val (_, connection) = givenConnection(MollieOnboardingStatus.NEEDS_DATA)
        val writer = MollieOnboardingSnapshotWriter(connectionRepo, eventPublisher)

        val changed = writer.apply(connection, OnboardingSnapshot(MollieOnboardingStatus.IN_REVIEW, false, false, null), Instant.now())

        assertTrue(changed)
        assertTrue(connection.inReviewObserved)
        verify(exactly = 0) { eventPublisher.publishEvent(any<Any>()) }
        val slotSave = slot<MollieConnection>()
        verify(exactly = 0) { connectionRepo.save(capture(slotSave)) }
    }
}
