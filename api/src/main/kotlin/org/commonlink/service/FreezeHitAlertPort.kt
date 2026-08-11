package org.commonlink.service

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.util.UUID

/**
 * Extension point for freeze-hit alerts raised at onboarding and at re-screening.
 *
 * Invoked by [FreezeScreeningOnboardingService] when one or more targets match the
 * asset-freeze register. The alert model (table, routing, acknowledgement lifecycle) is
 * implemented in prompt 16. Until then, the no-op bean [NoOpFreezeHitAlertPort] is active;
 * prompt 16 replaces it by declaring a `@Primary` bean or removing the no-op.
 *
 * **Contract** — implementations must be non-blocking and must not throw: a failure to raise
 * an alert must never mask the screening outcome. Log and swallow any internal failure.
 */
interface FreezeHitAlertPort {
    /**
     * @param associationId  The association whose dossier or relationship triggered the hit.
     * @param hits           Non-empty list of targets that matched the register. Each entry
     *                       carries the role (ASSOCIATION / REPRESENTATIVE / BENEFICIAL_OWNER)
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
 * No-op placeholder until prompt 16 implements the compliance alert model.
 * Logs a warning so the hit is visible in application logs (without identifying data).
 */
@Service
class NoOpFreezeHitAlertPort : FreezeHitAlertPort {
    private val logger = LoggerFactory.getLogger(javaClass)

    override fun onFreezeHit(associationId: UUID, hits: List<FreezeHitTarget>) {
        logger.warn(
            "FREEZE HIT — association {} — {} target(s): {} — alert handler not yet implemented (prompt 16)",
            associationId,
            hits.size,
            hits.map { it.role },
        )
    }
}
