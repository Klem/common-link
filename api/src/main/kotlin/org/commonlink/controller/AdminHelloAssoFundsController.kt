package org.commonlink.controller

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import org.commonlink.dto.HelloAssoFundsReleaseResponse
import org.commonlink.dto.HelloAssoReleasableFundsResponse
import org.commonlink.service.HelloAssoFundsReleaseService
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.core.userdetails.UserDetails
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RequestPart
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.multipart.MultipartFile
import java.util.UUID

/**
 * Curator back-office (API only, no screen in this sprint): HelloAsso donations of an association not available yet,
 * and their manual release on a bank statement line. CURATOR / ADMIN, enforced by the `/api/admin/associations` path
 * matcher in [org.commonlink.security.SecurityConfig] and by [PreAuthorize].
 */
@RestController
@RequestMapping("/api/admin/associations/{associationId}/helloasso-funds")
@Tag(name = "Admin · HelloAsso funds", description = "CURATOR-only manual release of HelloAsso donations.")
@PreAuthorize("hasAnyRole('CURATOR','ADMIN')")
class AdminHelloAssoFundsController(
    private val releaseService: HelloAssoFundsReleaseService,
) {

    /** Lists the association's HelloAsso donations not available yet; see [HelloAssoFundsReleaseService.listReleasable]. */
    @GetMapping
    @Operation(
        summary = "List the association's HelloAsso donations not available yet",
        description = "Donations still tracked at HelloAsso (AT_PROVIDER, PAYOUT_PREPARING, IN_TRANSIT) with campaign, " +
            "amount, last HelloAsso read, and the association's HelloAsso link state.",
    )
    @ApiResponses(
        ApiResponse(
            responseCode = "200", description = "Donations returned",
            content = [Content(schema = Schema(implementation = HelloAssoReleasableFundsResponse::class))],
        ),
        ApiResponse(responseCode = "401", description = "Missing or invalid JWT", content = [Content()]),
        ApiResponse(responseCode = "403", description = "Insufficient role", content = [Content()]),
        ApiResponse(responseCode = "404", description = "Association not found", content = [Content()]),
    )
    fun list(@PathVariable associationId: UUID): ResponseEntity<HelloAssoReleasableFundsResponse> =
        ResponseEntity.ok(releaseService.listReleasable(associationId))

    /** Releases donations on a bank statement line; see [HelloAssoFundsReleaseService.release]. */
    @PostMapping("/release", consumes = [MediaType.MULTIPART_FORM_DATA_VALUE])
    @Operation(
        summary = "Release HelloAsso donations by hand",
        description = "Makes the given donations available for payments when HelloAsso cannot confirm the payout " +
            "arrived (incident, link disconnected or broken). Requires a reason (max " +
            "${HelloAssoFundsReleaseService.REASON_MAX_LENGTH} characters), the bank statement line copied as text (max " +
            "${HelloAssoFundsReleaseService.STATEMENT_LINE_MAX_LENGTH} characters) and a file of that single line (PDF, " +
            "JPEG or PNG, 10 MB max). One transaction; journaled in the compliance log without the line nor the file.",
    )
    @ApiResponses(
        ApiResponse(
            responseCode = "200", description = "Donations released",
            content = [Content(schema = Schema(implementation = HelloAssoFundsReleaseResponse::class))],
        ),
        ApiResponse(responseCode = "401", description = "Missing or invalid JWT", content = [Content()]),
        ApiResponse(responseCode = "403", description = "Insufficient role", content = [Content()]),
        ApiResponse(
            responseCode = "404", description = "Association not found, or a donation is not a tracked HelloAsso donation of it",
            content = [Content()],
        ),
        ApiResponse(
            responseCode = "409", description = "A donation is already available or withdrawn (code FUNDS_RELEASE_NOT_ALLOWED)",
            content = [Content()],
        ),
        ApiResponse(
            responseCode = "422", description = "Missing or too long reason / line, no donation, missing or invalid file",
            content = [Content()],
        ),
    )
    fun release(
        @AuthenticationPrincipal principal: UserDetails,
        @PathVariable associationId: UUID,
        @RequestParam("donationIds") donationIds: List<UUID>,
        @RequestParam("reason", required = false) reason: String?,
        @RequestParam("statementLine", required = false) statementLine: String?,
        @RequestPart("file", required = false) file: MultipartFile?,
    ): ResponseEntity<HelloAssoFundsReleaseResponse> =
        ResponseEntity.ok(
            releaseService.release(associationId, donationIds, reason, statementLine, file, UUID.fromString(principal.username)),
        )
}
