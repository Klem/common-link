package org.commonlink.service

import org.commonlink.entity.ComplianceAuditSubjectType
import org.commonlink.entity.ComplianceDeclarant
import org.commonlink.repository.ComplianceDeclarantRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate
import java.util.UUID

/**
 * Write path for the LCB-FT declarant registry ([ComplianceDeclarant]).
 *
 * Every write appends an event to the shared compliance audit journal via
 * [ComplianceAuditLogService]. The payload carries only the declarant row [id] — never the
 * [org.commonlink.entity.ComplianceDeclarant.teledeclarantNumber], which is encrypted at rest
 * and must never appear in logs, payloads, or DTOs.
 *
 * No HTTP endpoints are exposed: the management UI is deferred to prompt 20 (back-office).
 */
@Service
class ComplianceDeclarantService(
    private val repo: ComplianceDeclarantRepository,
    private val auditLog: ComplianceAuditLogService,
) {

    /**
     * Records a new declarant designation and appends a `DECLARANT_DESIGNATED` journal event.
     *
     * @param userId platform user carrying [org.commonlink.entity.UserRole.COMPLIANCE_OFFICER].
     * @param teledeclarantNumber ERMES portal number — stored encrypted, never logged.
     * @param fullName legal name from the signed designation document — stored encrypted.
     * @param designatedAt date of the signed designation document.
     * @param actorUserId user performing the operation, or null for an automated process.
     */
    @Transactional
    fun designate(
        userId: UUID,
        teledeclarantNumber: String,
        fullName: String,
        designatedAt: LocalDate,
        actorUserId: UUID? = null,
    ): ComplianceDeclarant {
        val declarant = repo.save(
            ComplianceDeclarant(
                userId = userId,
                teledeclarantNumber = teledeclarantNumber,
                fullName = fullName,
                designatedAt = designatedAt,
            ),
        )
        auditLog.append(
            eventType = "DECLARANT_DESIGNATED",
            subjectType = ComplianceAuditSubjectType.DECLARANT,
            payload = mapOf("declarantId" to declarant.id),
            subjectId = declarant.id,
            actorUserId = actorUserId,
        )
        return declarant
    }

    /**
     * Revokes an existing designation by setting [ComplianceDeclarant.revokedAt] and appends a
     * `DECLARANT_REVOKED` journal event. The row is never deleted.
     *
     * @throws NoSuchElementException if [declarantId] does not exist.
     * @throws IllegalStateException if the designation is already revoked.
     */
    @Transactional
    fun revoke(
        declarantId: UUID,
        revokedAt: LocalDate = LocalDate.now(),
        actorUserId: UUID? = null,
    ) {
        val declarant = repo.findById(declarantId)
            .orElseThrow { NoSuchElementException("ComplianceDeclarant $declarantId not found") }
        check(declarant.revokedAt == null) { "ComplianceDeclarant $declarantId is already revoked" }
        declarant.revokedAt = revokedAt
        auditLog.append(
            eventType = "DECLARANT_REVOKED",
            subjectType = ComplianceAuditSubjectType.DECLARANT,
            payload = mapOf("declarantId" to declarantId),
            subjectId = declarantId,
            actorUserId = actorUserId,
        )
    }

    /** Returns all currently active (non-revoked) declarants. */
    @Transactional(readOnly = true)
    fun listActive(): List<ComplianceDeclarant> = repo.findAllByRevokedAtIsNull()
}
