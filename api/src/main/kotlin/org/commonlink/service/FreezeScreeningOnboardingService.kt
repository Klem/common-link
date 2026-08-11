package org.commonlink.service

import org.commonlink.config.SanctionsProperties
import org.commonlink.dto.FreezeScreenStatusDto
import org.commonlink.entity.ComplianceAuditSubjectType
import org.commonlink.exception.NotFoundException
import org.commonlink.repository.AssociationProfileRepository
import org.commonlink.repository.AssociationRegistryCheckRepository
import org.commonlink.repository.BeneficialOwnerRepository
import org.commonlink.repository.SanctionedEntityRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.util.UUID

/**
 * Outcome of a three-party freeze screening at onboarding or re-screening.
 *
 * - [CLEAR]       — all required parties screened, no match above threshold.
 * - [HIT]         — at least one party matched the register; alert port was notified.
 * - [UNAVAILABLE] — screening could not be completed (empty register, missing data,
 *                   service failure). An [UNAVAILABLE] outcome is **not** favorable:
 *                   the caller must treat it identically to [HIT].
 */
enum class ScreeningOutcome { CLEAR, HIT, UNAVAILABLE }

/**
 * Orchestrates the mandatory three-party asset-freeze screening at onboarding (LCB-FT,
 * art. L.561-5 CMF) and provides a re-screening method for existing business relationships.
 *
 * The three parties that must all be screened are:
 * 1. the association itself,
 * 2. its dirigeants as sourced from the official registry scan (prompt 7 — [AssociationRegistryCheck.officers]),
 * 3. each retained beneficial owner.
 *
 * Omitting any one of the three empties the control of its legal meaning.
 *
 * ### Blocking semantics
 * Any outcome other than [ScreeningOutcome.CLEAR] must block the caller's operation.
 * An impossible check ([ScreeningOutcome.UNAVAILABLE]) is not a favorable check —
 * the caller must refuse and not silently allow entry into the business relationship.
 *
 * ### Log hygiene
 * Screened names are never written to application logs. Per-screening traces belong
 * exclusively in the compliance audit journal ([ComplianceAuditLogService]).
 *
 * ### Alert extension point
 * On a hit, [FreezeHitAlertPort.onFreezeHit] is called before returning [ScreeningOutcome.HIT].
 * The no-op implementation [NoOpFreezeHitAlertPort] is active until prompt 16 provides
 * the compliance alert model.
 */
@Service
class FreezeScreeningOnboardingService(
    private val screeningService: SanctionScreeningService,
    private val auditLogService: ComplianceAuditLogService,
    private val sanctionedEntityRepository: SanctionedEntityRepository,
    private val beneficialOwnerRepository: BeneficialOwnerRepository,
    private val associationProfileRepository: AssociationProfileRepository,
    private val registryCheckRepository: AssociationRegistryCheckRepository,
    private val props: SanctionsProperties,
    private val alertPort: FreezeHitAlertPort,
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    /**
     * Screens all three mandatory parties and returns the outcome.
     *
     * Designed to be called from [VerificationService.adminApprove] — the method is
     * self-contained and always returns (never throws). Any unexpected failure is caught,
     * journalled as [ScreeningOutcome.UNAVAILABLE], and returned to the caller.
     *
     * @param associationId   UUID of the [org.commonlink.entity.AssociationProfile].
     * @param associationName Registered name of the association.
     */
    fun runFreezeCheck(
        associationId: UUID,
        associationName: String,
    ): ScreeningOutcome {
        return try {
            doFreezeCheck(associationId, associationName)
        } catch (e: Exception) {
            logger.error(
                "Freeze screening failed unexpectedly for association {}: {}",
                associationId, e.javaClass.simpleName,
            )
            try {
                auditLogService.appendFreezeScreeningUnavailable(
                    subjectType = ComplianceAuditSubjectType.ASSOCIATION,
                    subjectId = associationId,
                    reason = e.javaClass.simpleName,
                )
            } catch (logEx: Exception) {
                logger.error(
                    "Failed to record freeze screening failure in audit log for association {}: {}",
                    associationId, logEx.javaClass.simpleName,
                )
            }
            ScreeningOutcome.UNAVAILABLE
        }
    }

    /**
     * Returns the four-state indicator of the last onboarding freeze-screening run for the given
     * association. Delegates the audit-log derivation to [ComplianceAuditLogService] and provides
     * the beneficial-owner IDs so that BO events (stored with `subject_id = bo.id`) are included.
     *
     * Returns [org.commonlink.dto.FreezeScreenStatus.NOT_PERFORMED] if no screening has ever been run.
     */
    fun getOnboardingFreezeScreenStatus(associationId: UUID): FreezeScreenStatusDto {
        val boIds = beneficialOwnerRepository
            .findAllByAssociationIdOrderByCollectedAtAsc(associationId)
            .mapNotNull { it.id }
        return auditLogService.findLastOnboardingFreezeScreenStatus(associationId, boIds)
    }

    /**
     * Re-screens an association that is already in a business relationship.
     *
     * Applies the same three-party screening as at onboarding. Intended for curator-triggered
     * checks when the register evolves after the initial approval. No periodic automation is
     * built here — this method must be called explicitly (e.g. via a future admin endpoint).
     *
     * Journalling and alert-port behaviour are identical to [runFreezeCheck].
     *
     * @throws NotFoundException if [associationId] does not match any association profile.
     */
    fun reScreenAssociation(associationId: UUID): ScreeningOutcome {
        val profile = associationProfileRepository.findById(associationId)
            .orElseThrow { NotFoundException("Association $associationId not found") }
        return runFreezeCheck(associationId, profile.name)
    }

    // -----------------------------------------------------------------------------------------

    private fun doFreezeCheck(associationId: UUID, associationName: String): ScreeningOutcome {
        val publicationDate = sanctionedEntityRepository.findMaxPublicationDate()
        if (publicationDate == null) {
            auditLogService.appendFreezeScreeningUnavailable(
                subjectType = ComplianceAuditSubjectType.ASSOCIATION,
                subjectId = associationId,
                reason = "register empty — ingestion not yet run",
            )
            return ScreeningOutcome.UNAVAILABLE
        }

        val hits = mutableListOf<FreezeHitTarget>()

        // 1. Screen the association itself
        val assocMatches = screeningService.screen(associationName)
        if (assocMatches.isNotEmpty()) {
            auditLogService.appendFreezeScreeningHit(
                subjectType = ComplianceAuditSubjectType.ASSOCIATION,
                subjectId = associationId,
                registryPublicationDate = publicationDate,
                scoreThreshold = props.scoreThreshold,
                matchCount = assocMatches.size,
                topScore = assocMatches.first().score,
            )
            hits += FreezeHitTarget(role = FreezeHitRole.ASSOCIATION, subjectId = associationId)
        } else {
            auditLogService.appendFreezeScreeningClear(
                subjectType = ComplianceAuditSubjectType.ASSOCIATION,
                subjectId = associationId,
                registryPublicationDate = publicationDate,
                scoreThreshold = props.scoreThreshold,
            )
        }

        // 2. Screen each dirigeant sourced from the official registry scan (prompt 7).
        // Only registry-sourced names are authoritative for legal representatives.
        // A missing scan or empty officer list means the representatives cannot be identified
        // from an authoritative source: the check cannot proceed — not equivalent to clear.
        val officers = registryCheckRepository
            .findTopByAssociationIdOrderByCheckedAtDesc(associationId)
            ?.officers ?: emptyList()
        if (officers.isEmpty()) {
            auditLogService.appendFreezeScreeningUnavailable(
                subjectType = ComplianceAuditSubjectType.DECLARANT,
                subjectId = associationId,
                reason = "no registry scan available or no officers listed in latest scan",
            )
            return ScreeningOutcome.UNAVAILABLE
        }
        for (officerName in officers) {
            val repMatches = screeningService.screen(officerName)
            if (repMatches.isNotEmpty()) {
                auditLogService.appendFreezeScreeningHit(
                    subjectType = ComplianceAuditSubjectType.DECLARANT,
                    subjectId = associationId,
                    registryPublicationDate = publicationDate,
                    scoreThreshold = props.scoreThreshold,
                    matchCount = repMatches.size,
                    topScore = repMatches.first().score,
                )
                hits += FreezeHitTarget(role = FreezeHitRole.REPRESENTATIVE, subjectId = associationId)
            } else {
                auditLogService.appendFreezeScreeningClear(
                    subjectType = ComplianceAuditSubjectType.DECLARANT,
                    subjectId = associationId,
                    registryPublicationDate = publicationDate,
                    scoreThreshold = props.scoreThreshold,
                )
            }
        }

        // 3. Screen each retained beneficial owner
        // By the time this method is called from adminApprove, the UBO check has already
        // confirmed that at least one non-discarded beneficial owner exists.
        val bos = beneficialOwnerRepository
            .findAllByAssociationIdOrderByCollectedAtAsc(associationId)
            .filter { !it.discarded }
        for (bo in bos) {
            val boMatches = screeningService.screen(bo.name)
            if (boMatches.isNotEmpty()) {
                auditLogService.appendFreezeScreeningHit(
                    subjectType = ComplianceAuditSubjectType.BENEFICIAL_OWNER,
                    subjectId = bo.id!!,
                    registryPublicationDate = publicationDate,
                    scoreThreshold = props.scoreThreshold,
                    matchCount = boMatches.size,
                    topScore = boMatches.first().score,
                )
                hits += FreezeHitTarget(role = FreezeHitRole.BENEFICIAL_OWNER, subjectId = bo.id!!)
            } else {
                auditLogService.appendFreezeScreeningClear(
                    subjectType = ComplianceAuditSubjectType.BENEFICIAL_OWNER,
                    subjectId = bo.id!!,
                    registryPublicationDate = publicationDate,
                    scoreThreshold = props.scoreThreshold,
                )
            }
        }

        if (hits.isNotEmpty()) {
            try {
                alertPort.onFreezeHit(associationId, hits)
            } catch (e: Exception) {
                logger.error(
                    "Freeze hit alert handler failed for association {}: {} — outcome remains HIT",
                    associationId, e.javaClass.simpleName,
                )
            }
            return ScreeningOutcome.HIT
        }
        return ScreeningOutcome.CLEAR
    }
}
