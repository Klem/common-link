package org.commonlink.controller

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.commonlink.dto.CloseAlertRequest
import org.commonlink.dto.ComplianceAlertDetailDto
import org.commonlink.dto.PageResponse
import org.commonlink.dto.ComplianceAlertSummaryDto
import org.commonlink.dto.toEntryDto
import org.commonlink.dto.toDetailDto
import org.commonlink.dto.toPageResponse
import org.commonlink.dto.toSummaryDto
import org.commonlink.entity.ComplianceAlertDecision
import org.commonlink.exception.UnprocessableEntityException
import org.commonlink.service.ComplianceAlertService
import org.commonlink.service.ComplianceAuditLogService
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.core.userdetails.UserDetails
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.time.Instant
import java.util.UUID

/**
 * COMPLIANCE_OFFICER-only back-office entry point for LCB-FT alert treatment (prompt 17).
 *
 * All routes under the /api/compliance/ prefix are gated by [SecurityConfig] to the
 * COMPLIANCE_OFFICER role exclusively — CURATOR, ASSOCIATION and DONOR tokens are rejected.
 */
@RestController
@RequestMapping("/api/compliance")
@Tag(name = "Compliance", description = "COMPLIANCE_OFFICER-only LCB-FT back-office endpoints.")
class ComplianceController(
    private val alertService: ComplianceAlertService,
    private val auditLogService: ComplianceAuditLogService,
) {

    /**
     * Lists freeze-hit alerts (FREEZE_HIT_ONBOARDING and FREEZE_HIT_DONATION), most recent first.
     *
     * The response includes [ComplianceAlertSummaryDto.ageSeconds] — the elapsed time since the
     * alert was raised — so the UI can highlight unresolved alerts by age.
     */
    @GetMapping("/alerts")
    @Operation(
        summary = "List freeze-hit compliance alerts",
        description = "Returns a paginated list of FREEZE_HIT_ONBOARDING and FREEZE_HIT_DONATION " +
            "alerts, sorted by creation date descending. Includes ageSeconds for prominence display."
    )
    @ApiResponses(
        ApiResponse(responseCode = "200", description = "Page of alerts"),
        ApiResponse(responseCode = "401", description = "Missing or invalid JWT", content = [Content()]),
        ApiResponse(responseCode = "403", description = "Insufficient role", content = [Content()]),
    )
    fun listAlerts(
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int,
    ): ResponseEntity<PageResponse<ComplianceAlertSummaryDto>> {
        val now = Instant.now()
        val pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"))
        val result = alertService.listFreezeHitAlerts(pageable).map { it.toSummaryDto(now) }
        return ResponseEntity.ok(result.toPageResponse())
    }

    /**
     * Returns the full detail of a single compliance alert, including the freeze-screening
     * audit history for the subject (read from the hash-chained compliance journal).
     *
     * The freeze-screening history reveals what the automated control found at the time of the
     * hit, without exposing any data to the association concerned.
     */
    @GetMapping("/alerts/{alertId}")
    @Operation(
        summary = "Get compliance alert detail",
        description = "Returns alert fields, decision state, treasury notification proof, and the " +
            "chronological freeze-screening audit history for the alert subject."
    )
    @ApiResponses(
        ApiResponse(
            responseCode = "200", description = "Alert detail",
            content = [Content(schema = Schema(implementation = ComplianceAlertDetailDto::class))]
        ),
        ApiResponse(responseCode = "401", description = "Missing or invalid JWT", content = [Content()]),
        ApiResponse(responseCode = "403", description = "Insufficient role", content = [Content()]),
        ApiResponse(responseCode = "404", description = "Alert not found", content = [Content()]),
    )
    fun getAlert(
        @PathVariable @Parameter(description = "Alert UUID") alertId: UUID,
    ): ResponseEntity<ComplianceAlertDetailDto> {
        val now = Instant.now()
        val alert = alertService.findById(alertId)
        val history = if (alert.subjectId != null) {
            auditLogService.findFreezeScreeningHistory(alert.subjectId!!).map { it.toEntryDto() }
        } else {
            emptyList()
        }
        return ResponseEntity.ok(alert.toDetailDto(history, now))
    }

    /**
     * Transitions an alert from PENDING to IN_REVIEW, recording the officer's identity and timestamp.
     *
     * Writes an `ALERT_IN_REVIEW` entry to the compliance journal.
     */
    @PostMapping("/alerts/{alertId}/take-in-charge")
    @Operation(
        summary = "Take a compliance alert in charge",
        description = "Moves the alert from PENDING to IN_REVIEW, logging the acting compliance officer."
    )
    @ApiResponses(
        ApiResponse(responseCode = "200", description = "Alert is now IN_REVIEW"),
        ApiResponse(responseCode = "401", description = "Missing or invalid JWT", content = [Content()]),
        ApiResponse(responseCode = "403", description = "Insufficient role", content = [Content()]),
        ApiResponse(responseCode = "404", description = "Alert not found", content = [Content()]),
        ApiResponse(responseCode = "422", description = "Forbidden status transition", content = [Content()]),
    )
    fun takeInCharge(
        @PathVariable @Parameter(description = "Alert UUID") alertId: UUID,
        @AuthenticationPrincipal principal: UserDetails,
    ): ResponseEntity<ComplianceAlertSummaryDto> {
        val now = Instant.now()
        val officerId = UUID.fromString(principal.username)
        val alert = alertService.takeInCharge(alertId, officerId)
        return ResponseEntity.ok(alert.toSummaryDto(now))
    }

    /**
     * Closes an alert with a decision and mandatory rationale. Optionally records proof of the
     * human notification sent to the Direction générale du Trésor.
     *
     * **The notification to the DG Trésor is a HUMAN GESTURE performed outside the application.
     * No automated transmission exists or will be built.** The three treasury fields merely
     * capture the proof that this gesture was accomplished.
     *
     * When [CloseAlertRequest.decision] is SUSPICIOUS, the treasury notification fields are
     * mandatory (the rationale for a freeze-hit correspondence must be backed by evidence of
     * notification). For LEGITIMATE and FALSE_POSITIVE decisions they are optional.
     *
     * Writes an `ALERT_CLOSED` entry to the compliance journal.
     */
    @PostMapping("/alerts/{alertId}/close")
    @Operation(
        summary = "Close a compliance alert with decision",
        description = "Records the compliance decision, rationale, and optionally the proof of " +
            "DG Trésor notification (mandatory when decision = SUSPICIOUS). " +
            "Notification is a human gesture — no automated transmission is built."
    )
    @ApiResponses(
        ApiResponse(responseCode = "200", description = "Alert closed"),
        ApiResponse(responseCode = "401", description = "Missing or invalid JWT", content = [Content()]),
        ApiResponse(responseCode = "403", description = "Insufficient role", content = [Content()]),
        ApiResponse(responseCode = "404", description = "Alert not found", content = [Content()]),
        ApiResponse(responseCode = "422", description = "Blank rationale, forbidden transition, or missing treasury fields", content = [Content()]),
    )
    fun closeAlert(
        @PathVariable @Parameter(description = "Alert UUID") alertId: UUID,
        @Valid @RequestBody request: CloseAlertRequest,
        @AuthenticationPrincipal principal: UserDetails,
    ): ResponseEntity<ComplianceAlertSummaryDto> {
        val now = Instant.now()
        val officerId = UUID.fromString(principal.username)
        val decision = runCatching { ComplianceAlertDecision.valueOf(request.decision) }
            .getOrElse { throw UnprocessableEntityException("Décision invalide : ${request.decision}") }
        val alert = alertService.close(
            alertId = alertId,
            complianceOfficerUserId = officerId,
            decision = decision,
            rationale = request.rationale,
            treasuryNotifiedAt = request.treasuryNotifiedAt,
            treasuryNotificationMethod = request.treasuryNotificationMethod,
            treasuryNotificationRef = request.treasuryNotificationRef,
        )
        return ResponseEntity.ok(alert.toSummaryDto(now))
    }
}
