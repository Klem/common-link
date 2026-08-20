package org.commonlink.dto

import io.swagger.v3.oas.annotations.media.Schema
import org.commonlink.entity.ScopeVerdict
import java.time.Instant
import java.util.UUID

/**
 * Latest registry scan summary for one association, enriched with the association name.
 * Built by [org.commonlink.controller.ComplianceController] for the compliance dashboard.
 *
 * Does NOT extend or modify [RegistryPreCheckDto] — keeps the shared DTO untouched.
 */
@Schema(description = "Latest registry scan summary for one association, enriched with the association name.")
data class ComplianceRegistryScanSummaryDto(

    @Schema(description = "Association UUID.")
    val associationId: UUID,

    @Schema(description = "Official registered name of the association.")
    val associationName: String,

    @Schema(description = "Whether the entity was found as an association in the registries. Null if the check failed.")
    val associationExists: Boolean?,

    @Schema(description = "Whether the association is active in the RNA registry. Null if source unavailable.")
    val rnaActive: Boolean?,

    @Schema(description = "Perimeter verdict derived from the legal category.")
    val scopeVerdict: ScopeVerdict,

    @Schema(description = "Number of non-fatal warnings raised during the scan.")
    val warningCount: Int,

    @Schema(description = "Timestamp when the scan was performed.")
    val checkedAt: Instant,

    @Schema(description = "SIREN number, if resolved.")
    val siren: String?,

    @Schema(description = "RNA number, if resolved.")
    val rna: String?,
)
