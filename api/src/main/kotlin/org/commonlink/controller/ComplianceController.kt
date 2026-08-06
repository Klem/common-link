package org.commonlink.controller

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * COMPLIANCE_OFFICER-only back-office entry point.
 *
 * All routes under the /api/compliance/ prefix are gated by [SecurityConfig] to the
 * COMPLIANCE_OFFICER role exclusively. Subsequent prompts will add real routes here.
 */
@RestController
@RequestMapping("/api/compliance")
@Tag(name = "Compliance", description = "COMPLIANCE_OFFICER-only LCB-FT back-office endpoints.")
class ComplianceController {

    /**
     * Liveness probe for the COMPLIANCE_OFFICER access gate.
     *
     * Returns 200 when the caller holds the compliance role. Exists so the access guard
     * is testable immediately; future prompts add substantive routes to this controller.
     */
    @GetMapping("/ping")
    @Operation(
        summary = "Compliance ping",
        description = "Returns 200 when the caller holds the COMPLIANCE_OFFICER role. " +
            "Substantive routes will be added by later implementation steps."
    )
    @ApiResponses(
        ApiResponse(responseCode = "200", description = "Compliance officer authenticated"),
        ApiResponse(responseCode = "401", description = "Missing or invalid JWT"),
        ApiResponse(responseCode = "403", description = "Insufficient role"),
    )
    fun ping(): ResponseEntity<Map<String, String>> =
        ResponseEntity.ok(mapOf("status" to "ok"))
}
