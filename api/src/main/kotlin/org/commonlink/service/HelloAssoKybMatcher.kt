package org.commonlink.service

import org.commonlink.entity.AssociationProfile
import java.text.Normalizer

/**
 * Decides whether a HelloAsso organization is the association verified on CommonLink (decision 2026-11-02).
 *
 * **RNA first** — when HelloAsso returns an RNA and the association is identified by an RNA, they must be equal (case
 * and spaces ignored); a different RNA refuses the match even if the names agree. **Otherwise the name** — association
 * identified by SIREN only, or no RNA on HelloAsso: names equal once case, accents, punctuation and spaces are ignored.
 */
object HelloAssoKybMatcher {

    private val RNA = Regex("^W\\d{9}$")

    /** @return whether [organizationName] / [organizationRna] designate [association]. */
    fun matches(association: AssociationProfile, organizationName: String?, organizationRna: String?): Boolean {
        val associationRna = normalizeRna(association.identifier)?.takeIf { RNA.matches(it) }
        val helloAssoRna = normalizeRna(organizationRna)
        if (associationRna != null && helloAssoRna != null) return associationRna == helloAssoRna
        val name = normalizeName(organizationName)
        return name.isNotEmpty() && name == normalizeName(association.name)
    }

    private fun normalizeRna(value: String?): String? =
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
