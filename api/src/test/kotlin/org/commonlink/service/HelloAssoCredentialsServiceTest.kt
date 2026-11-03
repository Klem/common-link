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
import org.commonlink.entity.HelloAssoConnection
import org.commonlink.exception.BadGatewayException
import org.commonlink.exception.HelloAssoInvalidKeysException
import org.commonlink.exception.HelloAssoNotAssociationProviderException
import org.commonlink.exception.HelloAssoNotificationUrlNotConfiguredException
import org.commonlink.exception.HelloAssoOrganizationUnresolvedException
import org.commonlink.exception.HelloAssoUnauthorizedException
import org.commonlink.exception.HelloAssoUnavailableException
import org.commonlink.exception.RateLimitException
import org.commonlink.payment.PaymentProviderId
import org.commonlink.repository.AssociationProfileRepository
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

    private val secret = "SECRET-entered-by-the-association"
    private val userId = UUID.randomUUID()
    private val associationId = UUID.randomUUID()
    private val tokens = HelloAssoTokens("access", "refresh", 1800)
    private lateinit var association: AssociationProfile
    private lateinit var appender: ListAppender<ILoggingEvent>
    private val rootLogger = LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME) as Logger

    private fun service(baseUrl: String = "https://api.common-link.test/") = HelloAssoCredentialsService(
        associationRepo, client, store, rateLimiter, HelloAssoProperties(notificationBaseUrl = baseUrl),
        HelloAssoConnectionRows(connectionRepo),
    )

    private val request = HelloAssoVerifyRequest("client_123", secret)

    @BeforeEach
    fun setUp() {
        association = AssociationProfile(
            id = associationId, user = TestFixtures.associationUser(), name = "Techno Plus", identifier = "W751121684",
            paymentProvider = PaymentProviderId.HELLOASSO,
        )
        every { associationRepo.findByUserId(userId) } returns Optional.of(association)
        appender = ListAppender<ILoggingEvent>().apply { start() }
        rootLogger.addAppender(appender)
    }

    @AfterEach
    fun tearDown() {
        rootLogger.detachAppender(appender)
    }

    private fun helloAssoReturns(rna: String?, name: String = "TECHNO PLUS", cashIn: Boolean? = true) {
        every { client.obtainTokens("client_123", secret) } returns tokens
        every { client.listOrganizations("access") } returns listOf(HelloAssoOrganizationLightJson("techno-plus", name))
        every { client.getOrganization("access", "techno-plus") } returns HelloAssoOrganizationJson("techno-plus", name, rna, cashIn)
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
    fun `a blank base URL still creates the token, then refuses with not configured`() {
        every { connectionRepo.findByAssociationId(associationId) } returns null
        every { connectionRepo.saveAndFlush(any()) } answers { firstArg() }

        assertThrows<HelloAssoNotificationUrlNotConfiguredException> { service(baseUrl = " ").ensureNotificationUrl(userId) }
        verify(exactly = 1) { connectionRepo.saveAndFlush(any()) }
    }
}
