package org.commonlink.controller

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.commonlink.dto.AcceptLegalDocumentRequest
import org.commonlink.dto.LegalAcceptanceStateDto
import org.commonlink.entity.LegalDocumentType
import org.commonlink.service.LegalAcceptanceService
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.core.userdetails.UserDetails
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/api/association/legal-acceptance")
@Tag(name = "LegalAcceptance", description = "CGU/CGV acceptance state for the authenticated association")
class LegalAcceptanceController(
    private val legalAcceptanceService: LegalAcceptanceService,
) {

    @GetMapping("/{documentType}")
    @Operation(
        summary = "Get CGU/CGV acceptance state",
        description = "Returns the current document version and whether this association already has a " +
            "standing acceptance of it — drives the pre-checked/disabled state of the publish-time checkbox."
    )
    @ApiResponses(
        ApiResponse(responseCode = "200", description = "Acceptance state returned"),
        ApiResponse(responseCode = "401", description = "Missing or invalid JWT", content = [Content()]),
        ApiResponse(responseCode = "404", description = "Association profile not found, or no published document", content = [Content()]),
    )
    fun getState(
        @AuthenticationPrincipal principal: UserDetails,
        @Parameter(description = "CGU | CGV") @PathVariable documentType: LegalDocumentType,
    ): ResponseEntity<LegalAcceptanceStateDto> =
        ResponseEntity.ok(
            legalAcceptanceService.associationAcceptanceStateForUser(UUID.fromString(principal.username), documentType)
        )

    @PostMapping("/{documentType}")
    @Operation(
        summary = "Accept the current CGU/CGV version",
        description = "Records the association's acceptance of the version the post-login gate displayed. No-op " +
            "if the current version is already accepted. Only the current version can be accepted: versions " +
            "superseded since the last acceptance are never asked for."
    )
    @ApiResponses(
        ApiResponse(responseCode = "200", description = "Accepted (or already accepted) — new state returned"),
        ApiResponse(responseCode = "401", description = "Missing or invalid JWT", content = [Content()]),
        ApiResponse(
            responseCode = "409",
            description = "The displayed version is no longer current (code LEGAL_VERSION_OUTDATED) — reload the text",
            content = [Content()],
        ),
        ApiResponse(responseCode = "404", description = "Association profile not found, or no published document", content = [Content()]),
        ApiResponse(responseCode = "422", description = "Missing or oversized version", content = [Content()]),
    )
    fun accept(
        @AuthenticationPrincipal principal: UserDetails,
        @Parameter(description = "CGU | CGV") @PathVariable documentType: LegalDocumentType,
        @Valid @RequestBody request: AcceptLegalDocumentRequest,
    ): ResponseEntity<LegalAcceptanceStateDto> =
        ResponseEntity.ok(
            legalAcceptanceService.acceptForAssociationUser(
                UUID.fromString(principal.username), documentType, request.version,
            )
        )
}
