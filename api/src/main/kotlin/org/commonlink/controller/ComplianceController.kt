package org.commonlink.controller

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.commonlink.dto.AuditLogEntryDto
import org.commonlink.dto.CloseAlertRequest
import org.commonlink.dto.ComplianceAlertDetailDto
import org.commonlink.dto.ComplianceRegistryScanSummaryDto
import org.commonlink.dto.PageResponse
import org.commonlink.dto.ComplianceAlertSummaryDto
import org.commonlink.dto.FreezeScreeningMatchDto
import org.commonlink.dto.OpenAlertCountDto
import org.commonlink.dto.PriorDecisionDto
import org.commonlink.dto.SubjectRegistryDto
import org.commonlink.dto.toDto
import org.commonlink.dto.toEntryDto
import org.commonlink.dto.toDetailDto
import org.commonlink.dto.toPageResponse
import org.commonlink.dto.toPriorDecisionDto
import org.commonlink.dto.toSummaryDto
import org.commonlink.entity.ComplianceAlert
import org.commonlink.entity.ComplianceAlertDecision
import org.commonlink.entity.ComplianceAlertSubjectType
import org.commonlink.exception.UnprocessableEntityException
import org.commonlink.repository.AssociationProfileRepository
import org.commonlink.repository.AssociationRegistryCheckRepository
import org.commonlink.repository.BeneficialOwnerRepository
import org.commonlink.repository.DonorProfileRepository
import org.commonlink.repository.FreezeScreeningMatchRepository
import org.commonlink.repository.UserRepository
import org.commonlink.service.AssociationRegistryCheckService
import org.commonlink.service.ComplianceAlertService
import org.commonlink.service.ComplianceAuditLogService
import org.springframework.data.domain.PageImpl
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
    private val registryCheckService: AssociationRegistryCheckService,
    private val registryCheckRepository: AssociationRegistryCheckRepository,
    private val associationProfileRepository: AssociationProfileRepository,
    private val beneficialOwnerRepository: BeneficialOwnerRepository,
    private val donorProfileRepository: DonorProfileRepository,
    private val userRepository: UserRepository,
    private val matchRepository: FreezeScreeningMatchRepository,
) {

    /** Health-check / role-gate probe. Used by integration tests to verify COMPLIANCE_OFFICER access. */
    @GetMapping("/ping")
    fun ping(): ResponseEntity<Unit> = ResponseEntity.ok().build()

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
        val result = alertService.listFreezeHitAlerts(pageable)
            .map { it.toSummaryDto(now, resolveSubjectLabel(it)) }
        return ResponseEntity.ok(result.toPageResponse())
    }

    /**
     * Returns the number of freeze-related alerts still awaiting treatment (PENDING or IN_REVIEW).
     *
     * The dashboard previously derived this from the total element count of an unfiltered alert
     * page, which also counted closed alerts — the tile reported a backlog that treatment could
     * never reduce.
     */
    @GetMapping("/alerts/open-count")
    @Operation(
        summary = "Count freeze alerts awaiting treatment",
        description = "Returns the number of PENDING or IN_REVIEW freeze-related alerts. " +
            "Excludes CLOSED alerts, unlike the total of the paginated list."
    )
    @ApiResponses(
        ApiResponse(responseCode = "200", description = "Open alert count"),
        ApiResponse(responseCode = "401", description = "Missing or invalid JWT", content = [Content()]),
        ApiResponse(responseCode = "403", description = "Insufficient role", content = [Content()]),
    )
    fun countOpenAlerts(): ResponseEntity<OpenAlertCountDto> =
        ResponseEntity.ok(OpenAlertCountDto(alertService.countOpenFreezeAlerts()))

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
        val subjectId = alert.subjectId
        val history = if (subjectId != null) {
            auditLogService.findFreezeScreeningHistory(subjectId).map { it.toEntryDto() }
        } else {
            emptyList()
        }
        return ResponseEntity.ok(
            alert.toDetailDto(
                freezeHistory = history,
                matches = resolveMatches(alert),
                priorDecisions = resolvePriorDecisions(alert),
                now = now,
                subjectLabel = resolveSubjectLabel(alert),
                takenInChargeByLabel = alert.takenInChargeBy?.let { resolveUserLabel(it) },
                subjectRegistry = resolveSubjectRegistry(alert),
            ),
        )
    }

    /**
     * Loads the subject association's public-registry identity, when it has one.
     *
     * This is the discriminating evidence for a false-positive ruling: the officer compares an
     * association carrying an active RNA and a verified SIREN against a register entry designated
     * under a foreign sanctions programme. Without it the two sides of the comparison are a name
     * and a name, and only the score distinguishes them.
     */
    private fun resolveSubjectRegistry(alert: ComplianceAlert): SubjectRegistryDto? {
        if (alert.subjectType != ComplianceAlertSubjectType.ASSOCIATION) return null
        val subjectId = alert.subjectId ?: return null
        val scan = registryCheckService.latest(subjectId) ?: return null
        return SubjectRegistryDto(
            siren = scan.siren,
            rna = scan.rna,
            scopeVerdict = scan.scopeVerdict.name,
            associationExists = scan.associationExists,
            rnaActive = scan.rnaActive,
            checkedAt = scan.checkedAt,
        )
    }

    /**
     * Loads the register correspondences backing an alert.
     *
     * An alert raised for a representative or a beneficial owner carries the *association*'s id
     * (see [org.commonlink.service.FreezeHitAlertAdapter]), while the correspondence itself is
     * recorded against the screened party's own id. `associationId` is the only column that
     * bridges the two, so an association-scoped alert must be resolved through it — querying by
     * subject id alone would silently return nothing for exactly the representative hits the
     * officer most needs to see.
     */
    private fun resolveMatches(alert: ComplianceAlert): List<FreezeScreeningMatchDto> {
        val subjectId = alert.subjectId ?: return emptyList()
        val matches = when (alert.subjectType) {
            ComplianceAlertSubjectType.ASSOCIATION ->
                matchRepository.findByAssociationIdOrderByScoreDesc(subjectId)
            else ->
                matchRepository.findBySubjectIdOrderByScoreDesc(subjectId)
        }
        return matches.map { it.toDto() }
    }

    /**
     * Loads previous rulings on the same subject. Informative only — nothing is suppressed:
     * closure is irreversible and every new correspondence raises a new alert, so the officer
     * re-examines each time but is spared re-deriving an identical analysis.
     */
    private fun resolvePriorDecisions(alert: ComplianceAlert): List<PriorDecisionDto> {
        val subjectId = alert.subjectId ?: return emptyList()
        return alertService.findPriorDecisions(subjectId, alert.id).map { it.toPriorDecisionDto() }
    }

    /**
     * Resolves a human-readable designation for the alert subject.
     *
     * Returns null when the subject cannot be resolved — a dossier deleted since the screening,
     * or a SYSTEM alert with no subject. The UI renders that as an explicit "unresolved subject"
     * state rather than a broken link.
     */
    private fun resolveSubjectLabel(alert: ComplianceAlert): String? {
        val subjectId = alert.subjectId ?: return null
        return when (alert.subjectType) {
            ComplianceAlertSubjectType.ASSOCIATION ->
                associationProfileRepository.findById(subjectId).orElse(null)?.name

            ComplianceAlertSubjectType.BENEFICIAL_OWNER ->
                beneficialOwnerRepository.findById(subjectId).orElse(null)?.name

            ComplianceAlertSubjectType.DONOR ->
                donorProfileRepository.findById(subjectId).orElse(null)
                    ?.let { it.displayName ?: it.user.email }

            ComplianceAlertSubjectType.SYSTEM -> null
        }
    }

    /** Resolves the display name of the officer who took an alert in charge. */
    private fun resolveUserLabel(userId: UUID): String? =
        userRepository.findById(userId).orElse(null)?.let { it.displayName ?: it.email }

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
        return ResponseEntity.ok(alert.toSummaryDto(now, resolveSubjectLabel(alert)))
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
        return ResponseEntity.ok(alert.toSummaryDto(now, resolveSubjectLabel(alert)))
    }

    /**
     * Returns a paginated list of the latest registry scan per association (all associations),
     * sorted by most-recent scan date descending.
     *
     * Delegates to [AssociationRegistryCheckService.latest] per association to avoid duplicating
     * service logic. N+1 is acceptable here — this endpoint is compliance-only and not on any
     * hot path.
     */
    @GetMapping("/registry-scans")
    @Operation(
        summary = "List latest registry scans per association",
        description = "Returns one row per association — its most recent registry scan — sorted by " +
            "scan date descending. Enriched with the association name."
    )
    @ApiResponses(
        ApiResponse(responseCode = "200", description = "Paginated scan summaries"),
        ApiResponse(responseCode = "401", description = "Missing or invalid JWT", content = [Content()]),
        ApiResponse(responseCode = "403", description = "Insufficient role", content = [Content()]),
    )
    fun listRegistryScans(
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int,
    ): ResponseEntity<PageResponse<ComplianceRegistryScanSummaryDto>> {
        val allIds = registryCheckRepository.findAssociationIdsWithScansOrderedByLatest()
        val totalElements = allIds.size.toLong()
        val pageIds = allIds.drop(page * size).take(size)
        if (pageIds.isEmpty()) {
            return ResponseEntity.ok(PageImpl(emptyList<ComplianceRegistryScanSummaryDto>(), PageRequest.of(page, size), 0L).toPageResponse())
        }
        val profiles = associationProfileRepository.findAllById(pageIds)
            .filter { it.id != null }
            .associateBy { it.id!! }
        val content = pageIds.mapNotNull { id ->
            val dto = registryCheckService.latest(id) ?: return@mapNotNull null
            val profile = profiles[id] ?: return@mapNotNull null
            ComplianceRegistryScanSummaryDto(
                associationId = id,
                associationName = profile.name,
                associationExists = dto.associationExists,
                rnaActive = dto.rnaActive,
                scopeVerdict = dto.scopeVerdict,
                warningCount = dto.warnings.size,
                checkedAt = dto.checkedAt,
                siren = dto.siren,
                rna = dto.rna,
            )
        }
        val springPage = PageImpl(content, PageRequest.of(page, size), totalElements)
        return ResponseEntity.ok(springPage.toPageResponse())
    }

    /**
     * Returns the twenty most recent compliance audit journal entries, most recent first.
     * Intended for the compliance dashboard overview widget.
     */
    @GetMapping("/audit-log/recent")
    @Operation(
        summary = "Recent compliance audit log entries",
        description = "Returns the twenty most recent entries from the hash-chained compliance journal, " +
            "most recent first."
    )
    @ApiResponses(
        ApiResponse(responseCode = "200", description = "List of recent audit log entries"),
        ApiResponse(responseCode = "401", description = "Missing or invalid JWT", content = [Content()]),
        ApiResponse(responseCode = "403", description = "Insufficient role", content = [Content()]),
    )
    fun listRecentAuditLog(): ResponseEntity<List<AuditLogEntryDto>> {
        val entries = auditLogService.findRecentEntries().map { it.toEntryDto() }
        return ResponseEntity.ok(entries)
    }
}
