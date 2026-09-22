package org.commonlink.service

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.assertj.core.api.Assertions.assertThat
import org.commonlink.config.BridgeProperties
import org.commonlink.entity.BridgePaymentStatus
import org.commonlink.exception.BadGatewayException
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.test.web.client.MockRestServiceServer
import org.springframework.test.web.client.match.MockRestRequestMatchers.content
import org.springframework.test.web.client.match.MockRestRequestMatchers.header
import org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath
import org.springframework.test.web.client.match.MockRestRequestMatchers.method
import org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo
import org.springframework.test.web.client.response.MockRestResponseCreators.withServerError
import org.springframework.test.web.client.response.MockRestResponseCreators.withStatus
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
        // Bridge substitutes the dashboard IBAN when beneficiary.iban is absent, so a 200 could hide
        // a debit towards an account the association never chose — the recorded destination is
        // verified before the authorisation URL is handed out.
        val (service, server) = realService()
        server.expect(requestTo("$BASE_URL/v3/payment/payment-links"))
            .andRespond(withSuccess("""{"id":"pl_1","url":"https://pay/x"}""", MediaType.APPLICATION_JSON))
        expectReadBack(server, "FR7630006000011234567890189", id = "pl_1")

        assertThrows<BadGatewayException> { createLink(service) }
    }

    @Test
    fun `real mode - accepts the masked destination Bridge discloses on read-back`() {
        // Bridge's read endpoints return the IBAN masked — `FR76XXXXXXXXXXXXXXXXXXXX250` in the
        // documented examples. Comparing that to the IBAN sent can never be an equality: every
        // creation would be refused, whatever the destination really is.
        val (service, server) = realService()
        server.expect(requestTo("$BASE_URL/v3/payment/payment-links"))
            .andRespond(withSuccess("""{"id":"pl_1","url":"https://pay/x"}""", MediaType.APPLICATION_JSON))
        expectReadBack(server, "FR05XXXXXXXXXXXXXXXXXXXXJ55", id = "pl_1")

        val link = createLink(service)

        assertThat(link.url).isEqualTo("https://pay/x")
        server.verify()
    }

    @Test
    fun `real mode - accepts the exact masked payload observed from Bridge in sandbox`() {
        // Captured from staging on 22 September 2026, payout 0065370a: `FR8117569000701793447274U39`
        // was sent and Bridge read the destination back as `FR81XXXXXXXXXXXXXXXXXXXXU39`. Every other
        // masked case here is built from the documentation; this one is the real wire format, and it
        // is the payload the strict comparison used to refuse.
        val (service, server) = realService()
        server.expect(requestTo("$BASE_URL/v3/payment/payment-links"))
            .andRespond(withSuccess("""{"id":"pl_1","url":"https://pay/x"}""", MediaType.APPLICATION_JSON))
        expectReadBack(server, "FR81XXXXXXXXXXXXXXXXXXXXU39", id = "pl_1")

        val link = service.createPaymentLink(
            payoutId, "ZO PROD.", associationId, "SCALE THAT", "FR8117569000701793447274U39",
            BigDecimal("112.00"), "Livraison viande de barbecue", null, "https://app.example.org/return",
        )

        assertThat(link.url).isEqualTo("https://pay/x")
        server.verify()
    }

    @Test
    fun `real mode - refuses a masked destination whose disclosed check digits contradict the request`() {
        // The mask must never become a blanket pass: what Bridge does disclose still has to match.
        val (service, server) = realService()
        server.expect(requestTo("$BASE_URL/v3/payment/payment-links"))
            .andRespond(withSuccess("""{"id":"pl_1","url":"https://pay/x"}""", MediaType.APPLICATION_JSON))
        expectReadBack(server, "FR76XXXXXXXXXXXXXXXXXXXXJ55", id = "pl_1")

        assertThrows<BadGatewayException> { createLink(service) }
    }

    @Test
    fun `real mode - refuses a masked destination whose disclosed tail contradicts the request`() {
        val (service, server) = realService()
        server.expect(requestTo("$BASE_URL/v3/payment/payment-links"))
            .andRespond(withSuccess("""{"id":"pl_1","url":"https://pay/x"}""", MediaType.APPLICATION_JSON))
        expectReadBack(server, "FR05XXXXXXXXXXXXXXXXXXXX189", id = "pl_1")

        assertThrows<BadGatewayException> { createLink(service) }
    }

    @Test
    fun `real mode - refuses a destination masked so completely that nothing can be verified`() {
        // The mask must never make the check vacuous: a read-back disclosing nothing would let any
        // destination through, which is exactly what this guard exists to prevent.
        val (service, server) = realService()
        server.expect(requestTo("$BASE_URL/v3/payment/payment-links"))
            .andRespond(withSuccess("""{"id":"pl_1","url":"https://pay/x"}""", MediaType.APPLICATION_JSON))
        expectReadBack(server, "XXXXXXXXXXXXXXXXXXXXXXXXXXX", id = "pl_1")

        assertThrows<BadGatewayException> { createLink(service) }
    }

    @Test
    fun `real mode - refuses a destination whose trailing character Bridge does not disclose`() {
        val (service, server) = realService()
        server.expect(requestTo("$BASE_URL/v3/payment/payment-links"))
            .andRespond(withSuccess("""{"id":"pl_1","url":"https://pay/x"}""", MediaType.APPLICATION_JSON))
        expectReadBack(server, "FR05XXXXXXXXXXXXXXXXXXXXXXX", id = "pl_1")

        assertThrows<BadGatewayException> { createLink(service) }
    }

    @Test
    fun `real mode - refuses a destination of a different length however it is masked`() {
        // A shorter IBAN is another account, never a mask of this one.
        val (service, server) = realService()
        server.expect(requestTo("$BASE_URL/v3/payment/payment-links"))
            .andRespond(withSuccess("""{"id":"pl_1","url":"https://pay/x"}""", MediaType.APPLICATION_JSON))
        expectReadBack(server, "FR05XXXXXXXXXXXXXXXXXXXJ55", id = "pl_1")

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

    /**
     * Stubs the two calls a state read performs: the link itself, then the payment request that
     * actually carries the execution status.
     */
    private fun expectState(
        server: MockRestServiceServer,
        link: String,
        requests: String = """{"resources":[]}""",
    ) {
        server.expect(requestTo("$BASE_URL/v3/payment/payment-links/pl_1"))
            .andExpect(method(HttpMethod.GET))
            .andRespond(withSuccess(link, MediaType.APPLICATION_JSON))
        server.expect(requestTo("$BASE_URL/v3/payment/payment-requests?payment_link_id=pl_1"))
            .andExpect(method(HttpMethod.GET))
            .andRespond(withSuccess(requests, MediaType.APPLICATION_JSON))
    }

    @Test
    fun `real mode - reads the settled state off the payment request, never off the link`() {
        // The regression this whole endpoint pair exists for. Bridge documents that a payment
        // link's transaction objects "do not include status or id fields", so a link reporting an
        // authorised transfer still says nothing about it. Deriving the state from the link alone
        // pinned every payout to CREA and no transfer could ever settle.
        val (service, server) = realService()
        expectState(
            server,
            link = """{"id":"pl_1","status":"completed","payment_status":"initiated_in_success",
                      "transactions":[{"amount":112,"currency":"EUR","label":"x",
                      "beneficiary":{"iban":"FR81XXXXXXXXXXXXXXXXXXXXU39"}}]}""",
            requests = """{"resources":[{"id":"pr_1","status":"ACSC","payment_link_id":"pl_1",
                         "transactions":[{"id":"tx_1","status":"ACSC"}]}],"pagination":{}}""",
        )

        val state = service.getPaymentLink("pl_1")

        assertThat(state.status).isEqualTo(BridgePaymentStatus.ACSC)
        assertThat(state.transactionId).isEqualTo("tx_1")
        server.verify()
    }

    @Test
    fun `real mode - reads the payment request the notification named, and never lists`() {
        // A link can hold several payment requests: Bridge leaves it usable after a rejection, so
        // authorising again adds a second one beside the first. When the notification names which
        // one moved, that one is read directly — no list, hence no ordering question at all.
        val (service, server) = realService()
        server.expect(requestTo("$BASE_URL/v3/payment/payment-links/pl_1"))
            .andExpect(method(HttpMethod.GET))
            .andRespond(withSuccess("""{"id":"pl_1","status":"completed"}""", MediaType.APPLICATION_JSON))
        server.expect(requestTo("$BASE_URL/v3/payment/payment-requests/pr_2"))
            .andExpect(method(HttpMethod.GET))
            .andRespond(
                withSuccess(
                    """{"id":"pr_2","status":"ACSC","payment_link_id":"pl_1","transactions":[{"id":"tx_2"}]}""",
                    MediaType.APPLICATION_JSON,
                ),
            )

        val state = service.getPaymentLink("pl_1", "pr_2")

        assertThat(state.status).isEqualTo(BridgePaymentStatus.ACSC)
        assertThat(state.transactionId).isEqualTo("tx_2")
        // Fails if the list endpoint was called: MockRestServiceServer allows no unexpected request.
        server.verify()
    }

    @Test
    fun `real mode - a payment request belonging to another link is never acted on`() {
        // The id comes from the notification body. The list endpoint is filtered by link and could
        // not return a foreign request; this one returns whatever id it is given, and settling on
        // it would settle the wrong payout. So the read degrades to the scoped list.
        val (service, server) = realService()
        server.expect(requestTo("$BASE_URL/v3/payment/payment-links/pl_1"))
            .andExpect(method(HttpMethod.GET))
            .andRespond(withSuccess("""{"id":"pl_1","status":"valid"}""", MediaType.APPLICATION_JSON))
        server.expect(requestTo("$BASE_URL/v3/payment/payment-requests/pr_9"))
            .andExpect(method(HttpMethod.GET))
            .andRespond(
                withSuccess(
                    """{"id":"pr_9","status":"ACSC","payment_link_id":"pl_other"}""",
                    MediaType.APPLICATION_JSON,
                ),
            )
        server.expect(requestTo("$BASE_URL/v3/payment/payment-requests?payment_link_id=pl_1"))
            .andExpect(method(HttpMethod.GET))
            .andRespond(withSuccess("""{"resources":[]}""", MediaType.APPLICATION_JSON))

        val state = service.getPaymentLink("pl_1", "pr_9")

        // Nothing has been authorised on our link: the foreign ACSC must not leak in.
        assertThat(state.status).isEqualTo(BridgePaymentStatus.CREA)
        assertThat(state.transactionId).isNull()
        server.verify()
    }

    @Test
    fun `real mode - an unknown payment request id falls back to the link's own requests`() {
        // A 404 is permanent: answering 502 would have Bridge retry for two days and raise a
        // technical alert on each attempt, for an id no retry can make it know.
        val (service, server) = realService()
        server.expect(requestTo("$BASE_URL/v3/payment/payment-links/pl_1"))
            .andExpect(method(HttpMethod.GET))
            .andRespond(withSuccess("""{"id":"pl_1","status":"valid"}""", MediaType.APPLICATION_JSON))
        server.expect(requestTo("$BASE_URL/v3/payment/payment-requests/pr_gone"))
            .andExpect(method(HttpMethod.GET))
            .andRespond(withStatus(HttpStatus.NOT_FOUND))
        server.expect(requestTo("$BASE_URL/v3/payment/payment-requests?payment_link_id=pl_1"))
            .andExpect(method(HttpMethod.GET))
            .andRespond(
                withSuccess(
                    """{"resources":[{"id":"pr_1","status":"PDNG","payment_link_id":"pl_1",
                       "transactions":[{"id":"tx_1"}]}]}""",
                    MediaType.APPLICATION_JSON,
                ),
            )

        val state = service.getPaymentLink("pl_1", "pr_gone")

        assertThat(state.status).isEqualTo(BridgePaymentStatus.PDNG)
        assertThat(state.transactionId).isEqualTo("tx_1")
        server.verify()
    }

    @Test
    fun `real mode - a settled request outranks a rejected sibling whatever the list order`() {
        // The 2026-09-22 regression, pinned in both directions. Bridge returned the link's two
        // requests newest-first, `resources.last()` therefore took the older rejection, and four
        // consecutive notifications each re-stamped FAILED over a transfer the bank had executed.
        val settled = """{"id":"pr_2","status":"ACSC","transactions":[{"id":"tx_2"}]}"""
        val rejected = """{"id":"pr_1","status":"RJCT","status_reason":"AC01"}"""

        listOf("$settled,$rejected", "$rejected,$settled").forEach { ordering ->
            val (service, server) = realService()
            expectState(
                server,
                link = """{"id":"pl_1","status":"completed"}""",
                requests = """{"resources":[$ordering]}""",
            )

            val state = service.getPaymentLink("pl_1")

            assertThat(state.status).isEqualTo(BridgePaymentStatus.ACSC)
            assertThat(state.transactionId).isEqualTo("tx_2")
            server.verify()
        }
    }

    @Test
    fun `real mode - a retry in flight outranks the rejection it followed`() {
        // Ranking is not "most recent" but "what the payout is waiting on": a running attempt
        // beside a dead one means the association retried, and PDNG is the honest state.
        val (service, server) = realService()
        expectState(
            server,
            link = """{"id":"pl_1","status":"valid"}""",
            requests = """{"resources":[{"id":"pr_1","status":"RJCT","status_reason":"AC01"},
                         {"id":"pr_2","status":"PDNG","transactions":[{"id":"tx_2"}]}]}""",
        )

        val state = service.getPaymentLink("pl_1")

        assertThat(state.status).isEqualTo(BridgePaymentStatus.PDNG)
        assertThat(state.statusReason).isNull()
        server.verify()
    }

    @Test
    fun `real mode - revoking posts to the documented endpoint`() {
        val (service, server) = realService()
        server.expect(requestTo("$BASE_URL/v3/payment/payment-links/pl_1/revoke"))
            .andExpect(method(HttpMethod.POST))
            .andRespond(withSuccess())

        service.revokePaymentLink("pl_1")

        server.verify()
    }

    @Test
    fun `real mode - a refused revocation is not retried, the link cannot be usable`() {
        // Bridge documents only 200 and 404. Every other 4xx describes a link already completed,
        // expired or revoked — none of them authorisable — and answering the webhook non-2xx would
        // have Bridge redeliver the same impossible revocation for two days.
        listOf(HttpStatus.NOT_FOUND, HttpStatus.CONFLICT, HttpStatus.BAD_REQUEST).forEach { status ->
            val (service, server) = realService()
            server.expect(requestTo("$BASE_URL/v3/payment/payment-links/pl_1/revoke"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withStatus(status))

            service.revokePaymentLink("pl_1")

            server.verify()
        }
    }

    @Test
    fun `real mode - a revocation Bridge could not answer leaves the amount engaged`() {
        // Nothing is known about the link, so the caller must not release the payout's amount.
        val (service, server) = realService()
        server.expect(requestTo("$BASE_URL/v3/payment/payment-links/pl_1/revoke"))
            .andExpect(method(HttpMethod.POST))
            .andRespond(withServerError())

        assertThrows<BadGatewayException> { service.revokePaymentLink("pl_1") }

        server.verify()
    }

    @Test
    fun `demo mode - revoking calls nothing`() {
        val service = demoService()

        service.revokePaymentLink("pl_1")
    }

    @Test
    fun `real mode - maps every documented payment-request status`() {
        listOf(
            "CREA" to BridgePaymentStatus.CREA,
            "ACTC" to BridgePaymentStatus.ACTC,
            "PDNG" to BridgePaymentStatus.PDNG,
            "ACSC" to BridgePaymentStatus.ACSC,
            "RJCT" to BridgePaymentStatus.RJCT,
            "PART" to BridgePaymentStatus.PART,
        ).forEach { (wire, expected) ->
            val (service, server) = realService()
            expectState(
                server,
                link = """{"id":"pl_1","status":"completed"}""",
                requests = """{"resources":[{"id":"pr_1","status":"$wire","transactions":[{"id":"tx_1"}]}]}""",
            )

            val state = service.getPaymentLink("pl_1")
            assertThat(state.status).`as`(wire).isEqualTo(expected)
            assertThat(state.transactionId).isEqualTo("tx_1")
        }
    }

    @Test
    fun `real mode - carries the rejection reason so a failure is explainable`() {
        val (service, server) = realService()
        expectState(
            server,
            link = """{"id":"pl_1","status":"completed"}""",
            requests = """{"resources":[{"id":"pr_1","status":"RJCT",
                         "status_reason":"debit_account_insufficient_funds"}]}""",
        )

        val state = service.getPaymentLink("pl_1")

        assertThat(state.status).isEqualTo(BridgePaymentStatus.RJCT)
        assertThat(state.statusReason).isEqualTo("debit_account_insufficient_funds")
    }

    @Test
    fun `real mode - reports an expired link as terminally failed`() {
        val (service, server) = realService()
        expectState(server, link = """{"id":"pl_1","status":"expired","transactions":[]}""")

        val state = service.getPaymentLink("pl_1")

        assertThat(state.status).isEqualTo(BridgePaymentStatus.LINK_EXPIRED)
        assertThat(state.status.isTerminal).isTrue()
    }

    @Test
    fun `real mode - reports a revoked link as terminally failed`() {
        val (service, server) = realService()
        expectState(server, link = """{"id":"pl_1","status":"revoked"}""")

        assertThat(service.getPaymentLink("pl_1").status).isEqualTo(BridgePaymentStatus.LINK_REVOKED)
    }

    @Test
    fun `real mode - a still-valid link with no payment request is not yet authorised`() {
        // No payment request means the association has not authenticated at its bank — CREA, and
        // the authorisation link is still the right thing to offer.
        val (service, server) = realService()
        expectState(server, link = """{"id":"pl_1","status":"valid","transactions":[]}""")

        val state = service.getPaymentLink("pl_1")

        assertThat(state.status).isEqualTo(BridgePaymentStatus.CREA)
        assertThat(state.status.isTerminal).isFalse()
    }

    @Test
    fun `real mode - an unrecognised payment-request status is never treated as terminal`() {
        val (service, server) = realService()
        expectState(
            server,
            link = """{"id":"pl_1","status":"valid"}""",
            requests = """{"resources":[{"id":"pr_1","status":"WHAT"}]}""",
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

    @Test
    fun `real mode - an unreadable payment request surfaces as BadGatewayException`() {
        // A readable link plus an unreadable payment request must not be reported as CREA: that
        // would silently downgrade a settled transfer to "not authorised yet" and, on the webhook
        // path, hand Bridge a 200 for a notification that was never applied.
        val (service, server) = realService()
        server.expect(requestTo("$BASE_URL/v3/payment/payment-links/pl_1"))
            .andRespond(withSuccess("""{"id":"pl_1","status":"completed"}""", MediaType.APPLICATION_JSON))
        server.expect(requestTo("$BASE_URL/v3/payment/payment-requests?payment_link_id=pl_1"))
            .andRespond(withServerError())

        assertThrows<BadGatewayException> { service.getPaymentLink("pl_1") }
    }

    private companion object {
        const val BASE_URL = "https://api.bridgeapi.io"
    }
}
