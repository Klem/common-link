package org.commonlink.service

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.assertj.core.api.Assertions.assertThat
import org.commonlink.config.BridgeProperties
import org.commonlink.entity.BridgePaymentStatus
import org.commonlink.exception.BadGatewayException
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.http.HttpMethod
import org.springframework.http.MediaType
import org.springframework.test.web.client.MockRestServiceServer
import org.springframework.test.web.client.match.MockRestRequestMatchers.content
import org.springframework.test.web.client.match.MockRestRequestMatchers.header
import org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath
import org.springframework.test.web.client.match.MockRestRequestMatchers.method
import org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo
import org.springframework.test.web.client.response.MockRestResponseCreators.withServerError
import org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess
import org.springframework.web.client.RestClient
import java.math.BigDecimal
import java.util.UUID

/**
 * Covers both [BridgePaymentInitiationService] paths: the in-process simulation used until Bridge
 * credentials exist, and the real HTTP call, stubbed with [MockRestServiceServer].
 */
class BridgePaymentInitiationServiceTest {

    // Kotlin module required to bind the wire classes' constructor parameters, exactly as the
    // Spring-configured mapper injected in production does.
    private val objectMapper = jacksonObjectMapper()
    private val payoutId = UUID.randomUUID()
    private val associationId = UUID.randomUUID().toString()

    private fun demoService() = BridgePaymentInitiationService(
        props = BridgeProperties(demoMode = true),
        objectMapper = objectMapper,
        restClient = RestClient.create(),
    )

    /** Builds a real-mode service whose HTTP calls are intercepted, plus the interceptor itself. */
    private fun realService(): Pair<BridgePaymentInitiationService, MockRestServiceServer> {
        val builder = RestClient.builder().baseUrl(BASE_URL)
        val server = MockRestServiceServer.bindTo(builder).build()
        val service = BridgePaymentInitiationService(
            props = BridgeProperties(
                demoMode = false,
                baseUrl = BASE_URL,
                clientId = "cid",
                clientSecret = "csecret",
            ),
            objectMapper = objectMapper,
            restClient = builder.build(),
        )
        return service to server
    }

    /**
     * Stubs the read-back leg every creation performs: after POSTing the link, the service reads it
     * back to confirm Bridge recorded the destination IBAN we sent.
     */
    private fun expectReadBack(server: MockRestServiceServer, iban: String, id: String = "pl_123") {
        server.expect(requestTo("$BASE_URL/v3/payment/payment-links/$id"))
            .andExpect(method(HttpMethod.GET))
            .andRespond(
                withSuccess(
                    """{"id":"$id","status":"valid","transactions":[
                       {"id":"tx_1","status":"CREA","beneficiary":{"iban":"$iban"}}]}""",
                    MediaType.APPLICATION_JSON,
                )
            )
    }

    private fun createLink(service: BridgePaymentInitiationService) = service.createPaymentLink(
        payoutId = payoutId,
        payerName = "Association Test",
        payerReference = associationId,
        payeeName = "Croix-Rouge Francaise",
        payeeIban = "FR05 3000 3000 4029 1646 5922 J55",
        amount = BigDecimal("500.00"),
        label = "Achat materiel pedagogique",
        senderIban = null,
        callbackUrl = "https://app.example.org/return",
    )

    // ── demo mode ────────────────────────────────────────────────────────────

    @Test
    fun `demo mode - returns a synthetic link without calling Bridge`() {
        val link = createLink(demoService())

        assertThat(link.id).startsWith("demo_")
        assertThat(link.url).startsWith("https://app.example.org/return")
    }

    @Test
    fun `demo mode - a simulated link is reported as settled`() {
        val state = demoService().getPaymentLink("demo_$payoutId")

        assertThat(state.status).isEqualTo(BridgePaymentStatus.ACSC)
        assertThat(state.transactionId).isNotNull()
    }

    @Test
    fun `demo mode - starts without credentials`() {
        // The whole point of the demo default: local dev must boot with no Bridge env var set.
        BridgePaymentInitiationService(BridgeProperties(demoMode = true), objectMapper, RestClient.create())
    }

    @Test
    fun `real mode - refuses to start without credentials`() {
        assertThrows<IllegalArgumentException> {
            BridgePaymentInitiationService(BridgeProperties(demoMode = false), objectMapper, RestClient.create())
        }
    }

    // ── real mode: creation ──────────────────────────────────────────────────

    @Test
    fun `real mode - sends the payee IBAN inline as a dynamic beneficiary`() {
        val (service, server) = realService()
        server.expect(requestTo("$BASE_URL/v3/payment/payment-links"))
            .andExpect(method(HttpMethod.POST))
            .andExpect(header("Client-Id", "cid"))
            .andExpect(header("Client-Secret", "csecret"))
            .andExpect(header("Bridge-Version", "2025-01-15"))
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
            // This is what makes the flow IBAN → IBAN: the destination travels in the request, so
            // nothing has to be pre-registered and no account of ours holds the funds.
            .andExpect(jsonPath("$.transactions[0].beneficiary.iban").value("FR0530003000402916465922J55"))
            .andExpect(jsonPath("$.transactions[0].beneficiary.company_name").value("Croix-Rouge Francaise"))
            .andExpect(jsonPath("$.transactions[0].amount").value(500.0))
            .andExpect(jsonPath("$.transactions[0].currency").value("EUR"))
            // client_reference at both levels: a bank statement must be traceable to the payout.
            .andExpect(jsonPath("$.transactions[0].client_reference").value(payoutId.toString()))
            .andExpect(jsonPath("$.client_reference").value(payoutId.toString()))
            .andExpect(jsonPath("$.callback_url").value("https://app.example.org/return"))
            .andRespond(
                withSuccess(
                    """{"id":"pl_123","url":"https://pay.bridgeapi.io/link/abc"}""",
                    MediaType.APPLICATION_JSON,
                )
            )
        expectReadBack(server, "FR0530003000402916465922J55")

        val link = createLink(service)

        assertThat(link.id).isEqualTo("pl_123")
        assertThat(link.url).isEqualTo("https://pay.bridgeapi.io/link/abc")
        server.verify()
    }

    @Test
    fun `real mode - omits sender_iban when the association's own IBAN is unknown`() {
        val (service, server) = realService()
        server.expect(requestTo("$BASE_URL/v3/payment/payment-links"))
            .andExpect(jsonPath("$.sender_iban").doesNotExist())
            .andRespond(withSuccess("""{"id":"pl_1","url":"https://pay/x"}""", MediaType.APPLICATION_JSON))
        expectReadBack(server, "FR0530003000402916465922J55", id = "pl_1")

        createLink(service)

        server.verify()
    }

    @Test
    fun `real mode - bounds the authorisation window so an abandoned payout cannot stay engaged`() {
        // Without an expiry, an association that closes the tab leaves the payout engaged forever:
        // there is no polling loop and an unbounded link may never emit an event.
        val (service, server) = realService()
        server.expect(requestTo("$BASE_URL/v3/payment/payment-links"))
            .andExpect(jsonPath("$.expired_date").exists())
            .andRespond(withSuccess("""{"id":"pl_1","url":"https://pay/x"}""", MediaType.APPLICATION_JSON))
        expectReadBack(server, "FR0530003000402916465922J55", id = "pl_1")

        createLink(service)

        server.verify()
    }

    @Test
    fun `real mode - refuses the transfer when Bridge recorded a different destination IBAN`() {
        // Bridge substitutes the dashboard IBAN when beneficiary.iban is absent, and the
        // dynamic-beneficiary feature must be activated on the account. If it is not, a 200 could
        // hide a debit towards an account the association never chose — so the recorded destination
        // is verified before the authorisation URL is handed out.
        val (service, server) = realService()
        server.expect(requestTo("$BASE_URL/v3/payment/payment-links"))
            .andRespond(withSuccess("""{"id":"pl_1","url":"https://pay/x"}""", MediaType.APPLICATION_JSON))
        expectReadBack(server, "FR7630006000011234567890189", id = "pl_1")

        assertThrows<BadGatewayException> { createLink(service) }
    }

    @Test
    fun `real mode - refuses the transfer when the link cannot be read back`() {
        val (service, server) = realService()
        server.expect(requestTo("$BASE_URL/v3/payment/payment-links"))
            .andRespond(withSuccess("""{"id":"pl_1","url":"https://pay/x"}""", MediaType.APPLICATION_JSON))
        server.expect(requestTo("$BASE_URL/v3/payment/payment-links/pl_1"))
            .andRespond(withServerError())

        assertThrows<BadGatewayException> { createLink(service) }
    }

    @Test
    fun `real mode - accepts a link whose read-back exposes no beneficiary yet`() {
        // A check that cannot be performed must not be turned into a failure: Bridge may answer
        // without a transaction until the association authenticates.
        val (service, server) = realService()
        server.expect(requestTo("$BASE_URL/v3/payment/payment-links"))
            .andRespond(withSuccess("""{"id":"pl_1","url":"https://pay/x"}""", MediaType.APPLICATION_JSON))
        server.expect(requestTo("$BASE_URL/v3/payment/payment-links/pl_1"))
            .andRespond(withSuccess("""{"id":"pl_1","status":"valid","transactions":[]}""", MediaType.APPLICATION_JSON))

        assertThat(createLink(service).id).isEqualTo("pl_1")
    }

    @Test
    fun `real mode - sends the paying association as Bridge's mandatory user object`() {
        // Regression: the body used to carry no `user` at all. Bridge answered every initiation
        // 400 invalid_request / "Invalid body content", naming no field, and no payout ever went
        // through. Assert the object is on the wire, not merely that the request succeeded.
        val (service, server) = realService()
        server.expect(requestTo("$BASE_URL/v3/payment/payment-links"))
            .andExpect(jsonPath("$.user.company_name").value("Association Test"))
            .andExpect(jsonPath("$.user.external_reference").value(associationId))
            .andRespond(withSuccess("""{"id":"pl_123","url":"https://pay/x"}""", MediaType.APPLICATION_JSON))
        expectReadBack(server, "FR0530003000402916465922J55")

        createLink(service)

        server.verify()
    }

    @Test
    fun `real mode - truncates the label to Bridge's 50-character limit`() {
        val (service, server) = realService()
        server.expect(requestTo("$BASE_URL/v3/payment/payment-links"))
            .andExpect(jsonPath("$.transactions[0].label").value("x".repeat(50)))
            .andRespond(withSuccess("""{"id":"pl_1","url":"https://pay/x"}""", MediaType.APPLICATION_JSON))
        expectReadBack(server, "FR7630006000011234567890189", id = "pl_1")

        service.createPaymentLink(
            payoutId, "Association Test", associationId, "Payee", "FR7630006000011234567890189",
            BigDecimal("10.00"), "x".repeat(200), null, "https://app.example.org/return",
        )

        server.verify()
    }

    @Test
    fun `real mode - truncates the beneficiary name to Bridge's 35-character limit`() {
        val (service, server) = realService()
        server.expect(requestTo("$BASE_URL/v3/payment/payment-links"))
            .andExpect(jsonPath("$.transactions[0].beneficiary.company_name").value("y".repeat(35)))
            .andRespond(withSuccess("""{"id":"pl_1","url":"https://pay/x"}""", MediaType.APPLICATION_JSON))
        expectReadBack(server, "FR7630006000011234567890189", id = "pl_1")

        service.createPaymentLink(
            payoutId, "Association Test", associationId, "y".repeat(80), "FR7630006000011234567890189",
            BigDecimal("10.00"), "Achat materiel", null, "https://app.example.org/return",
        )

        server.verify()
    }

    @Test
    fun `real mode - a Bridge error on creation surfaces as BadGatewayException`() {
        val (service, server) = realService()
        server.expect(requestTo("$BASE_URL/v3/payment/payment-links"))
            .andRespond(withServerError())

        assertThrows<BadGatewayException> { createLink(service) }
    }

    @Test
    fun `real mode - a response without a url surfaces as BadGatewayException`() {
        // Without the authorisation URL the association can never authenticate, so this is a
        // failure even though Bridge answered 200.
        val (service, server) = realService()
        server.expect(requestTo("$BASE_URL/v3/payment/payment-links"))
            .andRespond(withSuccess("""{"id":"pl_1"}""", MediaType.APPLICATION_JSON))

        assertThrows<BadGatewayException> { createLink(service) }
    }

    // ── real mode: state ─────────────────────────────────────────────────────

    @Test
    fun `real mode - maps every documented transaction status`() {
        listOf(
            "CREA" to BridgePaymentStatus.CREA,
            "ACTC" to BridgePaymentStatus.ACTC,
            "PDNG" to BridgePaymentStatus.PDNG,
            "ACSC" to BridgePaymentStatus.ACSC,
            "RJCT" to BridgePaymentStatus.RJCT,
            "PART" to BridgePaymentStatus.PART,
        ).forEach { (wire, expected) ->
            val (service, server) = realService()
            server.expect(requestTo("$BASE_URL/v3/payment/payment-links/pl_1"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(
                    withSuccess(
                        """{"id":"pl_1","status":"completed","transactions":[{"id":"tx_1","status":"$wire"}]}""",
                        MediaType.APPLICATION_JSON,
                    )
                )

            val state = service.getPaymentLink("pl_1")
            assertThat(state.status).`as`(wire).isEqualTo(expected)
            assertThat(state.transactionId).isEqualTo("tx_1")
        }
    }

    @Test
    fun `real mode - carries the rejection reason so a failure is explainable`() {
        val (service, server) = realService()
        server.expect(requestTo("$BASE_URL/v3/payment/payment-links/pl_1"))
            .andRespond(
                withSuccess(
                    """{"id":"pl_1","status":"completed","transactions":[
                       {"id":"tx_1","status":"RJCT","status_reason":"debit_account_insufficient_funds"}]}""",
                    MediaType.APPLICATION_JSON,
                )
            )

        val state = service.getPaymentLink("pl_1")

        assertThat(state.status).isEqualTo(BridgePaymentStatus.RJCT)
        assertThat(state.statusReason).isEqualTo("debit_account_insufficient_funds")
    }

    @Test
    fun `real mode - reports an expired link as terminally failed`() {
        val (service, server) = realService()
        server.expect(requestTo("$BASE_URL/v3/payment/payment-links/pl_1"))
            .andRespond(
                withSuccess("""{"id":"pl_1","status":"expired","transactions":[]}""", MediaType.APPLICATION_JSON)
            )

        val state = service.getPaymentLink("pl_1")

        assertThat(state.status).isEqualTo(BridgePaymentStatus.LINK_EXPIRED)
        assertThat(state.status.isTerminal).isTrue()
    }

    @Test
    fun `real mode - reports a revoked link as terminally failed`() {
        val (service, server) = realService()
        server.expect(requestTo("$BASE_URL/v3/payment/payment-links/pl_1"))
            .andRespond(
                withSuccess("""{"id":"pl_1","status":"revoked"}""", MediaType.APPLICATION_JSON)
            )

        assertThat(service.getPaymentLink("pl_1").status).isEqualTo(BridgePaymentStatus.LINK_REVOKED)
    }

    @Test
    fun `real mode - a still-valid link with no transaction is not yet authorised`() {
        val (service, server) = realService()
        server.expect(requestTo("$BASE_URL/v3/payment/payment-links/pl_1"))
            .andRespond(
                withSuccess("""{"id":"pl_1","status":"valid","transactions":[]}""", MediaType.APPLICATION_JSON)
            )

        val state = service.getPaymentLink("pl_1")

        assertThat(state.status).isEqualTo(BridgePaymentStatus.CREA)
        assertThat(state.status.isTerminal).isFalse()
    }

    @Test
    fun `real mode - an unrecognised transaction status is never treated as terminal`() {
        val (service, server) = realService()
        server.expect(requestTo("$BASE_URL/v3/payment/payment-links/pl_1"))
            .andRespond(
                withSuccess(
                    """{"id":"pl_1","status":"valid","transactions":[{"id":"tx_1","status":"WHAT"}]}""",
                    MediaType.APPLICATION_JSON,
                )
            )

        val state = service.getPaymentLink("pl_1")

        assertThat(state.status).isEqualTo(BridgePaymentStatus.CREA)
        assertThat(state.status.isTerminal).isFalse()
    }

    @Test
    fun `real mode - a link-level status can never be mistaken for a transaction status`() {
        // LINK_EXPIRED / LINK_REVOKED are ours, not Bridge transaction values: a transaction
        // reporting them verbatim must not be accepted.
        assertThat(BridgePaymentStatus.fromTransactionWire("LINK_EXPIRED")).isNull()
        assertThat(BridgePaymentStatus.fromTransactionWire("LINK_REVOKED")).isNull()
        assertThat(BridgePaymentStatus.fromTransactionWire(null)).isNull()
    }

    @Test
    fun `real mode - an unreachable Bridge surfaces as BadGatewayException`() {
        // The webhook handler must then answer non-2xx so Bridge retries, rather than inferring an
        // outcome from a failed read.
        val (service, server) = realService()
        server.expect(requestTo("$BASE_URL/v3/payment/payment-links/pl_1"))
            .andRespond(withServerError())

        assertThrows<BadGatewayException> { service.getPaymentLink("pl_1") }
    }

    @Test
    fun `real mode - an unparsable state surfaces as BadGatewayException`() {
        val (service, server) = realService()
        server.expect(requestTo("$BASE_URL/v3/payment/payment-links/pl_1"))
            .andRespond(withSuccess("not json at all", MediaType.APPLICATION_JSON))

        assertThrows<BadGatewayException> { service.getPaymentLink("pl_1") }
    }

    private companion object {
        const val BASE_URL = "https://api.bridgeapi.io"
    }
}
