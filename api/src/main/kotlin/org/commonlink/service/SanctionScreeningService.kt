package org.commonlink.service

import org.apache.commons.text.similarity.JaroWinklerSimilarity
import org.commonlink.config.SanctionsProperties
import org.commonlink.entity.SanctionedNature
import org.commonlink.repository.SanctionedEntityRepository
import org.commonlink.util.NameNormalizer
import org.springframework.stereotype.Service

/**
 * Result of a name screening against the asset-freeze register.
 *
 * @property idRegistre    DG Trésor registry number of the matched entry.
 * @property nom           Human-readable name from the register.
 * @property nature        Nature of the matched entry.
 * @property score         JaroWinkler similarity score (0–1). Higher = more similar.
 * @property dateOfBirth   Partial date of birth, if available in the register.
 * @property legalReference EU or UN measure reference of the matched entry, if any. Carried
 *   because it is the decisive element of a false-positive ruling: it names the sanctions
 *   programme the entry falls under, which a score alone never discriminates.
 */
data class ScreeningMatch(
    val idRegistre: Int,
    val nom: String,
    val nature: SanctionedNature,
    val score: Double,
    val dateOfBirth: String?,
    val legalReference: String? = null,
)

/**
 * Internal API for screening a name against the national asset-freeze register.
 *
 * **Source** — The register is published by the Direction générale du Trésor and
 * consolidates French, EU and UN measures applicable in France (decision D2).
 *
 * **Matching strategy** — JaroWinkler similarity between the normalized query name and
 * every stored normalized variant of each register entry (main name NOM PRENOM, reversed
 * PRENOM NOM, and all aliases in both orderings). The score is the maximum across all
 * variants. Normalization is performed by [NameNormalizer], which is the single
 * normalization point used both at ingestion and here — any divergence would produce
 * undetected false negatives.
 *
 * **Threshold** — The default of 0.85 is a conservative choice: a false positive
 * (flagging a non-listed person) costs a human review, while a false negative (missing a
 * listed person) constitutes a legal violation (art. L.562-2 CMF). The threshold
 * tolerates typical transcription errors: accents, inverted name order, missing particles,
 * multiple spaces, and single-character substitutions. Human review absorbs the false
 * positives. Configured via `commonlink.sanctions.screening.score-threshold`.
 *
 * **Log hygiene** — this service never logs queried names or matched entries. Per-screening
 * traces belong in the compliance audit log (prompt 12), not in application logs.
 *
 * **This service reports potential matches only. It does not block, decide, or act.**
 */
@Service
class SanctionScreeningService(
    private val repository: SanctionedEntityRepository,
    private val props: SanctionsProperties,
) {
    private val similarity = JaroWinklerSimilarity()

    /**
     * Screens a name against the asset-freeze register.
     *
     * @param name        Subject name (raw — any case, with or without accents).
     * @param dateOfBirth Optional date of birth of the subject. Not used to filter results;
     *                    the register's DOB is returned in each [ScreeningMatch] so the human
     *                    reviewer can compare both values. Filtering by DOB would risk false
     *                    negatives if the register carries an incorrect or partial date.
     * @param nature      If provided, restricts comparison to entries of this nature.
     *                    Pass null to compare against the full register (conservative default).
     * @return            Matches ordered by descending score. Empty if none exceed the threshold.
     */
    fun screen(name: String, dateOfBirth: String? = null, nature: SanctionedNature? = null): List<ScreeningMatch> {
        val normalizedQuery = NameNormalizer.normalize(name)
        val candidates = if (nature != null) {
            repository.findByNature(nature)
        } else {
            repository.findAll()
        }

        return candidates.mapNotNull { entity ->
            val maxScore = entity.normalizedNames.maxOfOrNull { storedVariant ->
                similarity.apply(normalizedQuery, storedVariant) ?: 0.0
            } ?: 0.0

            if (maxScore >= props.scoreThreshold) {
                ScreeningMatch(
                    idRegistre = entity.idRegistre,
                    nom = entity.nom,
                    nature = entity.nature,
                    score = maxScore,
                    dateOfBirth = entity.dateOfBirth,
                    legalReference = entity.legalReference,
                )
            } else null
        }.sortedByDescending { it.score }
    }
}
