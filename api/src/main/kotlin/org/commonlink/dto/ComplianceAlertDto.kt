package org.commonlink.dto

import com.fasterxml.jackson.annotation.JsonRawValue
import org.commonlink.entity.ComplianceAlert
import org.commonlink.entity.ComplianceAuditLog
import org.commonlink.entity.FreezeScreeningMatch
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

data class ComplianceAlertSummaryDto(
    val id: UUID,
    val origin: String,
    val subjectType: String,
    val subjectId: UUID?,
    /**
     * Human-readable designation of the alert subject — association name, beneficial owner name,
     * or donor reference. Null when the subject could not be resolved (deleted dossier, or a
     * SYSTEM-level alert with no subject).
     *
     * Without it the officer cannot tell who an alert concerns without opening another screen.
     */
    val subjectLabel: String?,
    val severity: String,
    val status: String,
    val createdAt: Instant,
    val takenInChargeAt: Instant?,
    val ageSeconds: Long,
)

data class ComplianceAlertDetailDto(
    val id: UUID,
    val origin: String,
    val subjectType: String,
    val subjectId: UUID?,
    val subjectLabel: String?,
    val severity: String,
    val status: String,
    val createdAt: Instant,
    val takenInChargeAt: Instant?,
    val takenInChargeBy: UUID?,
    val takenInChargeByLabel: String?,
    val ageSeconds: Long,
    val decision: String?,
    val decisionRationale: String?,
    val treasuryNotifiedAt: Instant?,
    val treasuryNotificationMethod: String?,
    val treasuryNotificationRef: String?,
    val freezeHistory: List<AuditLogEntryDto>,
    /**
     * The register entries actually matched, with their scores — the evidence the decision rests
     * on. Empty for a `SCREENING_UNAVAILABLE` alert, where no comparison took place.
     */
    val matches: List<FreezeScreeningMatchDto>,
    /** Closed alerts previously ruled on for the same subject, most recent first. Informative only. */
    val priorDecisions: List<PriorDecisionDto>,
    /**
     * Public-registry identity of the subject association, when there is one.
     *
     * This is what lets the officer discriminate: a French loi-1901 with an active RNA and a
     * verified SIREN is not the foreign entity a sanctions programme designates, however close
     * the two names score. Null for donor and beneficial-owner subjects, and when no registry
     * scan has been run.
     */
    val subjectRegistry: SubjectRegistryDto?,
)

/** Public-registry identity of the subject association, shown next to the register match. */
data class SubjectRegistryDto(
    val siren: String?,
    val rna: String?,
    val scopeVerdict: String,
    val associationExists: Boolean?,
    val rnaActive: Boolean?,
    val checkedAt: Instant,
)

/**
 * One correspondence between a screened name and an asset-freeze register entry.
 *
 * [screenedNormalizedName] is the value that actually produced [score] — `"TECHNO +"` is compared
 * as `"TECHNO"`, which is why a 0.93 against `"TECHNOLAB"` is not the surprise it first looks
 * like. Displaying the raw dossier name alone leaves the score unexplainable.
 *
 * [matchedLegalReference] is the decisive field for a false-positive ruling: it names the
 * sanctions programme the entry falls under (`(UE) 2026/509 … Ukraine`, `ONU Corée du Nord`…).
 */
data class FreezeScreeningMatchDto(
    val subjectType: String,
    val subjectId: UUID,
    val screenedNormalizedName: String,
    val sanctionedIdRegistre: Int,
    val matchedName: String,
    val matchedNature: String,
    val matchedLegalReference: String?,
    val matchedDateOfBirth: String?,
    val score: Double,
    val scoreThreshold: Double,
    val algorithm: String,
    val registryPublicationDate: LocalDate,
)

/** Number of freeze alerts still awaiting treatment. Backs the dashboard tile. */
data class OpenAlertCountDto(val count: Long)

/** A previous ruling on the same subject. Shown for context; it suppresses nothing. */
data class PriorDecisionDto(
    val alertId: UUID,
    val origin: String,
    val decision: String?,
    val decisionRationale: String?,
    val createdAt: Instant,
)

data class AuditLogEntryDto(
    val sequenceNo: Long,
    val eventType: String,
    val subjectId: UUID?,
    val actorUserId: UUID?,
    /** Raw JSON payload — embedded as-is in the response object. */
    @JsonRawValue val payload: String,
    val occurredAt: Instant,
)

data class CloseAlertRequest(
    val decision: String,
    val rationale: String,
    val treasuryNotifiedAt: Instant? = null,
    val treasuryNotificationMethod: String? = null,
    val treasuryNotificationRef: String? = null,
)

fun ComplianceAlert.toSummaryDto(
    now: Instant = Instant.now(),
    subjectLabel: String? = null,
) = ComplianceAlertSummaryDto(
    id = id,
    origin = origin.name,
    subjectType = subjectType.name,
    subjectId = subjectId,
    subjectLabel = subjectLabel,
    severity = severity.name,
    status = status.name,
    createdAt = createdAt,
    takenInChargeAt = takenInChargeAt,
    ageSeconds = now.epochSecond - createdAt.epochSecond,
)

fun ComplianceAlert.toDetailDto(
    freezeHistory: List<AuditLogEntryDto>,
    matches: List<FreezeScreeningMatchDto>,
    priorDecisions: List<PriorDecisionDto>,
    now: Instant = Instant.now(),
    subjectLabel: String? = null,
    takenInChargeByLabel: String? = null,
    subjectRegistry: SubjectRegistryDto? = null,
) = ComplianceAlertDetailDto(
    id = id,
    origin = origin.name,
    subjectType = subjectType.name,
    subjectId = subjectId,
    subjectLabel = subjectLabel,
    severity = severity.name,
    status = status.name,
    createdAt = createdAt,
    takenInChargeAt = takenInChargeAt,
    takenInChargeBy = takenInChargeBy,
    takenInChargeByLabel = takenInChargeByLabel,
    ageSeconds = now.epochSecond - createdAt.epochSecond,
    decision = decision?.name,
    decisionRationale = decisionRationale,
    treasuryNotifiedAt = treasuryNotifiedAt,
    treasuryNotificationMethod = treasuryNotificationMethod,
    treasuryNotificationRef = treasuryNotificationRef,
    freezeHistory = freezeHistory,
    matches = matches,
    priorDecisions = priorDecisions,
    subjectRegistry = subjectRegistry,
)

fun FreezeScreeningMatch.toDto() = FreezeScreeningMatchDto(
    subjectType = subjectType.name,
    subjectId = subjectId,
    screenedNormalizedName = screenedNormalizedName,
    sanctionedIdRegistre = sanctionedIdRegistre,
    matchedName = matchedName,
    matchedNature = matchedNature.name,
    matchedLegalReference = matchedLegalReference,
    matchedDateOfBirth = matchedDateOfBirth,
    score = score,
    scoreThreshold = scoreThreshold,
    algorithm = algorithm.name,
    registryPublicationDate = registryPublicationDate,
)

fun ComplianceAlert.toPriorDecisionDto() = PriorDecisionDto(
    alertId = id,
    origin = origin.name,
    decision = decision?.name,
    decisionRationale = decisionRationale,
    createdAt = createdAt,
)

fun ComplianceAuditLog.toEntryDto() = AuditLogEntryDto(
    sequenceNo = sequenceNo,
    eventType = eventType,
    subjectId = subjectId,
    actorUserId = actorUserId,
    payload = payload,
    occurredAt = occurredAt,
)
