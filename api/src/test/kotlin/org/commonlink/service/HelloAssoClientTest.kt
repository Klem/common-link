package org.commonlink.service

import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import org.commonlink.config.HelloAssoProperties
import org.commonlink.dto.HelloAssoInitCheckoutJson
import org.commonlink.dto.HelloAssoOrderLine
import org.commonlink.dto.HelloAssoPayerJson
import org.commonlink.exception.HelloAssoException
import org.commonlink.exception.HelloAssoNotEligibleException
import org.commonlink.exception.HelloAssoRateLimitedException
import org.commonlink.exception.HelloAssoUnauthorizedException
import org.commonlink.exception.HelloAssoRequestRefusedException
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

    @Test
    fun `a 400 or 422 is a refused request, without leaking the body`() {
        server.expect(requestTo("$api/x")).andRespond(withStatus(HttpStatus.BAD_REQUEST).body(leakyBody))
        server.expect(requestTo("$api/x")).andRespond(withStatus(HttpStatus.UNPROCESSABLE_CONTENT).body(leakyBody))

        val ex: HelloAssoException = assertThrows<HelloAssoRequestRefusedException> { client.get("/x", accessToken, Map::class.java) }
        assertNothingLeaked(ex)
        assertThrows<HelloAssoRequestRefusedException> { client.get("/x", accessToken, Map::class.java) }
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

    /** The order total and lines of a paid checkout-intent survive the real client's deserialization (FD0-05). */
    @Test
    fun `getCheckoutIntent reads the order total and lines`() {
        val body = javaClass.getResource("/helloasso/cashout/checkout-intent-with-contribution.json")!!.readText()
        server.expect(requestTo("$api/organizations/asso-exemple/checkout-intents/30001"))
            .andRespond(withSuccess(body, MediaType.APPLICATION_JSON))

        val order = client.getCheckoutIntent(accessToken, "asso-exemple", 30001).order!!

        assertEquals(1000, order.payments!!.single().amount)
        assertEquals(1000, order.totalCents())
        assertEquals(listOf(HelloAssoOrderLine(1000, "Payment")), order.lines())
    }

    /** An order total or lines in an unexpected shape never break the parsing of the payment. */
    @Test
    fun `getCheckoutIntent tolerates an unexpected order total or lines shape`() {
        server.expect(requestTo("$api/organizations/techno-plus/checkout-intents/4242")).andRespond(
            withSuccess(
                """{"id":4242,"order":{"id":9,"amount":2550,"items":"none",""" +
                    """"payments":[{"id":77,"amount":2550,"state":"Authorized"}]}}""",
                MediaType.APPLICATION_JSON,
            )
        )

        val order = client.getCheckoutIntent(accessToken, "techno-plus", 4242).order!!

        assertEquals(2550, order.payments!!.single().amount)
        assertEquals(null, order.totalCents())
        assertEquals(null, order.lines())
    }

    // ── E2: GET /payments/{id} (cash-out tracking, FD3-01) ─────────────────────────────────────────────────────────

    private fun fixture(name: String) = javaClass.getResource("/helloasso/cashout/$name")!!.readText()

    @Test
    fun `getPayment reads the payout state of each E2 fixture`() {
        val expected = listOf(
            Triple("payment-transfered.json", "Transfered", null),
            Triple("payment-waiting-cashout.json", "WaitingForCashOutConfirmation", null),
            Triple("payment-cashedout.json", "CashedOut", 60001L),
            Triple("payment-refunded.json", "Refunded", null),
        )
        expected.forEach { (file, _, _) ->
            server.expect(requestTo("$api/payments/70001"))
                .andExpect(header("Authorization", "Bearer $accessToken"))
                .andRespond(withSuccess(fixture(file), MediaType.APPLICATION_JSON))
        }

        expected.forEach { (file, cashOutState, idCashOut) ->
            val payment = client.getPayment(accessToken, 70001)
            assertEquals(70001L, payment.id, file)
            assertEquals(1000, payment.amount, file)
            assertEquals(cashOutState, payment.cashOutState, file)
            assertEquals(idCashOut, payment.idCashOut, file)
        }
        server.verify()
    }

    @Test
    fun `getPayment binds the cash-out date and the refunded state`() {
        server.expect(requestTo("$api/payments/70001")).andRespond(withSuccess(fixture("payment-cashedout.json"), MediaType.APPLICATION_JSON))
        server.expect(requestTo("$api/payments/70001")).andRespond(withSuccess(fixture("payment-refunded.json"), MediaType.APPLICATION_JSON))

        val cashedOut = client.getPayment(accessToken, 70001)
        val refunded = client.getPayment(accessToken, 70001)

        assertEquals("Authorized", cashedOut.state)
        assertEquals("2026-11-12T10:00:00+01:00", cashedOut.cashOutDate)
        assertEquals("Refunded", refunded.state)
        assertEquals(null, refunded.cashOutDate)
    }

    @Test
    fun `getPayment maps 404, 429 and 401 like the other calls`() {
        server.expect(requestTo("$api/payments/1")).andRespond(withStatus(HttpStatus.NOT_FOUND).body(leakyBody))
        server.expect(ExpectedCount.times(3), requestTo("$api/payments/2")).andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS))
        server.expect(requestTo("$api/payments/3")).andRespond(withStatus(HttpStatus.UNAUTHORIZED).body(leakyBody))

        val notFound: HelloAssoException = assertThrows<HelloAssoUnavailableException> { client.getPayment(accessToken, 1) }
        assertThrows<HelloAssoRateLimitedException> { client.getPayment(accessToken, 2) }
        val unauthorized: HelloAssoException = assertThrows<HelloAssoUnauthorizedException> { client.getPayment(accessToken, 3) }
        assertNothingLeaked(notFound)
        assertNothingLeaked(unauthorized)
    }

    @Test
    fun `getPayment never logs the response body, which carries the payer's identity`() {
        server.expect(requestTo("$api/payments/70001")).andRespond(withSuccess(fixture("payment-cashedout.json"), MediaType.APPLICATION_JSON))
        server.expect(requestTo("$api/payments/70002")).andRespond(withStatus(HttpStatus.BAD_GATEWAY).body(fixture("payment-cashedout.json")))

        client.getPayment(accessToken, 70001)
        assertThrows<HelloAssoUnavailableException> { client.getPayment(accessToken, 70002) }

        val logs = appender.list.joinToString("\n") { it.formattedMessage }
        for (personal in listOf("donateur@example.org", "Exemple", "rue de l", "75000", "paiement-attestation")) {
            assertFalse(logs.contains(personal), "logged '$personal' in: $logs")
        }
    }

    @Test
    fun `getCashOutNetTotal asks for JSON and sums credits minus debits, the debit included`() {
        server.expect(requestTo("$api/organizations/mon-asso/cash-out/60001/export"))
            .andExpect(method(HttpMethod.GET))
            .andExpect(header("Authorization", "Bearer $accessToken"))
            .andExpect(header("Accept", MediaType.APPLICATION_JSON_VALUE))
            .andRespond(withSuccess(fixture("cashout-export.json"), MediaType.APPLICATION_JSON))

        // 10.0 + 25.0 − 5.0: the payout carries another form's payment and a refund, not only our donation.
        assertEquals(0, java.math.BigDecimal("30.0").compareTo(client.getCashOutNetTotal(accessToken, "mon-asso", 60001)))
        server.verify()
    }

    @Test
    fun `getCashOutNetTotal sums decimal amounts without float rounding`() {
        server.expect(requestTo("$api/organizations/mon-asso/cash-out/1/export")).andRespond(
            withSuccess("""[{"credit":0.1,"debit":0},{"credit":0.2,"debit":0},{"credit":0,"debit":0.3}]""", MediaType.APPLICATION_JSON),
        )
        assertEquals(0, java.math.BigDecimal.ZERO.compareTo(client.getCashOutNetTotal(accessToken, "mon-asso", 1)))
    }

    @Test
    fun `an empty export is no total, never a zero one`() {
        server.expect(requestTo("$api/organizations/mon-asso/cash-out/5/export")).andRespond(withSuccess("[]", MediaType.APPLICATION_JSON))
        assertThrows<HelloAssoUnavailableException> { client.getCashOutNetTotal(accessToken, "mon-asso", 5) }
    }

    @Test
    fun `getCashOutNetTotal maps errors like the other calls and never logs the export, which carries payers' data`() {
        val export = """[{"paymentId":1,"credit":12.5,"debit":0,"cashOutId":7,"payerFirstName":"Camille","payerLastName":"Exemple",
            |"payerEmail":"donateur@example.org","payerAddress":"1 rue de la Paix","payerZipCode":"75000"}]""".trimMargin()
        server.expect(requestTo("$api/organizations/mon-asso/cash-out/7/export")).andRespond(withSuccess(export, MediaType.APPLICATION_JSON))
        server.expect(requestTo("$api/organizations/mon-asso/cash-out/8/export")).andRespond(withStatus(HttpStatus.BAD_GATEWAY).body(export))
        server.expect(requestTo("$api/organizations/mon-asso/cash-out/9/export")).andRespond(withStatus(HttpStatus.UNAUTHORIZED).body(export))

        assertEquals(0, java.math.BigDecimal("12.5").compareTo(client.getCashOutNetTotal(accessToken, "mon-asso", 7)))
        val unavailable: HelloAssoException = assertThrows<HelloAssoUnavailableException> { client.getCashOutNetTotal(accessToken, "mon-asso", 8) }
        val unauthorized: HelloAssoException = assertThrows<HelloAssoUnauthorizedException> { client.getCashOutNetTotal(accessToken, "mon-asso", 9) }

        val texts = appender.list.map { it.formattedMessage } + listOfNotNull(unavailable.message, unauthorized.message)
        for (personal in listOf("Camille", "Exemple", "donateur@example.org", "rue de la Paix", "75000")) {
            assertFalse(texts.any { it.contains(personal) }, "leaked '$personal' in: $texts")
        }
        assertNothingLeaked(unauthorized)
    }
}
