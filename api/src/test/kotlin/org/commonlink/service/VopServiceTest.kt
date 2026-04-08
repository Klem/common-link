package org.commonlink.service

import com.fasterxml.jackson.databind.ObjectMapper
import org.commonlink.entity.VopResult
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

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
}
