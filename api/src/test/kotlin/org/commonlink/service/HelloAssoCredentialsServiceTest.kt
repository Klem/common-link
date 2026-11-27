package org.commonlink.service

import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.commonlink.config.HelloAssoProperties
import org.commonlink.dto.HelloAssoOrganizationJson
import org.commonlink.dto.HelloAssoOrganizationLightJson
import org.commonlink.dto.HelloAssoVerifyRequest
import org.commonlink.entity.AssociationProfile
import org.commonlink.entity.AssociationRegistryCheck
import org.commonlink.entity.HelloAssoConnection
import org.commonlink.entity.HelloAssoKybOutcome
import org.commonlink.entity.HelloAssoLinkAttempt
import org.commonlink.entity.HelloAssoLinkAttemptOutcome
import org.commonlink.exception.BadGatewayException
import org.commonlink.exception.HelloAssoInvalidKeysException
import org.commonlink.exception.HelloAssoNotAssociationProviderException
import org.commonlink.exception.HelloAssoNotificationUrlNotConfiguredException
import org.commonlink.exception.HelloAssoOrganizationAlreadyLinkedException
import org.commonlink.exception.HelloAssoRateLimitedException
import org.commonlink.exception.HelloAssoOrganizationUnresolvedException
import org.commonlink.exception.HelloAssoUnauthorizedException
import org.commonlink.exception.HelloAssoUnavailableException
import org.commonlink.exception.RateLimitException
import org.commonlink.payment.PaymentProviderId
import org.commonlink.repository.AssociationProfileRepository
import org.commonlink.repository.AssociationRegistryCheckRepository
import org.commonlink.repository.HelloAssoConnectionRepository
import org.commonlink.repository.TestFixtures
import org.commonlink.security.AuthRateLimiter
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.slf4j.LoggerFactory
import org.springframework.dao.DataIntegrityViolationException
import java.util.Optional
import java.util.UUID

/** [HelloAssoCredentialsService]: key check outcomes, notification URL, and the secret never written anywhere. */
class HelloAssoCredentialsServiceTest {

    private val associationRepo = mockk<AssociationProfileRepository>()
    private val connectionRepo = mockk<HelloAssoConnectionRepository>()
    private val client = mockk<HelloAssoGateway>()
    private val store = HelloAssoPendingLinkStore()
    private val rateLimiter = AuthRateLimiter()
    private val registryRepo = mockk<AssociationRegistryCheckRepository>()
    private val recorder = mockk<HelloAssoLinkAttemptRecorder>()
    private val attempts = mutableListOf<HelloAssoLinkAttempt>()

    private val secret = "SECRET-entered-by-the-association"
    private val userId = UUID.randomUUID()
    private val associationId = UUID.randomUUID()
    private val tokens = HelloAssoTokens("access", "refresh", 1800)
    private lateinit var association: AssociationProfile
    private lateinit var appender: ListAppender<ILoggingEvent>
    private val rootLogger = LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME) as Logger

    private fun service(webhookUrl: String = "https://api.common-link.test/api/public/webhooks/helloasso/") = HelloAssoCredentialsService(
        associationRepo, client, store, rateLimiter, HelloAssoProperties(webhookUrl = webhookUrl),
        HelloAssoConnectionRows(connectionRepo), connectionRepo, registryRepo, recorder,
    )

    private val request = HelloAssoVerifyRequest("client_123", secret)

    /** The association under test, identified by [identifier]. */
    private fun useAssociation(identifier: String) {
        association = AssociationProfile(
            id = associationId, user = TestFixtures.associationUser(), name = "Techno Plus", identifier = identifier,
            paymentProvider = PaymentProviderId.HELLOASSO,
        )
        every { associationRepo.findByUserId(userId) } returns Optional.of(association)
    }

    @BeforeEach
    fun setUp() {
        useAssociation("W751121684")
        every { connectionRepo.existsByOrganizationSlugAndAssociationIdNot(any(), any()) } returns false
        every { registryRepo.findTopByAssociationIdOrderByCheckedAtDesc(associationId) } returns null
        attempts.clear()
        every { recorder.record(any()) } answers { attempts += firstArg<HelloAssoLinkAttempt>() }
        appender = ListAppender<ILoggingEvent>().apply { start() }
        rootLogger.addAppender(appender)
    }

    @AfterEach
    fun tearDown() {
        rootLogger.detachAppender(appender)
    }

    private fun helloAssoReturns(
        rna: String?,
        name: String = "TECHNO PLUS",
        cashIn: Boolean? = true,
        type: String? = "Association1901",
    ) {
        every { client.obtainTokens("client_123", secret) } returns tokens
        every { client.listOrganizations("access") } returns listOf(HelloAssoOrganizationLightJson("techno-plus", name))
        every { client.getOrganization("access", "techno-plus") } returns
            HelloAssoOrganizationJson("techno-plus", name, rna, cashIn, type)
    }

    /** The single journaled attempt. */
    private fun journaled(): HelloAssoLinkAttempt {
        assertEquals(1, attempts.size, "exactly one attempt journaled")
        return attempts.single()
    }

    /** No log line and no message of [ex] contains the secret, and no HelloAsso row was written. */
    private fun assertSecretNeverWritten(ex: Throwable? = null) {
        val texts = appender.list.map { it.formattedMessage } + listOfNotNull(ex?.message)
        texts.forEach { assertFalse(it.contains(secret), "secret leaked in: $it") }
        verify(exactly = 0) { connectionRepo.save(any()) }
        verify(exactly = 0) { connectionRepo.saveAndFlush(any()) }
    }

    // ── verify ─────────────────────────────────────────────────────────────

    @Test
    fun `matching keys return a one-time verifyId holding the tokens, and persist nothing`() {
        helloAssoReturns(rna = "w751121684")

        val result = service().verify(userId, request)

        assertTrue(result.matchesKyb)
        assertTrue(result.canCollect)
        assertEquals("techno-plus", result.slug)
        val pending = store.take(result.verifyId!!, associationId)
        assertNotNull(pending)
        assertEquals("refresh", pending!!.tokens.refreshToken)
        assertFalse(pending.toString().contains("refresh"))
        assertSecretNeverWritten()
    }

    @Test
    fun `another organization is a non-matching result, nothing kept`() {
        helloAssoReturns(rna = "W000000001")

        val result = service().verify(userId, request)

        assertFalse(result.matchesKyb)
        assertNull(result.verifyId)
        assertSecretNeverWritten()
    }

    @Test
    fun `isCashInCompliant absent or false reads as cannot collect`() {
        helloAssoReturns(rna = "W751121684", cashIn = null)

        assertFalse(service().verify(userId, request).canCollect)
    }

    @Test
    fun `keys refused by HelloAsso are invalid keys, without the secret anywhere`() {
        every { client.obtainTokens("client_123", secret) } throws HelloAssoUnauthorizedException("client_credentials", 401)

        val ex = assertThrows<HelloAssoInvalidKeysException> { service().verify(userId, request) }
        assertSecretNeverWritten(ex)
    }

    @Test
    fun `keys reaching several organizations are unresolved`() {
        every { client.obtainTokens(any(), any()) } returns tokens
        every { client.listOrganizations("access") } returns
            listOf(HelloAssoOrganizationLightJson("a", "A"), HelloAssoOrganizationLightJson("b", "B"))

        val ex = assertThrows<HelloAssoOrganizationUnresolvedException> { service().verify(userId, request) }
        assertSecretNeverWritten(ex)
    }

    @Test
    fun `HelloAsso unavailable is a bad gateway`() {
        every { client.obtainTokens(any(), any()) } throws HelloAssoUnavailableException("client_credentials", 503)

        val ex = assertThrows<BadGatewayException> { service().verify(userId, request) }
        assertSecretNeverWritten(ex)
    }

    @Test
    fun `the 6th check within 10 minutes is refused before any HelloAsso call`() {
        helloAssoReturns(rna = "W751121684")
        val service = service()
        repeat(HelloAssoCredentialsService.VERIFY_MAX_ATTEMPTS) { service.verify(userId, request) }

        assertThrows<RateLimitException> { service.verify(userId, request) }
        verify(exactly = HelloAssoCredentialsService.VERIFY_MAX_ATTEMPTS) { client.obtainTokens(any(), any()) }
    }

    @Test
    fun `an association not on HelloAsso is refused before any HelloAsso call or row`() {
        association.paymentProvider = PaymentProviderId.MOLLIE

        assertThrows<HelloAssoNotAssociationProviderException> { service().verify(userId, request) }
        assertThrows<HelloAssoNotAssociationProviderException> { service().ensureNotificationUrl(userId) }
        verify(exactly = 0) { client.obtainTokens(any(), any()) }
        assertSecretNeverWritten()
    }

    // ── mismatch reasons, already linked, journal ───────────────────────────

    @Test
    fun `a matching check is journaled MATCHED with what HelloAsso returned`() {
        helloAssoReturns(rna = "w751121684")

        service().verify(userId, request)

        val attempt = journaled()
        assertEquals(HelloAssoLinkAttemptOutcome.MATCHED, attempt.outcome)
        assertEquals(associationId, attempt.associationId)
        assertEquals("techno-plus", attempt.organizationSlug)
        assertEquals("Association1901", attempt.organizationType)
        assertEquals("W751121684", attempt.organizationRna)
        assertEquals("W751121684", attempt.associationRna)
        assertEquals(1, attempt.organizationsCount)
        assertEquals("clie…_123", attempt.clientIdMasked)
    }

    @Test
    fun `a different RNA returns RNA_MISMATCH with both RNAs, journaled`() {
        helloAssoReturns(rna = "W000000001")

        val result = service().verify(userId, request)

        assertEquals(HelloAssoKybOutcome.RNA_MISMATCH, result.mismatchReason)
        assertEquals("W751121684", result.associationRna)
        assertEquals("W000000001", result.organizationRna)
        assertEquals(HelloAssoLinkAttemptOutcome.RNA_MISMATCH, journaled().outcome)
    }

    @Test
    fun `blank HelloAsso RNA and another name return NAME_MISMATCH, HelloAsso RNA null`() {
        helloAssoReturns(rna = "", name = "TP")

        val result = service().verify(userId, request)

        assertFalse(result.matchesKyb)
        assertEquals(HelloAssoKybOutcome.NAME_MISMATCH, result.mismatchReason)
        assertEquals("W751121684", result.associationRna)
        assertNull(result.organizationRna)
        val attempt = journaled()
        assertEquals(HelloAssoLinkAttemptOutcome.NAME_MISMATCH, attempt.outcome)
        assertEquals("TP", attempt.organizationName)
        assertNull(attempt.organizationRna)
    }

    @Test
    fun `association identified by SIREN uses the RNA of its registry scan`() {
        useAssociation("402328694")
        every { registryRepo.findTopByAssociationIdOrderByCheckedAtDesc(associationId) } returns
            AssociationRegistryCheck(associationId = associationId, siren = "402328694", rna = "W061006975")
        helloAssoReturns(rna = "W061006975", name = "FC Mougins")

        val result = service().verify(userId, request)

        assertTrue(result.matchesKyb)
        assertEquals("W061006975", journaled().associationRna)
    }

    @Test
    fun `the registry scan that informed the KYB decision wins over a later one`() {
        val decisionId = UUID.randomUUID()
        useAssociation("402328694")
        association.decisionRegistryCheckId = decisionId
        every { registryRepo.findById(decisionId) } returns
            Optional.of(AssociationRegistryCheck(associationId = associationId, rna = "W061006975"))
        every { registryRepo.findTopByAssociationIdOrderByCheckedAtDesc(associationId) } returns
            AssociationRegistryCheck(associationId = associationId, rna = "W999999999")
        helloAssoReturns(rna = "W061006975", name = "FC Mougins")

        assertTrue(service().verify(userId, request).matchesKyb)
    }

    @Test
    fun `a decision scan without RNA does not hide the RNA of a later scan`() {
        val decisionId = UUID.randomUUID()
        useAssociation("402328694")
        association.decisionRegistryCheckId = decisionId
        // Every scan made before the 2026-11-26 fix holds no RNA.
        every { registryRepo.findById(decisionId) } returns
            Optional.of(AssociationRegistryCheck(associationId = associationId, siren = "402328694", rna = null))
        every { registryRepo.findTopByAssociationIdOrderByCheckedAtDesc(associationId) } returns
            AssociationRegistryCheck(associationId = associationId, siren = "402328694", rna = "W061006975")
        helloAssoReturns(rna = "W061006975", name = "FC Mougins")

        val result = service().verify(userId, request)

        assertTrue(result.matchesKyb)
        assertEquals("W061006975", journaled().associationRna)
    }

    @Test
    fun `SIREN association without registry RNA falls back to the name`() {
        useAssociation("402328694")
        helloAssoReturns(rna = "W061006975", name = "FC Mougins")

        val result = service().verify(userId, request)

        assertEquals(HelloAssoKybOutcome.NAME_MISMATCH, result.mismatchReason)
        assertNull(result.associationRna)
    }

    @Test
    fun `an organization held by another association is refused and journaled, nothing kept`() {
        helloAssoReturns(rna = "W751121684")
        every { connectionRepo.existsByOrganizationSlugAndAssociationIdNot("techno-plus", associationId) } returns true

        val ex = assertThrows<HelloAssoOrganizationAlreadyLinkedException> { service().verify(userId, request) }

        assertEquals(HelloAssoLinkAttemptOutcome.ORGANIZATION_ALREADY_LINKED, journaled().outcome)
        assertSecretNeverWritten(ex)
    }

    @Test
    fun `refusals before the organization is read are journaled with their outcome`() {
        every { client.obtainTokens(any(), any()) } throws HelloAssoUnauthorizedException("client_credentials", 401)
        assertThrows<HelloAssoInvalidKeysException> { service().verify(userId, request) }
        assertEquals(HelloAssoLinkAttemptOutcome.INVALID_KEYS, journaled().outcome)

        attempts.clear()
        every { client.obtainTokens(any(), any()) } returns tokens
        every { client.listOrganizations("access") } returns emptyList()
        assertThrows<HelloAssoOrganizationUnresolvedException> { service().verify(userId, request) }
        assertEquals(HelloAssoLinkAttemptOutcome.ORGANIZATION_UNRESOLVED, journaled().outcome)
        assertEquals(0, journaled().organizationsCount)

        attempts.clear()
        every { client.listOrganizations("access") } throws HelloAssoUnavailableException("GET /users/me/organizations", 503)
        assertThrows<BadGatewayException> { service().verify(userId, request) }
        assertEquals(HelloAssoLinkAttemptOutcome.HELLOASSO_UNAVAILABLE, journaled().outcome)

        attempts.clear()
        every { client.listOrganizations("access") } throws HelloAssoRateLimitedException("GET /users/me/organizations")
        assertThrows<RateLimitException> { service().verify(userId, request) }
        assertEquals(HelloAssoLinkAttemptOutcome.HELLOASSO_RATE_LIMITED, journaled().outcome)
    }

    @Test
    fun `a journal failure never blocks the key check`() {
        helloAssoReturns(rna = "W751121684")
        every { recorder.record(any()) } throws IllegalStateException("db down")

        assertTrue(service().verify(userId, request).matchesKyb)
    }

    @Test
    fun `a check refused by the local rate limit is not journaled`() {
        helloAssoReturns(rna = "W751121684")
        val service = service()
        repeat(HelloAssoCredentialsService.VERIFY_MAX_ATTEMPTS) { service.verify(userId, request) }

        assertThrows<RateLimitException> { service.verify(userId, request) }
        assertEquals(HelloAssoCredentialsService.VERIFY_MAX_ATTEMPTS, attempts.size)
    }

    // ── notification URL ───────────────────────────────────────────────────

    @Test
    fun `first call creates the NOT_LINKED row with a random token and returns its URL`() {
        val saved = slot<HelloAssoConnection>()
        every { connectionRepo.findByAssociationId(associationId) } returns null
        every { connectionRepo.saveAndFlush(capture(saved)) } answers { firstArg() }

        val url = service().ensureNotificationUrl(userId).notificationUrl

        assertEquals(43, saved.captured.webhookToken.length)
        assertEquals("https://api.common-link.test/api/public/webhooks/helloasso/${saved.captured.webhookToken}", url)
    }

    @Test
    fun `an existing row keeps its token, whatever its state`() {
        every { connectionRepo.findByAssociationId(associationId) } returns
            HelloAssoConnection(association = association, webhookToken = "stable-token")

        assertEquals(
            "https://api.common-link.test/api/public/webhooks/helloasso/stable-token",
            service().ensureNotificationUrl(userId).notificationUrl,
        )
        verify(exactly = 0) { connectionRepo.saveAndFlush(any()) }
    }

    @Test
    fun `a concurrent creation keeps the row that won`() {
        every { connectionRepo.findByAssociationId(associationId) } returnsMany listOf(
            null, HelloAssoConnection(association = association, webhookToken = "winner-token"),
        )
        every { connectionRepo.saveAndFlush(any()) } throws DataIntegrityViolationException("duplicate")

        assertTrue(service().ensureNotificationUrl(userId).notificationUrl.endsWith("/winner-token"))
    }

    @Test
    fun `a blank webhook URL still creates the token, then refuses with not configured`() {
        every { connectionRepo.findByAssociationId(associationId) } returns null
        every { connectionRepo.saveAndFlush(any()) } answers { firstArg() }

        assertThrows<HelloAssoNotificationUrlNotConfiguredException> { service(webhookUrl = " ").ensureNotificationUrl(userId) }
        verify(exactly = 1) { connectionRepo.saveAndFlush(any()) }
    }
}
