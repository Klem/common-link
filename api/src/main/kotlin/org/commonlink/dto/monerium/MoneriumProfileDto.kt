package org.commonlink.dto.monerium

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import org.commonlink.entity.MoneriumProfileKind

/**
 * Subset of the Monerium `/profiles` response we care about.
 *
 * `kind` is nullable to absorb future Monerium-side additions (a new kind we don't yet
 * recognise would deserialize to null rather than fail the whole response). For CommonLink
 * associations we always expect [MoneriumProfileKind.CORPORATE]. `state` reflects the KYB
 * lifecycle (created / pending / approved / rejected) — left as String because Monerium has
 * many states we don't need to enumerate here.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
data class MoneriumProfileDto(
    val id: String,
    val kind: MoneriumProfileKind?,
    val name: String?,
    val state: String? = null,
)

/** Envelope shape: Monerium v2 wraps the list as `{ "profiles": [...] }`. */
@JsonIgnoreProperties(ignoreUnknown = true)
data class MoneriumProfileListDto(
    @field:JsonProperty("profiles") val profiles: List<MoneriumProfileDto> = emptyList(),
)

/**
 * Subset of the Monerium `GET /auth/context` response.
 *
 * Unlike `GET /profiles` (which needs the `profiles` scope), `/auth/context` is reachable
 * with just `openid` — it returns the authenticated user's accessible profiles and the
 * default one. Used during OAuth callback to pin a profile id without asking for extra
 * scopes during the consent screen.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
data class MoneriumAuthContextDto(
    @field:JsonProperty("userId") val userId: String? = null,
    @field:JsonProperty("email") val email: String? = null,
    @field:JsonProperty("name") val name: String? = null,
    @field:JsonProperty("roles") val roles: List<String> = emptyList(),
    @field:JsonProperty("defaultProfile") val defaultProfile: String? = null,
    @field:JsonProperty("profiles") val profiles: List<MoneriumProfileDto> = emptyList(),
    @field:JsonProperty("auth") val auth: MoneriumAuthInfoDto? = null,
)

/**
 * Inner auth info from /auth/context
 */
@JsonIgnoreProperties(ignoreUnknown = true)
data class MoneriumAuthInfoDto(
    @field:JsonProperty("method") val method: String? = null,
    @field:JsonProperty("subject") val subject: String? = null,
    @field:JsonProperty("verified") val verified: Boolean? = null,
)
