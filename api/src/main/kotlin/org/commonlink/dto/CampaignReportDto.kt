package org.commonlink.dto

import jakarta.validation.constraints.Email
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import java.time.Instant

/**
 * Body for POST /api/public/widget/{widgetToken}/report (IC-44 — canal de signalement de campagne).
 *
 * [reporterEmail] is optional and purely informative for the compliance officer — reporting is
 * not conditioned on providing one, and no automated reply is sent to it (decision recorded
 * 2026-08-26: no obligation to respond to the reporter in this lot).
 */
data class CampaignReportRequest(
    @field:NotBlank(message = "must not be blank")
    @field:Size(max = 4000, message = "must not exceed 4000 characters")
    val message: String,

    @field:Email(message = "must be a valid email address")
    @field:Size(max = 255, message = "must not exceed 255 characters")
    val reporterEmail: String? = null,
)

/** One report entry read back from the compliance journal, for the alert detail screen. */
data class CampaignReportEntryDto(
    val campaignId: String?,
    val message: String,
    val reporterEmail: String?,
    val occurredAt: Instant,
)

/** Body for POST /api/compliance/associations/{associationId}/reactivate. */
data class ReactivateAssociationRequest(
    @field:NotBlank(message = "must not be blank")
    val rationale: String,
)
