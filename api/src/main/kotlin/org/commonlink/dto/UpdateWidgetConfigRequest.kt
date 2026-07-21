package org.commonlink.dto

import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Size

data class UpdateWidgetConfigRequest(
    /** Allowed origin for widget post-payment redirects (e.g. `https://www.asso-a.fr`). Null clears the setting. */
    @field:Size(max = 255)
    @field:Pattern(regexp = "^(https?://.*)?$", message = "must start with http:// or https://")
    val widgetAllowedOrigin: String? = null,
)
