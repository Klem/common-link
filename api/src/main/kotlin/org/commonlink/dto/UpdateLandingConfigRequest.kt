package org.commonlink.dto

import org.commonlink.entity.LandingTheme

/**
 * Partial update of an association's landing page configuration.
 *
 * Every field is optional: a null field leaves the stored value untouched, so the settings UI can
 * send one field per interaction. The theme is typed as [LandingTheme] rather than a free String —
 * an unknown value is rejected at deserialization, which is the only enforcement the integration
 * tests can see (the DB CHECK constraint lives in Flyway, which is disabled in the test schema).
 */
data class UpdateLandingConfigRequest(
    /** Visual palette of the landing page. Null leaves the current theme unchanged. */
    val theme: LandingTheme? = null,

    /** Show the "what this donation funds" section. Null leaves the current value unchanged. */
    val showProject: Boolean? = null,

    /** Show the budget / milestones transparency section. Null leaves the current value unchanged. */
    val showTransparency: Boolean? = null,

    /** Show the "donate with confidence" section. Null leaves the current value unchanged. */
    val showTrust: Boolean? = null,
)
