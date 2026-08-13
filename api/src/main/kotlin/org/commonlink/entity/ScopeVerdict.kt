package org.commonlink.entity

/**
 * INSEE legal categories (level III) that place an entity within platform scope.
 *
 * Level II code `92` is "Association loi 1901 ou assimilé". Every declared-association form of that
 * family is accepted — the verdict qualifies the **legal form**, not the mission, which stays the
 * curator's manual assessment:
 *
 * - `9220` Association déclarée
 * - `9221` Association déclarée « entreprises d'insertion par l'économique »
 * - `9222` Association intermédiaire
 * - `9223` Groupement d'employeurs
 * - `9230` Association déclarée reconnue d'utilité publique
 * - `9260` Association de droit local *(Alsace-Moselle, régime de 1908 — équivalent au régime déclaré)*
 *
 * Deliberately excluded from the same family: `9210` association non déclarée (no legal personality,
 * cannot hold funds and has no RNA registration) and `9240` congrégation (cultual regime of its own).
 * `9300` fondation is a different family altogether.
 */
val ACCEPTED_LEGAL_CATEGORIES: Set<String> = setOf("9220", "9221", "9222", "9223", "9230", "9260")

/**
 * Computed perimeter verdict derived from the INSEE legal category captured during a registry scan.
 *
 * Three states are required — conflating [OUT_OF_SCOPE] and [UNDETERMINED] would reject an
 * association because a public registry was unavailable.
 */
enum class ScopeVerdict {
    /** Legal category belongs to [ACCEPTED_LEGAL_CATEGORIES] — association is within platform scope. */
    IN_SCOPE,

    /** Legal category is known but outside [ACCEPTED_LEGAL_CATEGORIES] — outside platform scope. */
    OUT_OF_SCOPE,

    /** Legal category is unknown: source was unavailable or no SIREN to query. Approval is not blocked. */
    UNDETERMINED,
}
