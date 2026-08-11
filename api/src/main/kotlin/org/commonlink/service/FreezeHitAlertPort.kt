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
}

/**
 * Describes one entity that matched the asset-freeze register during a screening.
 *
 * [subjectId] is the UUID of the affected entity:
 * - ASSOCIATION → [org.commonlink.entity.AssociationProfile.id]
 * - REPRESENTATIVE → [org.commonlink.entity.AssociationProfile.id] (no separate entity for the signer)
 * - BENEFICIAL_OWNER → [org.commonlink.entity.BeneficialOwner.id]
 * - DONOR → [org.commonlink.entity.DonorProfile.id]
 */
data class FreezeHitTarget(
    val role: FreezeHitRole,
    val subjectId: UUID,
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
                )
            } catch (ex: Exception) {
                logger.error(
                    "Échec création alerte gel — rôle {} sujet {}: {}",
                    hit.role, hit.subjectId, ex.message, ex,
                )
            }
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
