package org.commonlink.security

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class TokenHashServiceTest {

    private val tokenHashService = TokenHashService()

    @Test
    fun `generateOpaqueToken produces 64-char hex string`() {
        val token = tokenHashService.generateOpaqueToken()
        assertEquals(64, token.length)
        assertTrue(token.matches(Regex("[0-9a-f]+")), "Token must be lowercase hex")
    }

    @Test
    fun `generateOpaqueToken produces unique tokens over 1000 calls`() {
        val tokens = (1..1000).map { tokenHashService.generateOpaqueToken() }.toSet()
        assertEquals(1000, tokens.size, "All 1000 tokens must be unique")
    }

    @Test
    fun `hashToken is deterministic`() {
        val raw = "someRawToken"
        val hash1 = tokenHashService.hashToken(raw)
        val hash2 = tokenHashService.hashToken(raw)
        assertEquals(hash1, hash2)
    }

    @Test
    fun `hashToken result differs from raw token`() {
        val raw = "someRawToken"
        assertNotEquals(raw, tokenHashService.hashToken(raw))
    }

    @Test
    fun `hashToken produces 64-char hex string`() {
        val hash = tokenHashService.hashToken("anyInput")
        assertEquals(64, hash.length)
        assertTrue(hash.matches(Regex("[0-9a-f]+")))
    }

    @Test
    fun `verifyToken returns true for matching token and hash`() {
        val raw = tokenHashService.generateOpaqueToken()
        val hash = tokenHashService.hashToken(raw)
        assertTrue(tokenHashService.verifyToken(raw, hash))
    }

    @Test
    fun `verifyToken returns false for non-matching token`() {
        val raw = tokenHashService.generateOpaqueToken()
        val hash = tokenHashService.hashToken(raw)
        val otherToken = tokenHashService.generateOpaqueToken()
        assertFalse(tokenHashService.verifyToken(otherToken, hash))
    }
}
