package org.commonlink.service

import org.commonlink.dto.HelloAssoPayerJson
import org.commonlink.payment.CheckoutPayer
import java.util.Locale
import java.util.MissingResourceException

/**
 * Local mirror of HelloAsso's `payer` rules (flow §4, dev.helloasso.com « Fonctionnement technique »): HelloAsso
 * refuses the **whole** checkout when one pre-filled field is invalid, so only fields that pass are sent and the donor
 * types the rest on HelloAsso's page. **Never a birth date.**
 *
 * Names: the full name is split best-effort (first word = first name, the rest = last name); either both names pass —
 * Latin letters only, at least two characters, a vowel, no digit, no character three times in a row, not a placeholder
 * value, first ≠ last — or neither is sent.
 */
object HelloAssoPayerValidator {

    private const val MAX_LENGTH = 255
    private val LATIN_NAME = Regex("^[\\p{IsLatin}][\\p{IsLatin}' -]*[\\p{IsLatin}]$")
    private val VOWEL = Regex("[aeiouyàâäéèêëîïôöùûüÿæœ]", RegexOption.IGNORE_CASE)
    private val TRIPLE = Regex("(.)\\1\\1", RegexOption.IGNORE_CASE)
    private val EMAIL = Regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")
    private val PLACEHOLDERS = setOf("test", "anonyme", "anonymous", "unknown", "inconnu", "nom", "prenom", "prénom", "admin", "name")

    /** The pre-fill HelloAsso will accept, or null when nothing passes. */
    fun build(payer: CheckoutPayer?): HelloAssoPayerJson? {
        if (payer == null) return null
        val (firstName, lastName) = names(payer.fullName)
        val result = HelloAssoPayerJson(
            firstName = firstName,
            lastName = lastName,
            email = payer.email?.trim()?.takeIf { it.length <= MAX_LENGTH && EMAIL.matches(it) },
            address = text(payer.addressLine1),
            city = text(payer.city),
            zipCode = text(payer.postalCode),
            country = alpha3(payer.country),
        )
        return result.takeUnless { it == HelloAssoPayerJson() }
    }

    internal fun names(fullName: String?): Pair<String?, String?> {
        val words = fullName?.trim()?.split(Regex("\\s+"))?.filter { it.isNotEmpty() }.orEmpty()
        if (words.size < 2) return null to null
        val first = words.first()
        val last = words.drop(1).joinToString(" ")
        if (!validName(first) || !validName(last) || first.equals(last, ignoreCase = true)) return null to null
        return first to last
    }

    internal fun validName(name: String): Boolean =
        name.length in 2..MAX_LENGTH &&
            LATIN_NAME.matches(name) &&
            VOWEL.containsMatchIn(name) &&
            !TRIPLE.containsMatchIn(name) &&
            name.lowercase() !in PLACEHOLDERS

    /** ISO 3166-1 alpha-2 → alpha-3 (HelloAsso wants exactly three letters); unknown codes are not sent. */
    internal fun alpha3(alpha2: String?): String? {
        val code = alpha2?.trim()?.uppercase()?.takeIf { it.length == 2 && it.all(Char::isLetter) } ?: return null
        if (code !in Locale.getISOCountries()) return null
        return try {
            Locale.of("", code).isO3Country.takeIf { it.length == 3 }
        } catch (ex: MissingResourceException) {
            null
        }
    }

    private fun text(value: String?): String? = value?.trim()?.takeIf { it.isNotEmpty() && it.length <= MAX_LENGTH }
}
