package org.commonlink.controller

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import org.commonlink.dto.VerificationStateDto
import org.commonlink.entity.AssociationDocumentType
import org.commonlink.service.VerificationService
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.core.userdetails.UserDetails
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.multipart.MultipartFile
import java.util.UUID

@RestController
@RequestMapping("/api/association/verification")
@Tag(name = "Verification", description = "Association KYC verification endpoints")
class VerificationController(
    private val verificationService: VerificationService,
) {

    @GetMapping
    @Operation(
        summary = "Get verification state",
        description = "Returns the current KYC verification state and per-slot document metadata for the 3 required documents."
    )
    @ApiResponses(
        ApiResponse(
            responseCode = "200", description = "Verification state returned",
            content = [Content(schema = Schema(implementation = VerificationStateDto::class))]
        ),
        ApiResponse(responseCode = "401", description = "Missing or invalid JWT", content = [Content()]),
        ApiResponse(responseCode = "404", description = "Association profile not found", content = [Content()]),
    )
    fun getVerificationState(
        @AuthenticationPrincipal principal: UserDetails,
    ): ResponseEntity<VerificationStateDto> =
        ResponseEntity.ok(verificationService.getVerificationState(UUID.fromString(principal.username)))

    @PutMapping("/documents/{docType}", consumes = [MediaType.MULTIPART_FORM_DATA_VALUE])
    @Operation(
        summary = "Upload or replace a required verification document",
        description = "Uploads a document for the given slot. Replaces any existing document for that slot. " +
            "Accepted types: PDF, JPEG, PNG, DOCX. Max size: 10 MB. " +
            "Blocked when status is PENDING or VERIFIED."
    )
    @ApiResponses(
        ApiResponse(responseCode = "204", description = "Document uploaded successfully", content = [Content()]),
        ApiResponse(responseCode = "401", description = "Missing or invalid JWT", content = [Content()]),
        ApiResponse(responseCode = "404", description = "Association profile not found", content = [Content()]),
        ApiResponse(responseCode = "409", description = "Upload blocked by current verification status", content = [Content()]),
        ApiResponse(responseCode = "422", description = "Invalid file type, size, or docType family", content = [Content()]),
    )
    fun uploadVerificationDocument(
        @AuthenticationPrincipal principal: UserDetails,
        @Parameter(description = "Document slot: VERIF_STATUTS | VERIF_RNA_RECEIPT | VERIF_REPRESENTATIVE_ID")
        @PathVariable docType: AssociationDocumentType,
        @RequestParam("file") file: MultipartFile,
    ): ResponseEntity<Void> {
        verificationService.uploadVerificationDocument(UUID.fromString(principal.username), docType, file)
        return ResponseEntity.noContent().build()
    }

    @DeleteMapping("/documents/{docType}")
    @Operation(
        summary = "Delete a required verification document",
        description = "Removes the document from the given slot. Blocked when status is PENDING or VERIFIED."
    )
    @ApiResponses(
        ApiResponse(responseCode = "204", description = "Document deleted", content = [Content()]),
        ApiResponse(responseCode = "401", description = "Missing or invalid JWT", content = [Content()]),
        ApiResponse(responseCode = "404", description = "Document not found", content = [Content()]),
        ApiResponse(responseCode = "409", description = "Delete blocked by current verification status", content = [Content()]),
    )
    fun deleteVerificationDocument(
        @AuthenticationPrincipal principal: UserDetails,
        @Parameter(description = "Document slot: VERIF_STATUTS | VERIF_RNA_RECEIPT | VERIF_REPRESENTATIVE_ID")
        @PathVariable docType: AssociationDocumentType,
    ): ResponseEntity<Void> {
        verificationService.deleteVerificationDocument(UUID.fromString(principal.username), docType)
        return ResponseEntity.noContent().build()
    }

    @PostMapping("/submit")
    @Operation(
        summary = "Submit verification dossier",
        description = "Submits the dossier for admin review. Requires all 3 documents to be uploaded. " +
            "Transitions status from UNVERIFIED or REJECTED to PENDING."
    )
    @ApiResponses(
        ApiResponse(responseCode = "204", description = "Dossier submitted, status is now PENDING", content = [Content()]),
        ApiResponse(responseCode = "401", description = "Missing or invalid JWT", content = [Content()]),
        ApiResponse(responseCode = "404", description = "Association profile not found", content = [Content()]),
        ApiResponse(responseCode = "409", description = "Submission blocked: already PENDING/VERIFIED, or documents missing", content = [Content()]),
    )
    fun submitVerification(
        @AuthenticationPrincipal principal: UserDetails,
    ): ResponseEntity<Void> {
        verificationService.submitVerification(UUID.fromString(principal.username))
        return ResponseEntity.noContent().build()
    }
}
