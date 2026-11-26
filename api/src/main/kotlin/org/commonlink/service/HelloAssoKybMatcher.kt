package org.commonlink.service

import org.commonlink.entity.HelloAssoKybOutcome
import java.text.Normalizer

/**
 * Decides whether a HelloAsso organization is the association verified on CommonLink (decision 2026-11-02).
 *
 * **RNA first** — when HelloAsso returns a RNA and the association's RNA is known, they must be equal (case and spaces
 * ignored); a different RNA refuses the match even if the names agree. **Otherwise the name** — no RNA on one side:
 * names equal once case, accents, punctuation and spaces are ignored.
 *
 * HelloAsso's `rnaNumber` comes from the documents the organization supplied to HelloAsso; HelloAsso's API exposes no
 * SIREN at all, so a SIREN cannot be compared.
 */
object HelloAssoKybMatcher {

    /**
     * @param associationRna the association's RNA as known by CommonLink (identifier or registry scan), or null.
     * @param associationName the association's name on CommonLink.
     * @param organizationName the HelloAsso organization's `name`.
     * @param organizationRna the HelloAsso organization's `rnaNumber` (may be blank).
     */
    fun evaluate(
        associationRna: String?,
        associationName: String?,
        organizationName: String?,
        organizationRna: String?,
    ): HelloAssoKybOutcome {
        val ownRna = normalizeRna(associationRna)?.takeIf { RechercheEntreprisesResults.RNA_PATTERN.matches(it) }
        val helloAssoRna = normalizeRna(organizationRna)
        if (ownRna != null && helloAssoRna != null) {
            return if (ownRna == helloAssoRna) HelloAssoKybOutcome.MATCH_RNA else HelloAssoKybOutcome.RNA_MISMATCH
        }
        val name = normalizeName(organizationName)
        return if (name.isNotEmpty() && name == normalizeName(associationName)) {
            HelloAssoKybOutcome.MATCH_NAME
        } else {
            HelloAssoKybOutcome.NAME_MISMATCH
        }
    }

    /** Spaces removed, upper-cased; null when blank. */
    internal fun normalizeRna(value: String?): String? =
        value?.replace(Regex("\\s"), "")?.uppercase()?.takeIf { it.isNotEmpty() }

    /**
     * Lowercase, accents removed, ligatures expanded (œ → oe, æ → ae: NFD does not decompose them, and French names
     * use them — "Cœur" must match "COEUR"), every character that is not a letter or a digit dropped.
     */
    internal fun normalizeName(value: String?): String =
        Normalizer.normalize(value.orEmpty().lowercase(), Normalizer.Form.NFD)
            .replace(Regex("\\p{M}"), "")
            .replace("œ", "oe")
            .replace("æ", "ae")
            .replace(Regex("[^a-z0-9]"), "")
}
