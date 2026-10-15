package org.commonlink.dto

import jakarta.validation.constraints.Pattern
import org.commonlink.entity.EmbedType
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

    /**
     * Google Tag Manager container ID (e.g. `GTM-XXXXXXX`). Null/absent leaves the current value
     * unchanged; an empty string clears it (no GTM injection anywhere). This field is interpolated
     * into an inline `<script>` and an `iframe src` on our own origin — the pattern is a stored-XSS
     * guard, not a UX nicety.
     */
    @field:Pattern(regexp = "^(GTM-[A-Z0-9]{4,10})?$", message = "must be empty or match GTM-XXXXXXX")
    val gtmContainerId: String? = null,

    /**
     * Diffusion method picked in the settings tab, as its lowercase wire value (`direct`, `script`,
     * `iframe`, `html`, `widget`). Null leaves the current value unchanged; an unknown value is
     * rejected at deserialization.
     */
    val embedType: EmbedType? = null,
)
