package org.commonlink.service

import org.commonlink.dto.HelloAssoPayerJson
import org.commonlink.payment.CheckoutPayer
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** Each HelloAsso payer rule mirrored locally: what fails is not sent, never the whole pre-fill. */
class HelloAssoPayerValidatorTest {

    private fun payer(
        fullName: String? = "Jean Dupont",
        email: String? = "jean@example.org",
        country: String? = "FR",
    ) = CheckoutPayer(email, fullName, "1 rue de la Paix", "75001", "Paris", country)

    @Test
    fun `a valid identity is fully pre-filled, country in alpha-3`() {
        assertEquals(
            HelloAssoPayerJson("Jean", "Dupont", "jean@example.org", "1 rue de la Paix", "Paris", "75001", "FRA"),
            HelloAssoPayerValidator.build(payer()),
        )
    }

    @Test
    fun `compound last name and accents are kept`() {
        val built = HelloAssoPayerValidator.build(payer(fullName = "Élodie de La Fontaine-Saint-Ange"))!!
        assertEquals("Élodie", built.firstName)
        assertEquals("de La Fontaine-Saint-Ange", built.lastName)
    }

    @Test
    fun `names are not sent when one fails a rule - the rest still is`() {
        for (name in listOf("Jean", "Jean Dupont2", "Jean Duuupont", "Jean D", "Jean Brrr", "Test Dupont", "Jean Jean", "Иван Петров")) {
            val built = HelloAssoPayerValidator.build(payer(fullName = name))!!
            assertNull(built.firstName, name)
            assertNull(built.lastName, name)
            assertEquals("jean@example.org", built.email, name)
        }
    }

    @Test
    fun `an invalid email or unknown country is dropped`() {
        val built = HelloAssoPayerValidator.build(payer(email = "not-an-email", country = "ZZ"))!!
        assertNull(built.email)
        assertNull(built.country)
        assertEquals("Jean", built.firstName)
    }

    @Test
    fun `alpha-2 to alpha-3`() {
        assertEquals("BEL", HelloAssoPayerValidator.alpha3("be"))
        assertEquals("CHE", HelloAssoPayerValidator.alpha3("CH"))
        assertNull(HelloAssoPayerValidator.alpha3("FRA"))
        assertNull(HelloAssoPayerValidator.alpha3(null))
    }

    @Test
    fun `nothing valid means no payer at all`() {
        assertNull(HelloAssoPayerValidator.build(CheckoutPayer(null, "X", null, null, null, null)))
        assertNull(HelloAssoPayerValidator.build(null))
    }

    @Test
    fun `name rules`() {
        assertTrue(HelloAssoPayerValidator.validName("O'Neil"))
        assertFalse(HelloAssoPayerValidator.validName("Ab3"))
        assertFalse(HelloAssoPayerValidator.validName("admin"))
    }
}
