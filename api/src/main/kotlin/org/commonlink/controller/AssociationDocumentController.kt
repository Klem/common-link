package org.commonlink.controller

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import org.commonlink.dto.OptionalDocumentDto
import org.commonlink.service.VerificationService
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
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.multipart.MultipartFile
import java.util.UUID

@RestController
@RequestMapping("/api/association/documents")
@Tag(name = "AssociationDocuments", description = "Supplementary (optional) document management for associations")
class AssociationDocumentController(
    private val verificationService: VerificationService,
) {

    @GetMapping
    @Operation(
        summary = "List supplementary documents",
        description = "Returns metadata for all OPTIONAL documents uploaded by the association, newest first."
    )
    @ApiResponses(
        ApiResponse(
            responseCode = "200", description = "Document list returned",
            content = [Content(schema = Schema(implementation = OptionalDocumentDto::class))]
        ),
        ApiResponse(responseCode = "401", description = "Missing or invalid JWT", content = [Content()]),
    )
    fun listDocuments(
        @AuthenticationPrincipal principal: UserDetails,
    ): ResponseEntity<List<OptionalDocumentDto>> =
        ResponseEntity.ok(verificationService.listOptionalDocuments(UUID.fromString(principal.username)))

    @PostMapping(consumes = [MediaType.MULTIPART_FORM_DATA_VALUE])
    @Operation(
        summary = "Upload a supplementary document",
        description = "Uploads a document in any category. Accepted types: PDF, JPEG, PNG, DOCX, XLSX. Max size: 10 MB."
    )
    @ApiResponses(
        ApiResponse(
            responseCode = "201", description = "Document uploaded",
            content = [Content(schema = Schema(implementation = OptionalDocumentDto::class))]
        ),
        ApiResponse(responseCode = "401", description = "Missing or invalid JWT", content = [Content()]),
        ApiResponse(responseCode = "422", description = "Invalid file type, size, or category", content = [Content()]),
    )
    fun uploadDocument(
        @AuthenticationPrincipal principal: UserDetails,
        @RequestParam("file") file: MultipartFile,
        @Parameter(description = "Category: FINANCIAL | REPORT | SUPPORTING_DOC | OTHER (default: OTHER)")
        @RequestParam("category", required = false, defaultValue = "OTHER") category: String,
    ): ResponseEntity<OptionalDocumentDto> {
        val dto = verificationService.uploadOptionalDocument(UUID.fromString(principal.username), file, category)
        return ResponseEntity.status(201).body(dto)
    }

    @DeleteMapping("/{id}")
    @Operation(
        summary = "Delete a supplementary document",
        description = "Removes the document. Verifies that the document belongs to the requesting association."
    )
    @ApiResponses(
        ApiResponse(responseCode = "204", description = "Document deleted", content = [Content()]),
        ApiResponse(responseCode = "401", description = "Missing or invalid JWT", content = [Content()]),
        ApiResponse(responseCode = "404", description = "Document not found", content = [Content()]),
    )
    fun deleteDocument(
        @AuthenticationPrincipal principal: UserDetails,
        @PathVariable id: UUID,
    ): ResponseEntity<Void> {
        verificationService.deleteOptionalDocument(UUID.fromString(principal.username), id)
        return ResponseEntity.noContent().build()
    }

    @GetMapping("/{id}/content")
    @Operation(
        summary = "Download document content",
        description = "Returns the raw binary content of a document. Verifies ownership."
    )
    @ApiResponses(
        ApiResponse(responseCode = "200", description = "File content returned"),
        ApiResponse(responseCode = "401", description = "Missing or invalid JWT", content = [Content()]),
        ApiResponse(responseCode = "404", description = "Document not found", content = [Content()]),
    )
    fun downloadDocument(
        @AuthenticationPrincipal principal: UserDetails,
        @PathVariable id: UUID,
    ): ResponseEntity<ByteArray> {
        val (meta, content) = verificationService.downloadDocument(UUID.fromString(principal.username), id)
        val headers = HttpHeaders()
        headers.contentType = MediaType.parseMediaType(meta.contentType)
        headers.contentDisposition = ContentDisposition.attachment()
            .filename(meta.fileName)
            .build()
        return ResponseEntity.ok().headers(headers).body(content)
    }
}
