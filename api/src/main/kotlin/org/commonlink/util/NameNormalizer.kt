package org.commonlink.util

import java.text.Normalizer

/**
 * Single normalization point for name comparison in the asset-freeze screening service.
 *
 * This function MUST be called identically at ingestion (to produce the stored variants in
 * `sanctioned_entity.normalized_names`) and at query time in [SanctionScreeningService].
 * Any divergence between storage and query normalization — even a difference in whitespace
 * handling — is the classic failure mode of sanctions-screening services and would produce
 * undetected false negatives.
 *
 * Normalization steps (order matters):
 *  1. NFD decomposition — separates base letters from their diacritics
 *  2. Strip non-spacing marks (diacritics) — accents, cedillas, etc.
 *  3. Uppercase — case-insensitive comparison
 *  4. Replace any non-alphanumeric character with a space — handles hyphens, apostrophes,
 *     punctuation, and any transcription separator
 *  5. Collapse consecutive spaces and trim — eliminates alignment artifacts
 */
object NameNormalizer {

    fun normalize(name: String): String =
        Normalizer.normalize(name, Normalizer.Form.NFD)
            .replace(Regex("\\p{Mn}"), "")
            .uppercase()
            .replace(Regex("[^A-Z0-9 ]"), " ")
            .replace(Regex(" +"), " ")
            .trim()
}
