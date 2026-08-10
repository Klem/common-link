package org.commonlink.entity

/** The single accepted INSEE legal category for associations within platform scope. */
const val ACCEPTED_LEGAL_CATEGORY = "9220"

/**
 * Computed perimeter verdict derived from the INSEE legal category captured during a registry scan.
 *
 * Three states are required — conflating [OUT_OF_SCOPE] and [UNDETERMINED] would reject an
 * association because a public registry was unavailable.
 */
enum class ScopeVerdict {
    /** Legal category is [ACCEPTED_LEGAL_CATEGORY] — association is within platform scope. */
    IN_SCOPE,

    /** Legal category is known but not [ACCEPTED_LEGAL_CATEGORY] — outside platform scope. */
    OUT_OF_SCOPE,

    /** Legal category is unknown: source was unavailable or no SIREN to query. Approval is not blocked. */
    UNDETERMINED,
}
