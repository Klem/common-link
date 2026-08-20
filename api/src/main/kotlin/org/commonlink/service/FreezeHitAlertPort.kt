package org.commonlink.service

import org.commonlink.entity.ComplianceAlertOrigin
import org.commonlink.entity.ComplianceAlertSeverity
import org.commonlink.entity.ComplianceAlertSubjectType
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.util.UUID

/**
 * Port for raising compliance alerts when a freeze-register hit is detected during onboarding
 * or re-screening. Implemented by [FreezeHitAlertAdapter].
 *
 * **Contract** — implementations must be non-blocking and must not throw: a failure to raise
 * an alert must never mask the screening outcome. Log and swallow any internal failure.
 */
interface FreezeHitAlertPort {
    /**
     * @param associationId  The association whose dossier or relationship triggered the hit.
     * @param hits           Non-empty list of targets that matched the register. Each entry
     *                       carries the role (ASSOCIATION / REPRESENTATIVE / BENEFICIAL_OWNER / DONOR)
     *                       and the UUID of the affected entity.
     */
    fun onFreezeHit(associationId: UUID, hits: List<FreezeHitTarget>)

    /**
     * Raises an alert for a screening that could not be performed.
     *
     * An impossible control is not a favorable control: without this the officer never learns
     * that a mandatory check was skipped. See [ComplianceAlertOrigin.SCREENING_UNAVAILABLE].
     *
     * @param subjectType Nature of the party whose screening was attempted.
     * @param subjectId   UUID of that party, or null when the failure is system-wide.
     * @param reason      Technical cause only — never a name. Mirrors the journal convention.
     */
    fun onScreeningUnavailable(
        subjectType: ComplianceAlertSubjectType,
        subjectId: UUID?,
        reason: String,
    )
}

/**
 * Describes one entity that matched the asset-freeze register during a screening.
 *
 * [subjectId] is the UUID of the affected entity:
 * - ASSOCIATION → [org.commonlink.entity.AssociationProfile.id]
 * - REPRESENTATIVE → [org.commonlink.entity.AssociationProfile.id] (no separate entity for the signer)
 * - BENEFICIAL_OWNER → [org.commonlink.entity.BeneficialOwner.id]
 * - DONOR → [org.commonlink.entity.DonorProfile.id]
 *
 * [auditLogSeqRef] is the `sequence_no` of the `FREEZE_SCREENING_HIT` journal entry that recorded
 * this correspondence. It is what ties the alert to its evidence: without it
 * `compliance_alert.audit_log_seq_ref` stays null and the chain alert → journal entry →
 * [org.commonlink.entity.FreezeScreeningMatch] has no anchor.
 */
data class FreezeHitTarget(
    val role: FreezeHitRole,
    val subjectId: UUID,
    val auditLogSeqRef: Long? = null,
)

enum class FreezeHitRole { ASSOCIATION, REPRESENTATIVE, BENEFICIAL_OWNER, DONOR }

/**
 * Creates one [org.commonlink.entity.ComplianceAlert] per freeze-hit target via
 * [ComplianceAlertService.createOrIgnore]. Idempotent: a re-screening on the same subject
 * while an alert is already open produces no duplicate alert.
 *
 * Failures are caught and logged so a transient alert-service error never masks the
 * screening outcome.
 */
@Service
class FreezeHitAlertAdapter(
    private val alertService: ComplianceAlertService,
) : FreezeHitAlertPort {

    private val logger = LoggerFactory.getLogger(javaClass)

    override fun onFreezeHit(associationId: UUID, hits: List<FreezeHitTarget>) {
        hits.forEach { hit ->
            try {
                alertService.createOrIgnore(
                    origin = originFor(hit.role),
                    subjectType = subjectTypeFor(hit.role),
                    subjectId = subjectIdFor(associationId, hit),
                    severity = ComplianceAlertSeverity.HIGH,
                    auditLogSeqRef = hit.auditLogSeqRef,
                )
            } catch (ex: Exception) {
                logger.error(
                    "Échec création alerte gel — rôle {} sujet {}: {}",
                    hit.role, hit.subjectId, ex.message, ex,
                )
            }
        }
    }

    /**
     * Severity is MEDIUM, not HIGH: an unavailable screening means the platform does not know
     * whether there is a match, whereas a HIT means it does. Both must be seen; only one is a
     * suspected correspondence.
     */
    override fun onScreeningUnavailable(
        subjectType: ComplianceAlertSubjectType,
        subjectId: UUID?,
        reason: String,
    ) {
        try {
            alertService.createOrIgnore(
                origin = ComplianceAlertOrigin.SCREENING_UNAVAILABLE,
                subjectType = if (subjectId == null) ComplianceAlertSubjectType.SYSTEM else subjectType,
                subjectId = subjectId,
                severity = ComplianceAlertSeverity.MEDIUM,
            )
        } catch (ex: Exception) {
            logger.error(
                "Échec création alerte criblage indisponible — sujet {} motif {}: {}",
                subjectId, reason, ex.message, ex,
            )
        }
    }

    private fun originFor(role: FreezeHitRole) = when (role) {
        FreezeHitRole.DONOR -> ComplianceAlertOrigin.FREEZE_HIT_DONATION
        else -> ComplianceAlertOrigin.FREEZE_HIT_ONBOARDING
    }

    private fun subjectTypeFor(role: FreezeHitRole) = when (role) {
        FreezeHitRole.ASSOCIATION, FreezeHitRole.REPRESENTATIVE -> ComplianceAlertSubjectType.ASSOCIATION
        FreezeHitRole.BENEFICIAL_OWNER -> ComplianceAlertSubjectType.BENEFICIAL_OWNER
        FreezeHitRole.DONOR -> ComplianceAlertSubjectType.DONOR
    }

    private fun subjectIdFor(associationId: UUID, hit: FreezeHitTarget) = when (hit.role) {
        FreezeHitRole.ASSOCIATION, FreezeHitRole.REPRESENTATIVE -> associationId
        FreezeHitRole.BENEFICIAL_OWNER, FreezeHitRole.DONOR -> hit.subjectId
    }
}
