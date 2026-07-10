package org.commonlink.dto

import io.swagger.v3.oas.annotations.media.Schema
import java.time.Instant
import java.util.UUID

/**
 * Automated legal-existence pre-check result for an association.
 *
 * Informational only — never used to auto-approve or auto-reject a KYC dossier.
 * Null fields mean the source was unavailable or the check was not applicable
 * (e.g. no SIREN → INSEE and BODACC checks are skipped).
 *
 * Each result is a persisted, immutable scan row (see [org.commonlink.entity.AssociationRegistryCheck]).
 */
@Schema(description = "Automated registry pre-check result. Informational only — never auto-approves or rejects.")
data class RegistryPreCheckDto(

    @Schema(description = "Identifier of the persisted scan row.")
    val id: UUID,

    @Schema(description = "Whether the entity was found as an association in the Recherche d'entreprises registry.")
    val associationExists: Boolean?,

    @Schema(description = "SIREN (9 digits), if found.")
    val siren: String?,

    @Schema(description = "RNA number (W + 9 digits), if found.")
    val rna: String?,

    @Schema(description = "INSEE administrative status: 'A' = active, 'C' = ceased. Null if no SIREN or INSEE call failed.")
    val etatAdministratif: String?,

    @Schema(description = "Whether a creation or declaration entry was found in the JOAFE.")
    val joafeDeclarationFound: Boolean?,

    @Schema(description = "Whether a dissolution notice was found in the JOAFE.")
    val dissolutionDetected: Boolean?,

    @Schema(description = "Whether a collective insolvency procedure (procédure collective) was found in BODACC.")
    val bodaccProcedureFound: Boolean?,

    @Schema(description = "Timestamp when the check was performed.")
    val checkedAt: Instant,

    @Schema(description = "Non-fatal warnings from individual source failures (e.g. timeout, HTTP error).")
    val warnings: List<String>,
)
