package org.commonlink.service

import org.commonlink.config.SanctionsProperties
import org.commonlink.entity.SanctionedNature
import org.commonlink.repository.SanctionedEntityRepository
import org.commonlink.util.FuzzyNameMatcher
import org.commonlink.util.NameNormalizer
import org.springframework.stereotype.Service

/**
 * Result of a name screening against the asset-freeze register.
 *
 * @property idRegistre    DG Trésor registry number of the matched entry.
 * @property nom           Human-readable name from the register.
 * @property nature        Nature of the matched entry.
 * @property score         Composite phonetic + orthographic similarity score (0–1). Higher = more similar.
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
 * **Matching strategy** — Block-level phonetic + orthographic scoring via [FuzzyNameMatcher]
 * against every stored normalized variant of each register entry (main name NOM PRENOM,
 * reversed PRENOM NOM, and all aliases in both orderings). The score is the maximum across
 * all variants. Names are split into blocks; particles ("DE", "AL", "VAN", …) are filtered;
 * DoubleMetaphone codes are compared with JaroWinkler, then combined with orthographic
 * JaroWinkler in a weighted geometric mean. Cyrillic/Arabic donor names are transliterated
 * before normalization via [FuzzyNameMatcher.transliterate].
 *
 * **Threshold** — Configured via `commonlink.sanctions.screening.score-threshold` (default 0.85).
 * A false negative (missing a listed person) constitutes a legal violation (art. L.562-2 CMF).
 * The composite scorer is generally more discriminating than raw JaroWinkler; threshold
 * recalibration based on empirical testing against the live register is recommended.
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
    /**
     * Screens a name against the asset-freeze register.
     *
     * @param name        Subject name (raw — any case, with or without accents; Cyrillic/Arabic supported).
     * @param dateOfBirth Optional date of birth of the subject. Not used to filter results;
     *                    the register's DOB is returned in each [ScreeningMatch] so the human
     *                    reviewer can compare both values. Filtering by DOB would risk false
     *                    negatives if the register carries an incorrect or partial date.
     * @param nature      If provided, restricts comparison to entries of this nature.
     *                    Pass null to compare against the full register (conservative default).
     * @return            Matches ordered by descending score. Empty if none exceed the threshold.
     */
    fun screen(name: String, dateOfBirth: String? = null, nature: SanctionedNature? = null): List<ScreeningMatch> {
        val transliterated  = FuzzyNameMatcher.containsCyrillicOrArabic(name)
        val normalizedQuery = NameNormalizer.normalize(FuzzyNameMatcher.transliterate(name))
        val candidates = if (nature != null) {
            repository.findByNature(nature)
        } else {
            repository.findAll()
        }

        return candidates.mapNotNull { entity ->
            val maxScore = entity.normalizedNames.maxOfOrNull { storedVariant ->
                FuzzyNameMatcher.score(normalizedQuery, storedVariant, entity.nature, transliterated)
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
