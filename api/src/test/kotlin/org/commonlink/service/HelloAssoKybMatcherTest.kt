package org.commonlink.service

import org.commonlink.entity.AssociationProfile
import org.commonlink.repository.TestFixtures
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** RNA first, else normalized name (decision 2026-11-02). */
class HelloAssoKybMatcherTest {

    private fun association(name: String, identifier: String) =
        AssociationProfile(user = TestFixtures.associationUser(), name = name, identifier = identifier)

    @Test
    fun `same RNA matches, case and spaces ignored`() {
        assertTrue(HelloAssoKybMatcher.matches(association("Asso", "W751121684"), "Another name", " w751 121684 "))
    }

    @Test
    fun `a different RNA refuses even with the same name`() {
        assertFalse(HelloAssoKybMatcher.matches(association("Techno Plus", "W751121684"), "Techno Plus", "W000000001"))
    }

    @Test
    fun `association identified by SIREN falls back to the name`() {
        val siren = association("Les Restos du Cœur", "775671356")
        assertTrue(HelloAssoKybMatcher.matches(siren, "LES RESTOS DU COEUR", "W751121684"))
        assertFalse(HelloAssoKybMatcher.matches(siren, "Les Restos", "W751121684"))
    }

    @Test
    fun `no RNA on HelloAsso falls back to the name`() {
        assertTrue(HelloAssoKybMatcher.matches(association("Techno+ (T+)", "W751121684"), "techno t", null))
    }

    @Test
    fun `a blank organization name never matches`() {
        assertFalse(HelloAssoKybMatcher.matches(association("Asso", "775671356"), " ", null))
    }

    @Test
    fun `name normalization drops case, accents, punctuation and spaces`() {
        assertEquals("ecoleelementaire", HelloAssoKybMatcher.normalizeName(" École - Élémentaire ! "))
    }
}
