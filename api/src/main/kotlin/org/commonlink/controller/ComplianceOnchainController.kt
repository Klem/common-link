package org.commonlink.controller

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.commonlink.dto.OnchainControlEntryDto
import org.commonlink.dto.OnchainFailedJobDto
import org.commonlink.dto.OnchainJobFeedDto
import org.commonlink.dto.OnchainOutboxHealthDto
import org.commonlink.dto.OnchainPauseRequest
import org.commonlink.dto.OnchainPauseResultDto
import org.commonlink.dto.OnchainRegistryStatusDto
import org.commonlink.dto.OnchainUnpauseRequest
import org.commonlink.dto.OnchainUnpauseResultDto
import org.commonlink.service.OnchainOutboxReportService
import org.commonlink.service.OnchainPauseService
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.core.userdetails.UserDetails
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

/**
 * Compliance officer controls over the on-chain registry: status, freeze, resume, their history, and a read-only view
 * of the outbox. Secured by URL (every path under `/api/compliance/` requires `COMPLIANCE_OFFICER`, see
 * `SecurityConfig`).
 */
@RestController
@RequestMapping("/api/compliance/onchain")
@Tag(name = "Compliance — on-chain registry", description = "COMPLIANCE_OFFICER-only freeze / resume of the on-chain registry and outbox view.")
class ComplianceOnchainController(
    private val onchainPauseService: OnchainPauseService,
    private val outboxReportService: OnchainOutboxReportService,
) {

    /** Current state of the registry (simulated mode, chain, address, paused, resume right). See [OnchainPauseService.status]. */
    @GetMapping("/status")
    @Operation(summary = "On-chain registry status", description = "Simulated mode, chain, registry address, pause state and whether the platform may resume.")
    @ApiResponses(
        ApiResponse(responseCode = "200", description = "Status (paused is null when the chain cannot be read)"),
        ApiResponse(responseCode = "401", description = "Missing or invalid JWT", content = [Content()]),
        ApiResponse(responseCode = "403", description = "Insufficient role", content = [Content()]),
    )
    fun status(): ResponseEntity<OnchainRegistryStatusDto> = ResponseEntity.ok(onchainPauseService.status())

    /** Emergency freeze of every registry write, journaled with the officer and rationale. See [OnchainPauseService.pause]. */
    @PostMapping("/pause")
    @Operation(
        summary = "Freeze the on-chain registry",
        description = "Sends pause() signed by the dedicated pauser key, directly (not through the outbox). Idempotent: " +
            "an already paused registry gets no transaction. Every request is journaled with its rationale.",
    )
    @ApiResponses(
        ApiResponse(responseCode = "200", description = "Registry paused (or already paused)"),
        ApiResponse(responseCode = "401", description = "Missing or invalid JWT", content = [Content()]),
        ApiResponse(responseCode = "403", description = "Insufficient role", content = [Content()]),
        ApiResponse(responseCode = "409", description = "Simulated mode: no registry to freeze", content = [Content()]),
        ApiResponse(responseCode = "422", description = "Blank or too long rationale", content = [Content()]),
        ApiResponse(responseCode = "502", description = "The pause could not be read or sent (journaled)", content = [Content()]),
    )
    fun pause(
        @Valid @RequestBody request: OnchainPauseRequest,
        @AuthenticationPrincipal principal: UserDetails,
    ): ResponseEntity<OnchainPauseResultDto> =
        ResponseEntity.ok(onchainPauseService.pause(UUID.fromString(principal.username), request.rationale))

    /** Resume of every registry write, journaled with the officer and rationale. See [OnchainPauseService.unpause]. */
    @PostMapping("/unpause")
    @Operation(
        summary = "Resume the on-chain registry",
        description = "Sends unpause() signed by the dedicated pauser key (UNPAUSER_ROLE), directly. Idempotent: a " +
            "registry that is not paused gets no transaction. Refused once the registry admin revoked UNPAUSER_ROLE " +
            "from the platform key. Every request is journaled with its rationale.",
    )
    @ApiResponses(
        ApiResponse(responseCode = "200", description = "Registry resumed (or not paused)"),
        ApiResponse(responseCode = "401", description = "Missing or invalid JWT", content = [Content()]),
        ApiResponse(responseCode = "403", description = "Insufficient role", content = [Content()]),
        ApiResponse(responseCode = "409", description = "Simulated mode, or the platform key lost UNPAUSER_ROLE (journaled)", content = [Content()]),
        ApiResponse(responseCode = "422", description = "Blank or too long rationale", content = [Content()]),
        ApiResponse(responseCode = "502", description = "The resume could not be read or sent (journaled)", content = [Content()]),
    )
    fun unpause(
        @Valid @RequestBody request: OnchainUnpauseRequest,
        @AuthenticationPrincipal principal: UserDetails,
    ): ResponseEntity<OnchainUnpauseResultDto> =
        ResponseEntity.ok(onchainPauseService.unpause(UUID.fromString(principal.username), request.rationale))

    /** Freezes and resumes requested from the platform, newest first, with the officer. See [OnchainPauseService.history]. */
    @GetMapping("/history")
    @Operation(summary = "Freeze / resume history", description = "The fifty most recent freeze and resume requests (successful, idempotent or failed), from the compliance audit log.")
    @ApiResponses(
        ApiResponse(responseCode = "200", description = "Journal entries, newest first, with the officer's email"),
        ApiResponse(responseCode = "401", description = "Missing or invalid JWT", content = [Content()]),
        ApiResponse(responseCode = "403", description = "Insufficient role", content = [Content()]),
    )
    fun history(): ResponseEntity<List<OnchainControlEntryDto>> = ResponseEntity.ok(onchainPauseService.history())

    /** Outbox counts for the current chain. See [OnchainOutboxReportService.health]. */
    @GetMapping("/outbox")
    @Operation(summary = "On-chain outbox health", description = "Pending, running and failed registry writes of the current chain, blocked ones, oldest pending.")
    @ApiResponses(
        ApiResponse(responseCode = "200", description = "Outbox counts"),
        ApiResponse(responseCode = "401", description = "Missing or invalid JWT", content = [Content()]),
        ApiResponse(responseCode = "403", description = "Insufficient role", content = [Content()]),
    )
    fun outbox(): ResponseEntity<OnchainOutboxHealthDto> = ResponseEntity.ok(outboxReportService.health())

    /** Live feed of the registry writes of the current chain, read-only. See [OnchainOutboxReportService.recentJobs]. */
    @GetMapping("/jobs")
    @Operation(summary = "Live feed of on-chain writes", description = "The fifty most recently queued registry writes of the current chain, every status, newest first; flagged simulated in simulated mode. Polled by the compliance screen.")
    @ApiResponses(
        ApiResponse(responseCode = "200", description = "Feed, newest first"),
        ApiResponse(responseCode = "401", description = "Missing or invalid JWT", content = [Content()]),
        ApiResponse(responseCode = "403", description = "Insufficient role", content = [Content()]),
    )
    fun jobs(): ResponseEntity<OnchainJobFeedDto> = ResponseEntity.ok(outboxReportService.recentJobs())

    /** Failed registry writes of the current chain, read-only. See [OnchainOutboxReportService.failedJobs]. */
    @GetMapping("/failed-jobs")
    @Operation(summary = "Failed on-chain jobs", description = "The hundred most recent failed registry writes of the current chain, errors with URLs masked. Read-only.")
    @ApiResponses(
        ApiResponse(responseCode = "200", description = "Failed jobs, newest first"),
        ApiResponse(responseCode = "401", description = "Missing or invalid JWT", content = [Content()]),
        ApiResponse(responseCode = "403", description = "Insufficient role", content = [Content()]),
    )
    fun failedJobs(): ResponseEntity<List<OnchainFailedJobDto>> = ResponseEntity.ok(outboxReportService.failedJobs())
}
