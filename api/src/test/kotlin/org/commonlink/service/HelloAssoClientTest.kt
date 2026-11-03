package org.commonlink.service

import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import org.commonlink.config.HelloAssoProperties
import org.commonlink.dto.HelloAssoInitCheckoutJson
import org.commonlink.dto.HelloAssoPayerJson
import org.commonlink.exception.HelloAssoException
import org.commonlink.exception.HelloAssoNotEligibleException
import org.commonlink.exception.HelloAssoRateLimitedException
import org.commonlink.exception.HelloAssoUnauthorizedException
import org.commonlink.exception.HelloAssoUnavailableException
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.slf4j.LoggerFactory
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.test.web.client.ExpectedCount
import org.springframework.test.web.client.MockRestServiceServer
import org.springframework.test.web.client.match.MockRestRequestMatchers.content
import org.springframework.test.web.client.match.MockRestRequestMatchers.header
import org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath
import org.springframework.test.web.client.match.MockRestRequestMatchers.method
import org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo
import org.springframework.test.web.client.response.MockRestResponseCreators.withStatus
import org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess
import org.springframework.util.LinkedMultiValueMap
import org.springframework.web.client.RestClient
import java.time.Duration

/**
 * [HelloAssoClient] against a simulated HelloAsso: token calls, status mapping, bounded 429 retries, and — above all —
 * no secret, token nor HelloAsso response body in logs or exception messages.
 */
class HelloAssoClientTest {

    private val properties = HelloAssoProperties()
    private val tokenUrl = properties.oauthTokenUrl
    private val api = properties.apiBaseUrl

    private val secret = "SECRET-client-secret-value"
    private val refreshToken = "REFRESH-token-value"
    private val accessToken = "ACCESS-token-value"
    private val leakyBody = """{"error":"invalid_grant","echo":"$secret $refreshToken $accessToken"}"""

    private lateinit var server: MockRestServiceServer
    private lateinit var client: HelloAssoClient
    private lateinit var appender: ListAppender<ILoggingEvent>
    private val sleeps = mutableListOf<Duration>()
    private val logger = LoggerFactory.getLogger(HelloAssoClient::class.java) as Logger

    @BeforeEach
    fun setUp() {
        val builder = RestClient.builder()
        server = MockRestServiceServer.bindTo(builder).build()
        client = HelloAssoClient(properties, builder, HelloAssoAuthRateLimiter())
        client.sleeper = { sleeps += it }
        appender = ListAppender<ILoggingEvent>().apply { start() }
        logger.addAppender(appender)
    }

    @AfterEach
    fun tearDown() {
        logger.detachAppender(appender)
    }

    private fun tokenJson(access: String = "new-access", refresh: String = "new-refresh") =
        """{"access_token":"$access","refresh_token":"$refresh","token_type":"bearer","expires_in":1800}"""

    /** Neither the logs nor [ex]'s message may contain a secret, a token or the response body. */
    private fun assertNothingLeaked(ex: Throwable? = null) {
        val texts = appender.list.map { it.formattedMessage } + listOfNotNull(ex?.message)
        for (text in texts) {
            for (sensitive in listOf(secret, refreshToken, accessToken, "invalid_grant", "echo")) {
                assertFalse(text.contains(sensitive), "leaked '$sensitive' in: $text")
            }
        }
    }

    // ── Token calls ────────────────────────────────────────────────────────

    @Test
    fun `obtainTokens posts the client_credentials form and parses the pair`() {
        server.expect(requestTo(tokenUrl))
            .andExpect(method(HttpMethod.POST))
            .andExpect(header("User-Agent", HelloAssoClient.USER_AGENT))
            .andExpect(content().formData(LinkedMultiValueMap<String, String>().apply {
                add("grant_type", "client_credentials")
                add("client_id", "client_123")
                add("client_secret", secret)
            }))
            .andRespond(withSuccess(tokenJson(), MediaType.APPLICATION_JSON))

        val tokens = client.obtainTokens("client_123", secret)

        assertEquals("new-access", tokens.accessToken)
        assertEquals("new-refresh", tokens.refreshToken)
        assertEquals(1800, tokens.expiresIn)
        assertFalse(tokens.toString().contains("new-access"))
        server.verify()
        assertNothingLeaked()
    }

    @Test
    fun `refreshTokens posts the refresh_token form`() {
        server.expect(requestTo(tokenUrl))
            .andExpect(content().formData(LinkedMultiValueMap<String, String>().apply {
                add("grant_type", "refresh_token")
                add("refresh_token", refreshToken)
            }))
            .andRespond(withSuccess(tokenJson(), MediaType.APPLICATION_JSON))

        assertEquals("new-refresh", client.refreshTokens(refreshToken).refreshToken)
        server.verify()
    }

    @Test
    fun `rejected keys are an unauthorized error, without leaking the secret nor the response body`() {
        server.expect(requestTo(tokenUrl))
            .andRespond(withStatus(HttpStatus.BAD_REQUEST).body(leakyBody).contentType(MediaType.APPLICATION_JSON))

        val ex = assertThrows<HelloAssoUnauthorizedException> { client.obtainTokens("client_123", secret) }

        assertNothingLeaked(ex)
    }

    @Test
    fun `a rejected refresh token is an unauthorized error, without leaking it`() {
        server.expect(requestTo(tokenUrl))
            .andRespond(withStatus(HttpStatus.UNAUTHORIZED).body(leakyBody).contentType(MediaType.APPLICATION_JSON))

        val ex = assertThrows<HelloAssoUnauthorizedException> { client.refreshTokens(refreshToken) }

        assertNothingLeaked(ex)
    }

    @Test
    fun `a 429 on a token call is never retried`() {
        server.expect(ExpectedCount.once(), requestTo(tokenUrl)).andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS))

        assertThrows<HelloAssoRateLimitedException> { client.refreshTokens(refreshToken) }
        server.verify()
        assertEquals(emptyList<Duration>(), sleeps)
    }

    @Test
    fun `an incomplete token response is an unavailable error`() {
        server.expect(requestTo(tokenUrl))
            .andRespond(withSuccess("""{"access_token":"$accessToken"}""", MediaType.APPLICATION_JSON))

        val ex = assertThrows<HelloAssoUnavailableException> { client.obtainTokens("client_123", secret) }
        assertNothingLeaked(ex)
    }

    @Test
    fun `the local auth limit refuses the 11th token call within 10 seconds without any HTTP call`() {
        server.expect(ExpectedCount.times(10), requestTo(tokenUrl)).andRespond(withSuccess(tokenJson(), MediaType.APPLICATION_JSON))

        repeat(10) { client.refreshTokens(refreshToken) }
        assertThrows<HelloAssoRateLimitedException> { client.refreshTokens(refreshToken) }
        server.verify()
    }

    // ── Authenticated calls ────────────────────────────────────────────────

    @Test
    fun `get sends the bearer token and parses the body`() {
        server.expect(requestTo("$api/organizations/asso-test"))
            .andExpect(method(HttpMethod.GET))
            .andExpect(header("Authorization", "Bearer $accessToken"))
            .andExpect(header("User-Agent", HelloAssoClient.USER_AGENT))
            .andRespond(withSuccess("""{"name":"Asso"}""", MediaType.APPLICATION_JSON))

        val body = client.get("/organizations/asso-test", accessToken, Map::class.java)

        assertEquals("Asso", body["name"])
        assertNothingLeaked()
    }

    @Test
    fun `a 401 is an unauthorized error`() {
        server.expect(requestTo("$api/x")).andRespond(withStatus(HttpStatus.UNAUTHORIZED).body(leakyBody))

        val ex = assertThrows<HelloAssoUnauthorizedException> { client.get("/x", accessToken, Map::class.java) }
        assertNothingLeaked(ex)
    }

    @Test
    fun `a 409 is a not-eligible error`() {
        server.expect(requestTo("$api/x")).andRespond(withStatus(HttpStatus.CONFLICT).body(leakyBody))

        val ex = assertThrows<HelloAssoNotEligibleException> {
            client.post("/x", accessToken, mapOf("a" to 1), Map::class.java)
        }
        assertNothingLeaked(ex)
    }

    @Test
    fun `a 429 is retried twice with growing delays, then an error`() {
        server.expect(ExpectedCount.times(3), requestTo("$api/x")).andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS))

        assertThrows<HelloAssoRateLimitedException> { client.get("/x", accessToken, Map::class.java) }
        server.verify()
        assertEquals(listOf(Duration.ofSeconds(1), Duration.ofSeconds(2)), sleeps)
    }

    @Test
    fun `a 429 followed by a success returns the body`() {
        server.expect(requestTo("$api/x")).andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS))
        server.expect(requestTo("$api/x")).andRespond(withSuccess("""{"ok":true}""", MediaType.APPLICATION_JSON))

        assertEquals(true, client.get("/x", accessToken, Map::class.java)["ok"])
    }

    @Test
    fun `a 5xx is an unavailable error, without leaking the body`() {
        server.expect(requestTo("$api/x")).andRespond(withStatus(HttpStatus.BAD_GATEWAY).body(leakyBody))

        val ex: HelloAssoException = assertThrows<HelloAssoUnavailableException> { client.get("/x", accessToken, Map::class.java) }
        assertNothingLeaked(ex)
    }

    // ── Wire format of the gateway methods (shapes of the sandbox probe of 2026-11-02 and dev.helloasso.com) ─────

    @Test
    fun `listOrganizations parses organizationSlug`() {
        server.expect(requestTo("$api/users/me/organizations")).andRespond(
            withSuccess(
                """[{"organizationSlug":"techno-plus-techno-t","name":"TECHNO PLUS","role":"OrganizationAdmin","city":"Paris"}]""",
                MediaType.APPLICATION_JSON,
            )
        )

        assertEquals("techno-plus-techno-t", client.listOrganizations(accessToken).single().organizationSlug)
    }

    @Test
    fun `getOrganization binds isCashInCompliant and rnaNumber`() {
        server.expect(requestTo("$api/organizations/techno-plus-techno-t")).andRespond(
            withSuccess(
                """{"organizationSlug":"techno-plus-techno-t","name":"TECHNO PLUS","isCashInCompliant":true,""" +
                    """"isAuthenticated":false,"rnaNumber":"W751121684","type":"Association1901"}""",
                MediaType.APPLICATION_JSON,
            )
        )

        val organization = client.getOrganization(accessToken, "techno-plus-techno-t")

        assertEquals(true, organization.cashInCompliant)
        assertEquals("W751121684", organization.rnaNumber)
    }

    @Test
    fun `createCheckoutIntent sends only documented fields and parses id and redirectUrl`() {
        server.expect(requestTo("$api/organizations/techno-plus/checkout-intents"))
            .andExpect(method(HttpMethod.POST))
            .andExpect(jsonPath("$.totalAmount").value(2550))
            .andExpect(jsonPath("$.initialAmount").value(2550))
            .andExpect(jsonPath("$.containsDonation").value(true))
            .andExpect(jsonPath("$.payer.country").value("FRA"))
            .andExpect(jsonPath("$.payer.firstName").doesNotExist())
            .andExpect(jsonPath("$.metadata.publicRef").value("abc"))
            .andRespond(withSuccess("""{"id":4242,"redirectUrl":"https://checkout.helloasso.com/x"}""", MediaType.APPLICATION_JSON))

        val created = client.createCheckoutIntent(
            accessToken, "techno-plus",
            HelloAssoInitCheckoutJson(
                2550, 2550, "Don - X", "https://app/b", "https://app/e", "https://app/r", true,
                payer = HelloAssoPayerJson(country = "FRA"), metadata = mapOf("publicRef" to "abc"),
            ),
        )

        assertEquals(4242L, created.id)
        assertEquals("https://checkout.helloasso.com/x", created.redirectUrl)
    }

    @Test
    fun `createCheckoutIntent omits a null payer entirely`() {
        server.expect(requestTo("$api/organizations/techno-plus/checkout-intents"))
            .andExpect(jsonPath("$.payer").doesNotExist())
            .andExpect(jsonPath("$.metadata").doesNotExist())
            .andRespond(withSuccess("""{"id":1,"redirectUrl":"https://x"}""", MediaType.APPLICATION_JSON))

        client.createCheckoutIntent(
            accessToken, "techno-plus",
            HelloAssoInitCheckoutJson(100, 100, "Don - X", "https://app/b", "https://app/e", "https://app/r", true),
        )
        server.verify()
    }

    @Test
    fun `getCheckoutIntent parses the order payments`() {
        server.expect(requestTo("$api/organizations/techno-plus/checkout-intents/4242")).andRespond(
            withSuccess(
                """{"id":4242,"metadata":{"publicRef":"abc"},"order":{"id":9,"checkoutIntentId":4242,""" +
                    """"payments":[{"id":77,"amount":2550,"state":"Authorized","paymentMeans":"Card","cashOutState":"MoneyIn"}]}}""",
                MediaType.APPLICATION_JSON,
            )
        )

        val intent = client.getCheckoutIntent(accessToken, "techno-plus", 4242)
        val payment = intent.order!!.payments!!.single()

        assertEquals("Authorized", payment.state)
        assertEquals("Card", payment.paymentMeans)
        assertEquals(2550, payment.amount)
        assertEquals("abc", intent.metadata!!["publicRef"])
    }
}
