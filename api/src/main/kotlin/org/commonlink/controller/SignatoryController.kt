package org.commonlink.controller

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import org.commonlink.dto.SignatoryDto
import org.commonlink.service.SignatoryService
import org.springframework.http.CacheControl
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.core.userdetails.UserDetails
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RequestPart
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.multipart.MultipartFile
import java.util.UUID

/**
 * Authorised receipt signer and receipt signature of the authenticated association.
 *
 * Restricted to `ROLE_ASSOCIATION` by the `/api/association` route rule of the security configuration.
 * The principal's username is the **user** UUID; [SignatoryService] resolves the association profile.
 */
@RestController
@RequestMapping("/api/association/me/signatory")
@Tag(name = "Association signatory", description = "Authorised receipt signer and the signature printed on fiscal receipts")
class SignatoryController(
    private val signatoryService: SignatoryService,
) {

    /**
     * Returns the authorised signer and the receipt signature state.
     *
     * @param principal Injected JWT principal; username holds the user UUID.
     * @return 200 with the signer identity and signature metadata.
     */
    @GetMapping
    @Operation(
        summary = "Get the receipt signatory",
        description = "Returns the authorised receipt signer (name, role) and the state of the receipt signature " +
            "(mode, attestation and update instants). The image itself is served by `GET /api/association/me/signatory/image`."
    )
    @ApiResponses(
        ApiResponse(
            responseCode = "200", description = "Signatory returned",
            content = [Content(schema = Schema(implementation = SignatoryDto::class))]
        ),
        ApiResponse(responseCode = "401", description = "Missing or invalid JWT", content = [Content()]),
        ApiResponse(responseCode = "403", description = "Caller is not an association", content = [Content()]),
        ApiResponse(responseCode = "404", description = "Association profile not found", content = [Content()])
    )
    fun getSignatory(@AuthenticationPrincipal principal: UserDetails): ResponseEntity<SignatoryDto> =
        ResponseEntity.ok(signatoryService.getSignatory(UUID.fromString(principal.username)))

    /**
     * Returns the stored signature image bytes, for the association's own preview.
     *
     * @param principal Injected JWT principal; username holds the user UUID.
     * @return 200 with the raw image under its stored Content-Type, never cached.
     */
    @GetMapping("/image")
    @Operation(
        summary = "Get the receipt signature image",
        description = "Returns the raw signature image (PNG or JPEG) with its Content-Type and `Cache-Control: no-store`. " +
            "404 when no signature is saved."
    )
    @ApiResponses(
        ApiResponse(
            responseCode = "200", description = "Image bytes",
            content = [
                Content(mediaType = MediaType.IMAGE_PNG_VALUE),
                Content(mediaType = MediaType.IMAGE_JPEG_VALUE),
            ]
        ),
        ApiResponse(responseCode = "401", description = "Missing or invalid JWT", content = [Content()]),
        ApiResponse(responseCode = "403", description = "Caller is not an association", content = [Content()]),
        ApiResponse(responseCode = "404", description = "No signature image, or association profile not found", content = [Content()])
    )
    fun getSignatureImage(@AuthenticationPrincipal principal: UserDetails): ResponseEntity<ByteArray> {
        val (contentType, bytes) = signatoryService.getSignatureImage(UUID.fromString(principal.username))
        return ResponseEntity.ok()
            .contentType(MediaType.parseMediaType(contentType))
            .cacheControl(CacheControl.noStore())
            .body(bytes)
    }

    /**
     * Saves the authorised signer and the receipt signature under the signatory attestation.
     *
     * Every field is bound as an optional string and validated by [SignatoryService.saveSignatory], so any
     * invalid input — missing field included — is answered with the same 422.
     *
     * @param principal Injected JWT principal; username holds the user UUID.
     * @param signerName Signer full name (≤ 255 characters once trimmed).
     * @param signerRole Signer role (≤ 100 characters once trimmed).
     * @param mode `UPLOAD` or `DRAWN`.
     * @param attested `"true"` when the user ticked the attestation.
     * @param file Signature image (PNG/JPEG, ≤ 200 KB, ≤ 1200x600 px); required for every mode.
     * @return 200 with the saved signatory.
     */
    @PutMapping(consumes = [MediaType.MULTIPART_FORM_DATA_VALUE])
    @Operation(
        summary = "Save the receipt signatory and signature",
        description = "Multipart form. Saves the authorised signer (name, role) and the receipt signature, and journals " +
            "the signatory attestation (RECEIPT_SIGNATURE_ATTESTED) in the compliance audit log. `file` is required for " +
            "every mode (UPLOAD or DRAWN). Receipts already issued are not modified."
    )
    @ApiResponses(
        ApiResponse(
            responseCode = "200", description = "Signatory saved",
            content = [Content(schema = Schema(implementation = SignatoryDto::class))]
        ),
        ApiResponse(responseCode = "401", description = "Missing or invalid JWT", content = [Content()]),
        ApiResponse(responseCode = "403", description = "Caller is not an association", content = [Content()]),
        ApiResponse(responseCode = "404", description = "Association profile not found", content = [Content()]),
        ApiResponse(
            responseCode = "422",
            description = "Missing or invalid field, attestation not accepted, or image refused (type, size, dimensions, undecodable)",
            content = [Content()]
        )
    )
    fun saveSignatory(
        @AuthenticationPrincipal principal: UserDetails,
        @Parameter(description = "Signer full name") @RequestParam("signerName", required = false) signerName: String?,
        @Parameter(description = "Signer role") @RequestParam("signerRole", required = false) signerRole: String?,
        @Parameter(description = "UPLOAD or DRAWN") @RequestParam("mode", required = false) mode: String?,
        @Parameter(description = "\"true\" when the attestation is ticked") @RequestParam("attested", required = false) attested: String?,
        @Parameter(description = "Signature image (PNG or JPEG)") @RequestPart("file", required = false) file: MultipartFile?,
    ): ResponseEntity<SignatoryDto> =
        ResponseEntity.ok(
            signatoryService.saveSignatory(
                userId = UUID.fromString(principal.username),
                signerName = signerName,
                signerRole = signerRole,
                mode = mode,
                attested = attested,
                file = file,
            )
        )
}
