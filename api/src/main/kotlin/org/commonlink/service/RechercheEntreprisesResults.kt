package org.commonlink.service

import com.fasterxml.jackson.databind.JsonNode

/**
 * Reads the `results[]` entries of the Recherche d'entreprises search API (`GET /search?q=…`).
 *
 * Single parser for every backend consumer ([AssociationRegistryCheckService], [RnaBySirenResolver]), so the field
 * paths cannot drift apart again: the RNA lives under `complements.identifiant_association`, not at the entry root —
 * reading the root left the curator scan without any RNA or SIREN resolution (fixed 2026-11-26). The root is still
 * read as a fallback, as the sign-up RNA search does.
 */
object RechercheEntreprisesResults {

    /** A RNA: `W` then 9 alphanumerics (Corsican départements use `W2A`/`W2B`). */
    val RNA_PATTERN = Regex("^W[0-9A-Za-z]{9}$")

    /** The `results[]` entries of a search response body, empty when absent. */
    fun entries(body: JsonNode): List<JsonNode> {
        val results = body.path("results")
        return if (results.isArray) (0 until results.size()).map { results[it] } else emptyList()
    }

    /** The entry whose own SIREN is exactly [siren]: the search is full-text and may rank another entity first. */
    fun findBySiren(entries: List<JsonNode>, siren: String): JsonNode? =
        entries.firstOrNull { sirenOf(it) == siren }

    /** The entry whose own RNA is exactly [rna] (case ignored). */
    fun findByRna(entries: List<JsonNode>, rna: String): JsonNode? =
        entries.firstOrNull { rnaOf(it)?.equals(rna, ignoreCase = true) == true }

    /** The entry's SIREN, null when blank. */
    fun sirenOf(entry: JsonNode): String? = entry.path("siren").asText("").trim().takeIf { it.isNotEmpty() }

    /** The RNA the registry associates with the entry, null when blank. Not checked against [RNA_PATTERN]. */
    fun rnaOf(entry: JsonNode): String? =
        (entry.path("complements").path("identifiant_association").asText("").trim().takeIf { it.isNotEmpty() }
            ?: entry.path("identifiant_association").asText("").trim().takeIf { it.isNotEmpty() })
}
