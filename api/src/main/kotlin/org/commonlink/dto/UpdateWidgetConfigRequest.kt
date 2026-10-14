package org.commonlink.dto

import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Size

data class UpdateWidgetConfigRequest(
    /**
     * URL of the page where the association hosts its campaign (e.g. `https://www.asso-a.fr/don`),
     * kept whole: it is the post-payment return target, and its origin gates every redirect.
     * Null clears the setting. Mirrors the frontend check in `CampaignPageUrlCard` (rule 8).
     */
    @field:Size(max = 2048)
    @field:Pattern(regexp = "^(https?://.*)?$", message = "must start with http:// or https://")
    val widgetAllowedOrigin: String? = null,
)
