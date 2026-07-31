package org.commonlink.entity

/**
 * KYC onboarding status of a Mollie merchant.
 *
 * Mollie reports these as kebab-case strings on the wire; [fromMollie] converts them.
 * Stored in DB (and sent in DTOs) using the Kotlin name (e.g. "NEEDS_DATA"), which matches
 * the CHECK constraint in V45 exactly — no AttributeConverter needed.
 */
enum class MollieOnboardingStatus(val mollieValue: String) {
    /** Mollie requires additional data before review can begin. */
    NEEDS_DATA("needs-data"),
    /** Mollie is reviewing the submitted information. */
    IN_REVIEW("in-review"),
    /** KYC is complete; the merchant can receive payments and settlements. */
    COMPLETED("completed");

    companion object {
        fun fromMollie(value: String): MollieOnboardingStatus =
            entries.firstOrNull { it.mollieValue == value }
                ?: throw IllegalArgumentException("Unknown Mollie onboarding status: $value")
    }
}
