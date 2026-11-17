package org.commonlink.dto

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import java.util.UUID

/**
 * Body of `POST /api/association/helloasso/credentials/verify`: the association's HelloAsso API keys.
 *
 * The secret is used for a single token call and never kept; [toString] never prints it.
 *
 * @property clientId HelloAsso API `client_id` (not a secret).
 * @property clientSecret HelloAsso API `client_secret` — never stored, logged nor echoed.
 */
data class HelloAssoVerifyRequest(
    @field:NotBlank @field:Size(max = 255) val clientId: String,
    @field:NotBlank @field:Size(max = 255) val clientSecret: String,
) {
    override fun toString(): String = "HelloAssoVerifyRequest(clientId=$clientId, clientSecret=***)"
}

/**
 * Result of the key verification. Nothing is persisted at this stage.
 *
 * @property verifyId one-time handle of the verified link, valid 10 minutes, to confirm it with the mandate; null when
 *   the organization does not match the association (nothing is kept then).
 * @property orgName HelloAsso organization name.
 * @property slug HelloAsso organization slug.
 * @property matchesKyb whether the organization is the association verified on CommonLink (RNA, else name).
 * @property canCollect HelloAsso `isCashInCompliant`: whether the organization can collect right now.
 */
data class HelloAssoVerifyResponse(
    val verifyId: UUID?,
    val orgName: String,
    val slug: String,
    val matchesKyb: Boolean,
    val canCollect: Boolean,
)

/**
 * Body of `POST /api/association/helloasso/connection`.
 *
 * @property verifyId handle returned by the key verification (valid 10 minutes, single use).
 * @property mandate the association's acceptance of the current mandate wording.
 */
data class HelloAssoConnectRequest(
    val verifyId: UUID,
    @field:jakarta.validation.Valid val mandate: HelloAssoMandateDto,
)

/**
 * The association's mandate to CommonLink over its HelloAsso access.
 *
 * @property accepted the checkbox was ticked.
 * @property version wording version shown to the user; must equal the current one.
 */
data class HelloAssoMandateDto(
    val accepted: Boolean,
    @field:NotBlank @field:Size(max = 64) val version: String,
)

/**
 * The association's HelloAsso notification URL, to paste in its HelloAsso back office.
 *
 * @property notificationUrl `{base}/api/public/webhooks/helloasso/{token}` — the token never changes.
 */
data class HelloAssoNotificationUrlResponse(val notificationUrl: String)

/** One organization of `GET /v5/users/me/organizations` (HelloAsso wire format). */
@JsonIgnoreProperties(ignoreUnknown = true)
data class HelloAssoOrganizationLightJson(
    @field:JsonProperty("organizationSlug") val organizationSlug: String? = null,
    @field:JsonProperty("name") val name: String? = null,
)

/** `GET /v5/organizations/{slug}` (HelloAsso wire format), fields CommonLink reads. */
@JsonIgnoreProperties(ignoreUnknown = true)
data class HelloAssoOrganizationJson(
    @field:JsonProperty("organizationSlug") val organizationSlug: String? = null,
    @field:JsonProperty("name") val name: String? = null,
    @field:JsonProperty("rnaNumber") val rnaNumber: String? = null,
    /** Returned to an organization admin only (an association key has that role). */
    @field:JsonProperty("isCashInCompliant") val cashInCompliant: Boolean? = null,
)
