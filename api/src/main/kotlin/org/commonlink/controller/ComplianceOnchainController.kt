package org.commonlink.controller

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.commonlink.dto.OnchainPauseRequest
import org.commonlink.dto.OnchainPauseResultDto
import org.commonlink.dto.OnchainRegistryStatusDto
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
 * Compliance officer controls over the on-chain registry. Secured by URL (every path under `/api/compliance/` requires
 * `COMPLIANCE_OFFICER`, see `SecurityConfig`). Only freezing is offered: resuming is reserved to the registry admin.
 */
@RestController
@RequestMapping("/api/compliance/onchain")
@Tag(name = "Compliance — on-chain registry", description = "COMPLIANCE_OFFICER-only emergency freeze of the on-chain registry.")
class ComplianceOnchainController(private val onchainPauseService: OnchainPauseService) {

    /** Current state of the registry (simulated mode, chain, address, paused). See [OnchainPauseService.status]. */
    @GetMapping("/status")
    @Operation(summary = "On-chain registry status", description = "Simulated mode, chain, registry address and pause state.")
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
            "an already paused registry gets no transaction. Every request is journaled with its rationale. " +
            "Resuming is reserved to the registry admin (Safe) and is not exposed.",
    )
    @ApiResponses(
        ApiResponse(responseCode = "200", description = "Registry paused (or already paused)"),
        ApiResponse(responseCode = "401", description = "Missing or invalid JWT", content = [Content()]),
        ApiResponse(responseCode = "403", description = "Insufficient role", content = [Content()]),
        ApiResponse(responseCode = "409", description = "Simulated mode: no registry to freeze", content = [Content()]),
        ApiResponse(responseCode = "422", description = "Blank rationale", content = [Content()]),
        ApiResponse(responseCode = "502", description = "The pause could not be read or sent (journaled)", content = [Content()]),
    )
    fun pause(
        @Valid @RequestBody request: OnchainPauseRequest,
        @AuthenticationPrincipal principal: UserDetails,
    ): ResponseEntity<OnchainPauseResultDto> =
        ResponseEntity.ok(onchainPauseService.pause(UUID.fromString(principal.username), request.rationale))
}
