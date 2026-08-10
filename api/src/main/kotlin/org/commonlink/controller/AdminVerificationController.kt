package org.commonlink.controller

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.commonlink.dto.AdminVerificationDetailDto
import org.commonlink.dto.AdminVerificationSummaryDto
import org.commonlink.dto.RegistryPreCheckDto
import org.commonlink.dto.RejectVerificationRequest
import org.commonlink.entity.VerificationStatus
import org.commonlink.service.AssociationRegistryCheckService
import org.commonlink.dto.PageResponse
import org.commonlink.dto.toPageResponse
import org.commonlink.service.VerificationService
import org.springframework.data.domain.Pageable
import org.springframework.data.web.PageableDefault
import org.springframework.http.ContentDisposition
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.core.userdetails.UserDetails
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/api/admin/verifications")
@Tag(name = "Admin · Verification", description = "CURATOR-only KYC dossier review and decision endpoints.")
@PreAuthorize("hasAnyRole('CURATOR','ADMIN')")
class AdminVerificationController(
    private val verificationService: VerificationService,
    private val registryCheckService: AssociationRegistryCheckService,
) {

    @GetMapping
    @Operation(
        summary = "List verification dossiers",
        description = "Returns a paginated list of association dossiers filtered by status. Defaults to PENDING."
    )
    @ApiResponses(
        ApiResponse(
            responseCode = "200", description = "Dossier list returned",
            content = [Content(schema = Schema(implementation = AdminVerificationSummaryDto::class))]
        ),
        ApiResponse(responseCode = "401", description = "Missing or invalid JWT", content = [Content()]),
        ApiResponse(responseCode = "403", description = "Insufficient role", content = [Content()]),
    )
    fun listVerifications(
        @Parameter(description = "Filter by verification status (default: PENDING)")
        @RequestParam(defaultValue = "PENDING") status: VerificationStatus,
        @PageableDefault(size = 20, sort = ["verificationSubmittedAt"]) pageable: Pageable,
    ): ResponseEntity<PageResponse<AdminVerificationSummaryDto>> =
        ResponseEntity.ok(verificationService.adminListVerifications(status, pageable).toPageResponse())

    @GetMapping("/{associationId}")
    @Operation(
        summary = "Get dossier detail",
        description = "Returns the full dossier for one association: status, required document slots, and optional docs."
    )
    @ApiResponses(
        ApiResponse(
            responseCode = "200", description = "Dossier detail returned",
            content = [Content(schema = Schema(implementation = AdminVerificationDetailDto::class))]
        ),
        ApiResponse(responseCode = "401", description = "Missing or invalid JWT", content = [Content()]),
        ApiResponse(responseCode = "403", description = "Insufficient role", content = [Content()]),
        ApiResponse(responseCode = "404", description = "Association not found", content = [Content()]),
    )
    fun getDetail(
        @PathVariable associationId: UUID,
    ): ResponseEntity<AdminVerificationDetailDto> =
        ResponseEntity.ok(verificationService.adminGetDetail(associationId))

    @GetMapping("/{associationId}/documents/{docId}/content")
    @Operation(
        summary = "Download a dossier document",
        description = "Returns the raw binary content of a document belonging to the given association."
    )
    @ApiResponses(
        ApiResponse(responseCode = "200", description = "File content returned"),
        ApiResponse(responseCode = "401", description = "Missing or invalid JWT", content = [Content()]),
        ApiResponse(responseCode = "403", description = "Insufficient role", content = [Content()]),
        ApiResponse(responseCode = "404", description = "Document not found", content = [Content()]),
    )
    fun downloadDocument(
        @PathVariable associationId: UUID,
        @PathVariable docId: UUID,
    ): ResponseEntity<ByteArray> {
        val (meta, content) = verificationService.adminDownloadDocument(associationId, docId)
        val headers = HttpHeaders()
        headers.contentType = MediaType.parseMediaType(meta.contentType)
        headers.contentDisposition = ContentDisposition.attachment().filename(meta.fileName).build()
        return ResponseEntity.ok().headers(headers).body(content)
    }

    @PostMapping("/{associationId}/approve")
    @Operation(
        summary = "Approve a dossier",
        description = "Transitions the dossier from PENDING to VERIFIED. Returns 409 if not in PENDING state."
    )
    @ApiResponses(
        ApiResponse(responseCode = "204", description = "Dossier approved", content = [Content()]),
        ApiResponse(responseCode = "401", description = "Missing or invalid JWT", content = [Content()]),
        ApiResponse(responseCode = "403", description = "Insufficient role", content = [Content()]),
        ApiResponse(responseCode = "404", description = "Association not found", content = [Content()]),
        ApiResponse(responseCode = "409", description = "Dossier is not in PENDING state", content = [Content()]),
    )
    fun approve(
        @PathVariable associationId: UUID,
    ): ResponseEntity<Void> {
        verificationService.adminApprove(associationId)
        return ResponseEntity.noContent().build()
    }

    @PostMapping("/{associationId}/reject")
    @Operation(
        summary = "Reject a dossier",
        description = "Transitions the dossier from PENDING to REJECTED with an admin-provided reason. Returns 409 if not in PENDING state."
    )
    @ApiResponses(
        ApiResponse(responseCode = "204", description = "Dossier rejected", content = [Content()]),
        ApiResponse(responseCode = "401", description = "Missing or invalid JWT", content = [Content()]),
        ApiResponse(responseCode = "403", description = "Insufficient role", content = [Content()]),
        ApiResponse(responseCode = "404", description = "Association not found", content = [Content()]),
        ApiResponse(responseCode = "409", description = "Dossier is not in PENDING state", content = [Content()]),
        ApiResponse(responseCode = "422", description = "Reason is blank or exceeds 1000 characters", content = [Content()]),
    )
    fun reject(
        @PathVariable associationId: UUID,
        @Valid @RequestBody request: RejectVerificationRequest,
    ): ResponseEntity<Void> {
        verificationService.adminReject(associationId, request.reason)
        return ResponseEntity.noContent().build()
    }

    @GetMapping("/{associationId}/registry-precheck")
    @Operation(
        summary = "Latest registry pre-check",
        description = "Returns the most recent persisted registry pre-check for the association, without " +
                "contacting any external registry. Returns 204 if the association has never been scanned. " +
                "Use POST to run a fresh scan. Informational only — never auto-approves or rejects."
    )
    @ApiResponses(
        ApiResponse(
            responseCode = "200", description = "Latest persisted pre-check returned",
            content = [Content(schema = Schema(implementation = RegistryPreCheckDto::class))]
        ),
        ApiResponse(responseCode = "204", description = "Association has never been scanned", content = [Content()]),
        ApiResponse(responseCode = "401", description = "Missing or invalid JWT", content = [Content()]),
        ApiResponse(responseCode = "403", description = "Insufficient role", content = [Content()]),
    )
    fun latestRegistryPreCheck(
        @PathVariable associationId: UUID,
    ): ResponseEntity<RegistryPreCheckDto> =
        registryCheckService.latest(associationId)
            ?.let { ResponseEntity.ok(it) }
            ?: ResponseEntity.noContent().build()

    @PostMapping("/{associationId}/registry-precheck")
    @Operation(
        summary = "Run a registry pre-check scan",
        description = "Queries French public registries (Recherche d'entreprises, INSEE Sirene, JOAFE, BODACC, RNA/DJEPVA) " +
                "to check the legal existence of the association, then persists the result as a new immutable " +
                "scan (append-only audit trail). Informational only — never auto-approves or rejects. " +
                "Each source degrades gracefully: failures are reported as warnings, not errors."
    )
    @ApiResponses(
        ApiResponse(
            responseCode = "200", description = "Scan performed and persisted (even if some sources failed)",
            content = [Content(schema = Schema(implementation = RegistryPreCheckDto::class))]
        ),
        ApiResponse(responseCode = "401", description = "Missing or invalid JWT", content = [Content()]),
        ApiResponse(responseCode = "403", description = "Insufficient role", content = [Content()]),
        ApiResponse(responseCode = "404", description = "Association not found", content = [Content()]),
    )
    fun scanRegistryPreCheck(
        @PathVariable associationId: UUID,
        @AuthenticationPrincipal principal: UserDetails,
    ): ResponseEntity<RegistryPreCheckDto> =
        ResponseEntity.ok(registryCheckService.scan(associationId, UUID.fromString(principal.username)))
}
