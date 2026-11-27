package org.commonlink.controller

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import org.commonlink.dto.HelloAssoLinkAttemptPageResponse
import org.commonlink.dto.HelloAssoLinkAttemptStatsResponse
import org.commonlink.entity.HelloAssoLinkAttemptOutcome
import org.commonlink.service.HelloAssoLinkAttemptStatsService
import org.springframework.format.annotation.DateTimeFormat
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.time.Instant

/**
 * Curator statistics of the associations' HelloAsso key checks: why links fail. CURATOR / ADMIN, enforced by the
 * `/api/admin/helloasso` path matcher in [org.commonlink.security.SecurityConfig] and by [PreAuthorize].
 */
@RestController
@RequestMapping("/api/admin/helloasso/link-attempts")
@Tag(name = "Admin · HelloAsso link attempts", description = "CURATOR-only statistics of HelloAsso key checks.")
@PreAuthorize("hasAnyRole('CURATOR','ADMIN')")
class AdminHelloAssoLinkAttemptController(
    private val statsService: HelloAssoLinkAttemptStatsService,
) {

    /** Attempts per outcome over a period; see [HelloAssoLinkAttemptStatsService.stats]. */
    @GetMapping("/stats")
    @Operation(
        summary = "HelloAsso key checks per outcome",
        description = "Counts every outcome (0 included) over [from, to). Defaults: to = now, from = to - 30 days.",
    )
    @ApiResponses(
        ApiResponse(
            responseCode = "200", description = "Statistics returned",
            content = [Content(schema = Schema(implementation = HelloAssoLinkAttemptStatsResponse::class))],
        ),
        ApiResponse(responseCode = "401", description = "Missing or invalid JWT", content = [Content()]),
        ApiResponse(responseCode = "403", description = "Insufficient role", content = [Content()]),
        ApiResponse(responseCode = "422", description = "from is not before to", content = [Content()]),
    )
    fun stats(
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) from: Instant?,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) to: Instant?,
    ): ResponseEntity<HelloAssoLinkAttemptStatsResponse> = ResponseEntity.ok(statsService.stats(from, to))

    /** Attempts of a period, newest first; see [HelloAssoLinkAttemptStatsService.list]. */
    @GetMapping
    @Operation(
        summary = "List HelloAsso key checks",
        description = "Attempts over [from, to), newest first, with the association, what HelloAsso returned (name, " +
            "type, RNA) and CommonLink's RNA at that moment. Optional outcome filter. Page size max " +
            "${HelloAssoLinkAttemptStatsService.MAX_PAGE_SIZE}.",
    )
    @ApiResponses(
        ApiResponse(
            responseCode = "200", description = "Attempts returned",
            content = [Content(schema = Schema(implementation = HelloAssoLinkAttemptPageResponse::class))],
        ),
        ApiResponse(responseCode = "401", description = "Missing or invalid JWT", content = [Content()]),
        ApiResponse(responseCode = "403", description = "Insufficient role", content = [Content()]),
        ApiResponse(responseCode = "422", description = "from is not before to, or page/size out of range", content = [Content()]),
    )
    fun list(
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) from: Instant?,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) to: Instant?,
        @RequestParam(required = false) outcome: HelloAssoLinkAttemptOutcome?,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int,
    ): ResponseEntity<HelloAssoLinkAttemptPageResponse> =
        ResponseEntity.ok(statsService.list(from, to, outcome, page, size))
}
