package org.commonlink.dto

import java.time.Instant

/**
 * Short-lived credential letting an association preview its own landing page before the campaign is LIVE.
 *
 * @param previewToken Value to pass as the `preview` query parameter of the public landing endpoint.
 * @param expiresAt Absolute expiry — the caller re-issues rather than caching it.
 */
data class LandingPreviewTokenDto(
    val previewToken: String,
    val expiresAt: Instant,
)
