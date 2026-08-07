package org.commonlink.config

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * Typed binding of `compliance/risk-classification.yml`.
 *
 * The file is the authoritative, versioned transcription of CommonLink's risk-classification
 * policy (art. L.561-5, L.561-9, L.561-10 CMF and ACPR guidelines). Every assessment stamps
 * [version] in the database so the applicable rules remain retrievable after any revision.
 *
 * There is deliberately no administration endpoint to modify this mapping at runtime —
 * architectural decision D6. Any change must pass code review and leaves an immutable trace
 * in git history.
 */
@ConfigurationProperties(prefix = "risk-classification")
data class RiskClassificationProperties(
    val version: String = "",
    val approvedOn: String = "",
    /** Keyed by [org.commonlink.entity.RiskLevel] name (LOW, STANDARD, HIGH). */
    val measures: Map<String, VigilanceMeasures> = emptyMap(),
) {
    data class VigilanceMeasures(
        val description: String,
        val reviewFrequency: String,
        val requiredDocuments: List<String> = emptyList(),
    )
}
