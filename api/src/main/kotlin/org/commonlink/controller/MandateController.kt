package org.commonlink.controller

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.commonlink.dto.MandateStateDto
import org.commonlink.dto.SignMandateRequest
import org.commonlink.entity.AssociationDocumentType
import org.commonlink.service.MandatePdfService
import org.commonlink.service.MandateService
import org.springframework.http.ContentDisposition
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.core.userdetails.UserDetails
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.multipart.MultipartFile
import java.util.UUID

@RestController
@RequestMapping("/api/association/mandate")
@Tag(name = "FiscalMandate", description = "Fiscal mandate lifecycle — signing, revocation, document upload, and PDF download")
class MandateController(
    private val mandateService: MandateService,
    private val mandatePdfService: MandatePdfService,
) {

    @GetMapping
    @Operation(
        summary = "Get mandate state",
        description = "Returns the current fiscal mandate state: whether a mandate is signed, document slot metadata, " +
            "and whether signing is blocked because the association is not yet VERIFIED."
    )
    @ApiResponses(
        ApiResponse(
            responseCode = "200", description = "Mandate state returned",
            content = [Content(schema = Schema(implementation = MandateStateDto::class))]
        ),
        ApiResponse(responseCode = "401", description = "Missing or invalid JWT", content = [Content()]),
        ApiResponse(responseCode = "404", description = "Association profile not found", content = [Content()]),
    )
    fun getMandateState(
        @AuthenticationPrincipal principal: UserDetails,
    ): ResponseEntity<MandateStateDto> =
        ResponseEntity.ok(mandateService.getMandateState(UUID.fromString(principal.username)))

    @PutMapping("/documents/{docType}", consumes = [MediaType.MULTIPART_FORM_DATA_VALUE])
    @Operation(
        summary = "Upload or replace a mandate document",
        description = "Uploads a document for the given slot (MANDATE_STATUTS or MANDATE_RESCRIT). " +
            "Replaces any existing document. Accepted types: PDF, JPEG, PNG, DOCX. Max size: 10 MB. " +
            "Blocked while an active mandate is signed."
    )
    @ApiResponses(
        ApiResponse(responseCode = "204", description = "Document uploaded", content = [Content()]),
        ApiResponse(responseCode = "401", description = "Missing or invalid JWT", content = [Content()]),
        ApiResponse(responseCode = "404", description = "Association profile not found", content = [Content()]),
        ApiResponse(responseCode = "409", description = "Upload blocked: association not VERIFIED, or an active mandate is signed", content = [Content()]),
        ApiResponse(responseCode = "422", description = "Invalid file type, size, or document type", content = [Content()]),
    )
    fun uploadMandateDocument(
        @AuthenticationPrincipal principal: UserDetails,
        @Parameter(description = "Document slot: MANDATE_STATUTS | MANDATE_RESCRIT")
        @PathVariable docType: AssociationDocumentType,
        @RequestParam("file") file: MultipartFile,
    ): ResponseEntity<Void> {
        mandateService.uploadMandateDocument(UUID.fromString(principal.username), docType, file)
        return ResponseEntity.noContent().build()
    }

    @DeleteMapping("/documents/{docType}")
    @Operation(
        summary = "Delete a mandate document",
        description = "Removes the document from the given slot. Blocked while an active mandate is signed."
    )
    @ApiResponses(
        ApiResponse(responseCode = "204", description = "Document deleted", content = [Content()]),
        ApiResponse(responseCode = "401", description = "Missing or invalid JWT", content = [Content()]),
        ApiResponse(responseCode = "404", description = "Document not found", content = [Content()]),
        ApiResponse(responseCode = "409", description = "Delete blocked: association not VERIFIED, or an active mandate is signed", content = [Content()]),
        ApiResponse(responseCode = "422", description = "Document type is not a mandate document", content = [Content()]),
    )
    fun deleteMandateDocument(
        @AuthenticationPrincipal principal: UserDetails,
        @Parameter(description = "Document slot: MANDATE_STATUTS | MANDATE_RESCRIT")
        @PathVariable docType: AssociationDocumentType,
    ): ResponseEntity<Void> {
        mandateService.deleteMandateDocument(UUID.fromString(principal.username), docType)
        return ResponseEntity.noContent().build()
    }

    @PostMapping("/sign")
    @Operation(
        summary = "Sign a fiscal mandate",
        description = "Creates a new fiscal mandate with an electronic timestamp. " +
            "Guards: association must be VERIFIED, both mandate documents must be uploaded, " +
            "no active mandate must already exist, and `accepted` must be true."
    )
    @ApiResponses(
        ApiResponse(
            responseCode = "200", description = "Mandate signed; updated state returned",
            content = [Content(schema = Schema(implementation = MandateStateDto::class))]
        ),
        ApiResponse(responseCode = "401", description = "Missing or invalid JWT", content = [Content()]),
        ApiResponse(responseCode = "404", description = "Association profile not found", content = [Content()]),
        ApiResponse(responseCode = "409", description = "Guard not met: not VERIFIED, docs missing, or active mandate exists", content = [Content()]),
        ApiResponse(responseCode = "422", description = "accepted is false or eligibility missing", content = [Content()]),
    )
    fun signMandate(
        @AuthenticationPrincipal principal: UserDetails,
        @Valid @RequestBody request: SignMandateRequest,
    ): ResponseEntity<MandateStateDto> =
        ResponseEntity.ok(mandateService.signMandate(UUID.fromString(principal.username), request))

    @PostMapping("/revoke")
    @Operation(
        summary = "Revoke the active mandate",
        description = "Marks the active mandate as revoked. Revocation is immediate; previously issued receipts remain valid."
    )
    @ApiResponses(
        ApiResponse(responseCode = "204", description = "Mandate revoked", content = [Content()]),
        ApiResponse(responseCode = "401", description = "Missing or invalid JWT", content = [Content()]),
        ApiResponse(responseCode = "404", description = "No active mandate found", content = [Content()]),
    )
    fun revokeMandate(
        @AuthenticationPrincipal principal: UserDetails,
    ): ResponseEntity<Void> {
        mandateService.revokeMandate(UUID.fromString(principal.username))
        return ResponseEntity.noContent().build()
    }

    @GetMapping("/pdf")
    @Operation(
        summary = "Download mandate PDF",
        description = "Generates and returns the PDF document for the active mandate."
    )
    @ApiResponses(
        ApiResponse(responseCode = "200", description = "PDF returned as attachment"),
        ApiResponse(responseCode = "401", description = "Missing or invalid JWT", content = [Content()]),
        ApiResponse(responseCode = "404", description = "No active mandate found", content = [Content()]),
    )
    fun downloadMandatePdf(
        @AuthenticationPrincipal principal: UserDetails,
    ): ResponseEntity<ByteArray> {
        val (mandate, profile) = mandateService.getMandatePdf(UUID.fromString(principal.username))
        val pdf = mandatePdfService.generate(mandate, profile)
        val headers = HttpHeaders()
        headers.contentType = MediaType.APPLICATION_PDF
        headers.contentDisposition = ContentDisposition.attachment()
            .filename("mandat-${mandate.reference}.pdf")
            .build()
        return ResponseEntity.ok().headers(headers).body(pdf)
    }
}
