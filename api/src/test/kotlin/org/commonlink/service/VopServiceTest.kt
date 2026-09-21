package org.commonlink.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.commonlink.entity.VopResult
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.http.MediaType
import org.springframework.test.web.client.MockRestServiceServer
import org.springframework.test.web.client.match.MockRestRequestMatchers.header
import org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath
import org.springframework.test.web.client.match.MockRestRequestMatchers.method
import org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo
import org.springframework.test.web.client.response.MockRestResponseCreators.withServerError
import org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess
import org.springframework.http.HttpMethod
import org.springframework.web.client.RestClient

/**
 * Unit tests for [VopService] demo mode.
 *
 * Constructs [VopService] directly (no Spring context) with [demoMode] = true
 * and verifies that [VopService.verify] always returns [VopResult.MATCH] without
 * performing any real verification, regardless of the IBAN's content.
 */
class VopServiceTest {

    private val vopService = VopService(
        demoMode = true,
        apiUrl = "https://example.com",
        apiToken = "",
        objectMapper = ObjectMapper()
    )

    @Test
    fun `demo mode - always returns MATCH regardless of IBAN`() {
        val result = vopService.verify("DE89370400440532013000", "Test Organisation")

        assertEquals(VopResult.MATCH, result.result)
        assertNull(result.suggestedName)
        assertNotNull(result.rawResponse)
        assertTrue(result.rawResponse!!.contains("\"simulation\":true"))
        assertTrue(result.rawResponse!!.contains("\"verification\":\"skipped\""))
    }

    @Test
    fun `demo mode - returns MATCH even for IBAN that would have failed real checks`() {
        val result = vopService.verify("GB82WEST1234569876543A", "Anyone")

        assertEquals(VopResult.MATCH, result.result)
        assertNull(result.suggestedName)
    }

    // ── Startup validation ────────────────────────────────────────────────────

    @Test
    fun `real mode with blank api-token throws at construction`() {
        assertThrows<IllegalArgumentException> {
            VopService(demoMode = false, apiUrl = "https://example.com", apiToken = "", objectMapper = ObjectMapper())
        }
    }

    @Test
    fun `real mode with non-blank api-token constructs without error`() {
        VopService(demoMode = false, apiUrl = "https://example.com", apiToken = "secret", objectMapper = ObjectMapper())
    }

    @Test
    fun `demo mode with blank api-token constructs without error`() {
        VopService(demoMode = true, apiUrl = "https://example.com", apiToken = "", objectMapper = ObjectMapper())
    }

    // ── Real mode — Mollie Verify Payee API ──────────────────────────────────

    private lateinit var server: MockRestServiceServer
    private lateinit var realVopService: VopService

    @BeforeEach
    fun setUp() {
        val builder = RestClient.builder()
        server = MockRestServiceServer.bindTo(builder).build()
        realVopService = VopService(
            demoMode = false,
            apiUrl = "https://api.mollie.com/v2/business-accounts/payee-verifications",
            apiToken = "test_token",
            objectMapper = jacksonObjectMapper(),
            restClientBuilder = builder,
        )
    }

    @Test
    fun `real mode sends creditorBankAccount body and bearer token`() {
        server.expect(requestTo("https://api.mollie.com/v2/business-accounts/payee-verifications"))
            .andExpect(method(HttpMethod.POST))
            .andExpect(header("Authorization", "Bearer test_token"))
            .andExpect(jsonPath("$.creditorBankAccount.accountHolderName").value("Les Restos du Coeur"))
            .andExpect(jsonPath("$.creditorBankAccount.format").value("iban"))
            .andExpect(jsonPath("$.creditorBankAccount.accountNumber").value("FR7630006000011234567890189"))
            .andRespond(withSuccess(verifyPayeeResponse("match"), MediaType.APPLICATION_JSON))

        val result = realVopService.verify("FR7630006000011234567890189", "Les Restos du Coeur")

        assertEquals(VopResult.MATCH, result.result)
        assertNull(result.suggestedName)
    }

    @Test
    fun `real mode maps close-match outcome and suggested name`() {
        server.expect(requestTo("https://api.mollie.com/v2/business-accounts/payee-verifications"))
            .andRespond(withSuccess(verifyPayeeResponse("close-match", "Les Restaurants du Coeur"), MediaType.APPLICATION_JSON))

        val result = realVopService.verify("FR7630006000011234567890189", "Les Restos du Coeur")

        assertEquals(VopResult.CLOSE_MATCH, result.result)
        assertEquals("Les Restaurants du Coeur", result.suggestedName)
    }

    @Test
    fun `real mode maps no-match outcome`() {
        server.expect(requestTo("https://api.mollie.com/v2/business-accounts/payee-verifications"))
            .andRespond(withSuccess(verifyPayeeResponse("no-match"), MediaType.APPLICATION_JSON))

        val result = realVopService.verify("FR7630006000011234567890189", "Les Restos du Coeur")

        assertEquals(VopResult.NO_MATCH, result.result)
    }

    @Test
    fun `real mode maps not-available outcome to NOT_POSSIBLE`() {
        server.expect(requestTo("https://api.mollie.com/v2/business-accounts/payee-verifications"))
            .andRespond(withSuccess(verifyPayeeResponse("not-available"), MediaType.APPLICATION_JSON))

        val result = realVopService.verify("FR7630006000011234567890189", "Les Restos du Coeur")

        assertEquals(VopResult.NOT_POSSIBLE, result.result)
    }

    @Test
    fun `real mode throws BadGatewayException on 5xx`() {
        server.expect(requestTo("https://api.mollie.com/v2/business-accounts/payee-verifications"))
            .andRespond(withServerError())

        assertThrows<org.commonlink.exception.BadGatewayException> {
            realVopService.verify("FR7630006000011234567890189", "Les Restos du Coeur")
        }
    }

    private fun verifyPayeeResponse(outcome: String, suggestedName: String? = null) = """
        {
          "resource": "business-account-payee-verification",
          "mode": "test",
          "creditorBankAccount": {
            "accountHolderName": "Les Restos du Coeur",
            "format": "iban",
            "accountNumber": "FR7630006000011234567890189"
          },
          "verificationResult": {
            "outcome": "$outcome"${if (suggestedName != null) ",\n            \"accountHolderName\": \"$suggestedName\"" else ""}
          },
          "createdAt": "2026-09-06T10:00:00Z"
        }
    """.trimIndent()
}
