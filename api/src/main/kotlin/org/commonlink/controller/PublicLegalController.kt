package org.commonlink.controller

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import org.commonlink.dto.LegalDocumentDto
import org.commonlink.entity.LegalDocumentType
import org.commonlink.service.LegalAcceptanceService
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * Public, unauthenticated read of the current CGU/CGV text — linked from the campaign-publish
 * checkbox and the donor donation form, neither of which requires a CommonLink account.
 */
@RestController
@RequestMapping("/api/public/legal")
@Tag(name = "PublicLegal", description = "Public read access to the current CGU/CGV text")
class PublicLegalController(
    private val legalAcceptanceService: LegalAcceptanceService,
) {

    @GetMapping("/{documentType}")
    @Operation(
        summary = "Get the current CGU/CGV text",
        description = "Returns the currently published version of the given document — content, version, and publication date."
    )
    @ApiResponses(
        ApiResponse(responseCode = "200", description = "Document returned"),
        ApiResponse(responseCode = "404", description = "No published document of this type", content = [Content()]),
    )
    fun getCurrent(
        @Parameter(description = "CGU | CGV") @PathVariable documentType: LegalDocumentType,
    ): ResponseEntity<LegalDocumentDto> =
        ResponseEntity.ok(legalAcceptanceService.currentDocumentDto(documentType))
}
