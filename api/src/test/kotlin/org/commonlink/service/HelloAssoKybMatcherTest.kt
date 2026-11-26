package org.commonlink.service

import org.commonlink.entity.HelloAssoKybOutcome
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

/** RNA first, else normalized name (decision 2026-11-02). */
class HelloAssoKybMatcherTest {

    @Test
    fun `same RNA matches, case and spaces ignored`() {
        assertEquals(
            HelloAssoKybOutcome.MATCH_RNA,
            HelloAssoKybMatcher.evaluate("W751121684", "Asso", "Another name", " w751 121684 "),
        )
    }

    @Test
    fun `a different RNA refuses even with the same name`() {
        assertEquals(
            HelloAssoKybOutcome.RNA_MISMATCH,
            HelloAssoKybMatcher.evaluate("W751121684", "Techno Plus", "Techno Plus", "W000000001"),
        )
    }

    @Test
    fun `association RNA unknown falls back to the name`() {
        assertEquals(
            HelloAssoKybOutcome.MATCH_NAME,
            HelloAssoKybMatcher.evaluate(null, "Les Restos du Cœur", "LES RESTOS DU COEUR", "W751121684"),
        )
        assertEquals(
            HelloAssoKybOutcome.NAME_MISMATCH,
            HelloAssoKybMatcher.evaluate(null, "Les Restos du Cœur", "Les Restos", "W751121684"),
        )
    }

    @Test
    fun `a SIREN passed as association RNA is ignored`() {
        assertEquals(
            HelloAssoKybOutcome.NAME_MISMATCH,
            HelloAssoKybMatcher.evaluate("775671356", "Asso", "Autre", "775671356"),
        )
    }

    @Test
    fun `no RNA on HelloAsso falls back to the name`() {
        assertEquals(
            HelloAssoKybOutcome.MATCH_NAME,
            HelloAssoKybMatcher.evaluate("W751121684", "Techno+ (T+)", "techno t", null),
        )
    }

    @Test
    fun `blank HelloAsso RNA counts as no RNA - FC Mougins case`() {
        assertEquals(
            HelloAssoKybOutcome.NAME_MISMATCH,
            HelloAssoKybMatcher.evaluate("W061006975", "FOOTBALL CLUB DE MOUGINS", "FC Mougins", ""),
        )
    }

    @Test
    fun `a blank organization name never matches`() {
        assertEquals(HelloAssoKybOutcome.NAME_MISMATCH, HelloAssoKybMatcher.evaluate(null, "Asso", " ", null))
    }

    @Test
    fun `name normalization drops case, accents, punctuation and spaces`() {
        assertEquals("ecoleelementaire", HelloAssoKybMatcher.normalizeName(" École - Élémentaire ! "))
    }

    @Test
    fun `RNA normalization upper-cases, drops spaces, blank is null`() {
        assertEquals("W061006975", HelloAssoKybMatcher.normalizeRna(" w061 006975 "))
        assertNull(HelloAssoKybMatcher.normalizeRna("  "))
    }
}
