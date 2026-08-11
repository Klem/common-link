package org.commonlink.dto

import com.fasterxml.jackson.annotation.JsonRawValue
import org.commonlink.entity.ComplianceAlert
import org.commonlink.entity.ComplianceAuditLog
import java.time.Instant
import java.util.UUID

data class ComplianceAlertSummaryDto(
    val id: UUID,
    val origin: String,
    val subjectType: String,
    val subjectId: UUID?,
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
    val severity: String,
    val status: String,
    val createdAt: Instant,
    val takenInChargeAt: Instant?,
    val takenInChargeBy: UUID?,
    val ageSeconds: Long,
    val decision: String?,
    val decisionRationale: String?,
    val treasuryNotifiedAt: Instant?,
    val treasuryNotificationMethod: String?,
    val treasuryNotificationRef: String?,
    val freezeHistory: List<AuditLogEntryDto>,
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

fun ComplianceAlert.toSummaryDto(now: Instant = Instant.now()) = ComplianceAlertSummaryDto(
    id = id,
    origin = origin.name,
    subjectType = subjectType.name,
    subjectId = subjectId,
    severity = severity.name,
    status = status.name,
    createdAt = createdAt,
    takenInChargeAt = takenInChargeAt,
    ageSeconds = now.epochSecond - createdAt.epochSecond,
)

fun ComplianceAlert.toDetailDto(freezeHistory: List<AuditLogEntryDto>, now: Instant = Instant.now()) = ComplianceAlertDetailDto(
    id = id,
    origin = origin.name,
    subjectType = subjectType.name,
    subjectId = subjectId,
    severity = severity.name,
    status = status.name,
    createdAt = createdAt,
    takenInChargeAt = takenInChargeAt,
    takenInChargeBy = takenInChargeBy,
    ageSeconds = now.epochSecond - createdAt.epochSecond,
    decision = decision?.name,
    decisionRationale = decisionRationale,
    treasuryNotifiedAt = treasuryNotifiedAt,
    treasuryNotificationMethod = treasuryNotificationMethod,
    treasuryNotificationRef = treasuryNotificationRef,
    freezeHistory = freezeHistory,
)

fun ComplianceAuditLog.toEntryDto() = AuditLogEntryDto(
    sequenceNo = sequenceNo,
    eventType = eventType,
    subjectId = subjectId,
    actorUserId = actorUserId,
    payload = payload,
    occurredAt = occurredAt,
)
