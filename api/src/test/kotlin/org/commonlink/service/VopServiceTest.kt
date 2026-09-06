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
 * Unit tests for [VopService] demo mode simulation.
 *
 * Constructs [VopService] directly (no Spring context) with [demoMode] = true
 * and verifies that [VopService.verify] returns the correct [VopResult] based
 * on the last alphanumeric character of the IBAN.
 */
class VopServiceTest {

    private val vopService = VopService(
        demoMode = true,
        apiUrl = "https://example.com",
        apiToken = "",
        objectMapper = ObjectMapper()
    )

    // ── MATCH (even digit: 0,2,4,6,8) ────────────────────────────────────────

    @Test
    fun `demo mode - IBAN ending in even digit returns MATCH`() {
        // Ends in '0' → MATCH
        val result = vopService.verify("DE89370400440532013000", "Test Organisation")

        assertEquals(VopResult.MATCH, result.result)
        assertNull(result.suggestedName)
        assertNotNull(result.rawResponse)
        assertTrue(result.rawResponse!!.contains("MATCH"))
    }

    @Test
    fun `demo mode - IBAN ending in 2 returns MATCH`() {
        // Ends in '2' → MATCH
        val result = vopService.verify("GB82WEST12345698765432", "Test Organisation")

        assertEquals(VopResult.MATCH, result.result)
        assertNull(result.suggestedName)
    }

    // ── CLOSE_MATCH (odd digit: 1,3,5) ───────────────────────────────────────

    @Test
    fun `demo mode - IBAN ending in odd digit 1 returns CLOSE_MATCH with reversed name`() {
        // Ends in '1' → CLOSE_MATCH; suggestedName = words reversed
        val result = vopService.verify("FR7630006000011234567890181", "Jean Dupont")

        assertEquals(VopResult.CLOSE_MATCH, result.result)
        assertEquals("Dupont Jean", result.suggestedName)
        assertNotNull(result.rawResponse)
        assertTrue(result.rawResponse!!.contains("CLOSE_MATCH"))
    }

    @Test
    fun `demo mode - IBAN ending in 3 returns CLOSE_MATCH with reversed name`() {
        // Ends in '3' → CLOSE_MATCH
        val result = vopService.verify("FR7630006000011234567890183", "Les Restos du Coeur")

        assertEquals(VopResult.CLOSE_MATCH, result.result)
        assertEquals("Coeur du Restos Les", result.suggestedName)
    }

    @Test
    fun `demo mode - single word name reversed is unchanged`() {
        val result = vopService.verify("FR7630006000011234567890181", "Fondation")

        assertEquals(VopResult.CLOSE_MATCH, result.result)
        assertEquals("Fondation", result.suggestedName)
    }

    // ── NO_MATCH (7,9) ────────────────────────────────────────────────────────

    @Test
    fun `demo mode - IBAN ending in 7 returns NO_MATCH`() {
        val result = vopService.verify("FR7630006000011234567890187", "Test Organisation")

        assertEquals(VopResult.NO_MATCH, result.result)
        assertNull(result.suggestedName)
        assertNotNull(result.rawResponse)
        assertTrue(result.rawResponse!!.contains("NO_MATCH"))
    }

    @Test
    fun `demo mode - IBAN ending in 9 returns NO_MATCH`() {
        val result = vopService.verify("DE89370400440532013000".dropLast(1) + "9", "Test Organisation")

        assertEquals(VopResult.NO_MATCH, result.result)
        assertNull(result.suggestedName)
    }

    // ── NOT_POSSIBLE (letter or other) ────────────────────────────────────────

    @Test
    fun `demo mode - IBAN ending in letter returns NOT_POSSIBLE`() {
        // GB82WEST12345698765432 ends in '2' but if we construct one ending in a letter:
        // Use an IBAN that ends in a letter (e.g. BBAN ending in alpha char)
        val result = vopService.verify("GB82WEST1234569876543A", "Test Organisation")

        assertEquals(VopResult.NOT_POSSIBLE, result.result)
        assertNull(result.suggestedName)
        assertNotNull(result.rawResponse)
        assertTrue(result.rawResponse!!.contains("NOT_POSSIBLE"))
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
