package org.commonlink.controller

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import org.commonlink.dto.MollieKycStatusDto
import org.commonlink.service.MollieConnectService
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Profile
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.core.userdetails.UserDetails
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

/**
 * DEV/STAGING-ONLY Mollie Connect endpoints, mirroring the declarative mock style used by the
 * on-chain layer ([org.commonlink.onchain.MockOnchainRegistry]): the whole controller bean is only
 * registered when `app.mollie.connect.allow-fake-completion=true`. `application-prod.yml` sets that
 * flag `false` explicitly, and `@Profile("!prod")` is a belt-and-braces guard, so under the prod
 * profile the bean never exists and the routes return 404. (Base `application.yml` defaults the flag
 * to `true` for local dev — prod safety relies on the explicit override, not the base default.)
 *
 * Unlike `app.mollie.connect.mock` (which bypasses the OAuth popup entirely and fabricates a
 * connection), these endpoints operate on a REAL connection created through the genuine popup flow —
 * they only simulate the final KYC-validation step Mollie has no dashboard button for.
 *
 * Still association-only: the `/api/mollie` route prefix requires ROLE_ASSOCIATION (SecurityConfig).
 */
@RestController
@RequestMapping("/api/mollie/connect/dev")
@Profile("!prod")
@ConditionalOnProperty(prefix = "app.mollie.connect", name = ["allow-fake-completion"], havingValue = "true")
@Tag(name = "Mollie Connect (dev)", description = "Dev/staging-only helpers to simulate Mollie KYC validation")
class MollieConnectMockController(
    private val mollieConnectService: MollieConnectService,
) {

    /**
     * Simulates Mollie validating the association's KYC by flipping an existing connection to
     * COMPLETED. Does not touch Mollie or bypass the OAuth popup — the connection must already exist.
     *
     * @param principal Authenticated user — must be an association profile with an existing connection.
     * @return the refreshed [MollieKycStatusDto] (now COMPLETED).
     */
    @PostMapping("/complete")
    @Operation(
        summary = "[DEV] Force Mollie onboarding to COMPLETED",
        description = "Simulates a Mollie KYC validation. Route only exists when allow-fake-completion=true (dev/staging)."
    )
    @ApiResponses(
        ApiResponse(
            responseCode = "200", description = "Onboarding forced to COMPLETED",
            content = [Content(schema = Schema(implementation = MollieKycStatusDto::class))]
        ),
        ApiResponse(responseCode = "401", description = "Missing or invalid JWT", content = [Content()]),
        ApiResponse(responseCode = "403", description = "Not an association account", content = [Content()]),
        ApiResponse(responseCode = "404", description = "Association profile not found", content = [Content()])
    )
    fun forceComplete(@AuthenticationPrincipal principal: UserDetails): ResponseEntity<MollieKycStatusDto> {
        val status = mollieConnectService.forceCompleteOnboarding(UUID.fromString(principal.username))
        return ResponseEntity.ok(status)
    }
}
