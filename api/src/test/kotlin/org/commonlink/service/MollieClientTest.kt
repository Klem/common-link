package org.commonlink.service

import com.fasterxml.jackson.databind.ObjectMapper
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.commonlink.config.MollieProperties
import org.commonlink.exception.MolliePaymentException
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.http.HttpMethod
import org.springframework.http.MediaType
import org.springframework.test.web.client.MockRestServiceServer
import org.springframework.test.web.client.match.MockRestRequestMatchers.header
import org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath
import org.springframework.test.web.client.match.MockRestRequestMatchers.method
import org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo
import org.springframework.test.web.client.response.MockRestResponseCreators.withServerError
import org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess
import org.springframework.web.client.RestClient
import java.math.BigDecimal

class MollieClientTest {

    private val properties = MollieProperties(
        apiKey = "test_key",
        apiBaseUrl = "https://api.mollie.com/v2",
        redirectBaseUrl = "http://localhost:3000",
        webhookUrl = "http://localhost:8080/api/public/webhooks/mollie"
    )

    private lateinit var server: MockRestServiceServer
    private lateinit var mollieClient: MollieClient

    @BeforeEach
    fun setUp() {
        val builder = RestClient.builder()
        server = MockRestServiceServer.bindTo(builder).build()
        mollieClient = MollieClient(properties, builder)
    }

    // ── createPayment ─────────────────────────────────────────────────────────

    @Test
    fun `createPayment serialises amount as string with 2 decimal places`() {
        server.expect(requestTo("https://api.mollie.com/v2/payments"))
            .andExpect(method(HttpMethod.POST))
            // Critical: value must be a string "10.00", never a number
            .andExpect(jsonPath("$.amount.value").value("10.00"))
            .andExpect(jsonPath("$.amount.currency").value("EUR"))
            .andExpect(jsonPath("$.description").value("Don – Test Campaign"))
            .andRespond(withSuccess(createPaymentResponse("tr_abc123", "open"), MediaType.APPLICATION_JSON))

        val result = mollieClient.createPayment(
            amount = BigDecimal("10"),
            description = "Don – Test Campaign",
            redirectUrl = "http://localhost:3000/return",
            webhookUrl = "http://localhost:8080/api/public/webhooks/mollie",
            metadata = mapOf("campaignId" to "c1")
        )

        assertThat(result.id).isEqualTo("tr_abc123")
        assertThat(result.status).isEqualTo(MolliePaymentStatus.OPEN)
        server.verify()
    }

    @Test
    fun `createPayment rounds amount to 2 decimal places`() {
        // 10.001 rounds down to "10.00"; 10.005 would round up to "10.01" with HALF_UP
        server.expect(requestTo("https://api.mollie.com/v2/payments"))
            .andExpect(method(HttpMethod.POST))
            .andExpect(jsonPath("$.amount.value").value("10.00"))
            .andRespond(withSuccess(createPaymentResponse("tr_xyz", "open"), MediaType.APPLICATION_JSON))

        mollieClient.createPayment(
            amount = BigDecimal("10.001"),
            description = "Test",
            redirectUrl = "http://r",
            webhookUrl = "http://w",
            metadata = emptyMap()
        )

        server.verify()
    }

    @Test
    fun `createPayment parses checkoutUrl from _links`() {
        val checkoutUrl = "https://www.mollie.com/checkout/select-method/tr_abc"
        server.expect(requestTo("https://api.mollie.com/v2/payments"))
            .andExpect(method(HttpMethod.POST))
            .andRespond(withSuccess(createPaymentResponse("tr_abc", "open", checkoutUrl), MediaType.APPLICATION_JSON))

        val result = mollieClient.createPayment(
            amount = BigDecimal("25.00"),
            description = "Test",
            redirectUrl = "http://r",
            webhookUrl = "http://w",
            metadata = emptyMap()
        )

        assertThat(result.checkoutUrl).isEqualTo(checkoutUrl)
    }

    @Test
    fun `createPayment sends Idempotency-Key header when provided`() {
        server.expect(requestTo("https://api.mollie.com/v2/payments"))
            .andExpect(method(HttpMethod.POST))
            .andExpect(header("Idempotency-Key", "test-idempotency-key-uuid"))
            .andRespond(withSuccess(createPaymentResponse("tr_idem", "open"), MediaType.APPLICATION_JSON))

        mollieClient.createPayment(
            amount = BigDecimal("10.00"),
            description = "Test",
            redirectUrl = "http://r",
            webhookUrl = "http://w",
            metadata = emptyMap(),
            idempotencyKey = "test-idempotency-key-uuid"
        )

        server.verify()
    }

    @Test
    fun `createPayment does not send Idempotency-Key when null`() {
        server.expect(requestTo("https://api.mollie.com/v2/payments"))
            .andExpect(method(HttpMethod.POST))
            .andRespond(withSuccess(createPaymentResponse("tr_noidempotency", "open"), MediaType.APPLICATION_JSON))

        // Should not throw — no Idempotency-Key expectation
        mollieClient.createPayment(
            amount = BigDecimal("10.00"),
            description = "Test",
            redirectUrl = "http://r",
            webhookUrl = "http://w",
            metadata = emptyMap(),
            idempotencyKey = null
        )

        server.verify()
    }

    @Test
    fun `createPayment omits cancelUrl from body when null`() {
        server.expect(requestTo("https://api.mollie.com/v2/payments"))
            .andExpect(method(HttpMethod.POST))
            .andExpect(jsonPath("$.cancelUrl").doesNotExist())
            .andRespond(withSuccess(createPaymentResponse("tr_noc", "open"), MediaType.APPLICATION_JSON))

        mollieClient.createPayment(
            amount = BigDecimal("10.00"),
            description = "Test",
            redirectUrl = "http://r",
            cancelUrl = null,
            webhookUrl = "http://w",
            metadata = emptyMap()
        )

        server.verify()
    }

    @Test
    fun `createPayment truncates description to 255 characters`() {
        val longDescription = "A".repeat(300)
        server.expect(requestTo("https://api.mollie.com/v2/payments"))
            .andExpect(method(HttpMethod.POST))
            .andExpect(jsonPath("$.description").value("A".repeat(255)))
            .andRespond(withSuccess(createPaymentResponse("tr_trunc", "open"), MediaType.APPLICATION_JSON))

        mollieClient.createPayment(
            amount = BigDecimal("10.00"),
            description = longDescription,
            redirectUrl = "http://r",
            webhookUrl = "http://w",
            metadata = emptyMap()
        )

        server.verify()
    }

    @Test
    fun `createPayment throws MolliePaymentException on 5xx`() {
        server.expect(requestTo("https://api.mollie.com/v2/payments"))
            .andRespond(withServerError())

        assertThatThrownBy {
            mollieClient.createPayment(
                amount = BigDecimal("10.00"),
                description = "Test",
                redirectUrl = "http://r",
                webhookUrl = "http://w",
                metadata = emptyMap()
            )
        }.isInstanceOf(MolliePaymentException::class.java)
    }

    // ── getPayment — status mapping ───────────────────────────────────────────

    @Test
    fun `getPayment maps all known statuses correctly`() {
        val cases = listOf(
            "open" to MolliePaymentStatus.OPEN,
            "pending" to MolliePaymentStatus.PENDING,
            "authorized" to MolliePaymentStatus.AUTHORIZED,
            "paid" to MolliePaymentStatus.PAID,
            "canceled" to MolliePaymentStatus.CANCELED,
            "expired" to MolliePaymentStatus.EXPIRED,
            "failed" to MolliePaymentStatus.FAILED
        )

        // MockRestServiceServer requires all expectations to be registered before any request is made
        cases.forEach { (mollieStatus, _) ->
            server.expect(requestTo("https://api.mollie.com/v2/payments/tr_$mollieStatus"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(getPaymentResponse("tr_$mollieStatus", mollieStatus), MediaType.APPLICATION_JSON))
        }

        cases.forEach { (mollieStatus, expectedEnum) ->
            val result = mollieClient.getPayment("tr_$mollieStatus")
            assertThat(result.status)
                .withFailMessage("Status '$mollieStatus' should map to $expectedEnum")
                .isEqualTo(expectedEnum)
        }

        server.verify()
    }

    @Test
    fun `getPayment maps unknown status to PENDING (no crash)`() {
        server.expect(requestTo("https://api.mollie.com/v2/payments/tr_unk"))
            .andRespond(withSuccess(getPaymentResponse("tr_unk", "some_future_status"), MediaType.APPLICATION_JSON))

        val result = mollieClient.getPayment("tr_unk")
        assertThat(result.status).isEqualTo(MolliePaymentStatus.PENDING)
    }

    @Test
    fun `authorized status is not confirmed (not PAID)`() {
        server.expect(requestTo("https://api.mollie.com/v2/payments/tr_auth"))
            .andRespond(withSuccess(getPaymentResponse("tr_auth", "authorized"), MediaType.APPLICATION_JSON))

        val result = mollieClient.getPayment("tr_auth")
        assertThat(result.status.isConfirmed).isFalse()
        assertThat(result.status.isPending).isTrue()
    }

    @Test
    fun `getPayment parses metadata map`() {
        val responseJson = """
            {
              "id": "tr_meta",
              "status": "paid",
              "amount": {"currency": "EUR", "value": "50.00"},
              "metadata": {"campaignId": "abc", "donorProfileId": "def"},
              "_links": {}
            }
        """.trimIndent()

        server.expect(requestTo("https://api.mollie.com/v2/payments/tr_meta"))
            .andRespond(withSuccess(responseJson, MediaType.APPLICATION_JSON))

        val result = mollieClient.getPayment("tr_meta")
        assertThat(result.metadata).containsEntry("campaignId", "abc")
        assertThat(result.metadata).containsEntry("donorProfileId", "def")
    }

    @Test
    fun `getPayment throws MolliePaymentException on 5xx`() {
        server.expect(requestTo("https://api.mollie.com/v2/payments/tr_err"))
            .andRespond(withServerError())

        assertThatThrownBy { mollieClient.getPayment("tr_err") }
            .isInstanceOf(MolliePaymentException::class.java)
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private fun createPaymentResponse(
        id: String,
        status: String,
        checkoutUrl: String = "https://www.mollie.com/checkout/$id"
    ) = """
        {
          "id": "$id",
          "status": "$status",
          "amount": {"currency": "EUR", "value": "10.00"},
          "metadata": {},
          "_links": {
            "checkout": {"href": "$checkoutUrl", "type": "text/html"}
          }
        }
    """.trimIndent()

    private fun getPaymentResponse(id: String, status: String) = """
        {
          "id": "$id",
          "status": "$status",
          "amount": {"currency": "EUR", "value": "10.00"},
          "metadata": {},
          "_links": {}
        }
    """.trimIndent()
}
