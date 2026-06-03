package org.commonlink.dto.monerium

import com.fasterxml.jackson.annotation.JsonProperty

/**
 * Subset of the Monerium `/profiles` response we care about.
 *
 * `kind` is "personal" or "corporate"; for CommonLink associations we always expect
 * "corporate". `state` reflects the KYB lifecycle (created / pending / approved / rejected).
 */
data class MoneriumProfileDto(
    val id: String,
    val kind: String,
    val name: String?,
    val state: String?,
)

/** Envelope shape: Monerium v2 wraps the list as `{ "profiles": [...] }`. */
data class MoneriumProfileListDto(
    @field:JsonProperty("profiles") val profiles: List<MoneriumProfileDto> = emptyList(),
)
