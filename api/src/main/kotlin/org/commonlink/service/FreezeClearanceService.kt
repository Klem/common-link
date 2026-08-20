package org.commonlink.service

import org.commonlink.entity.ComplianceAlertDecision
import org.commonlink.entity.ComplianceAlertOrigin
import org.commonlink.entity.ComplianceAlertStatus
import org.commonlink.repository.BeneficialOwnerRepository
import org.commonlink.repository.ComplianceAlertRepository
import org.commonlink.repository.ComplianceAuditLogRepository
import org.commonlink.repository.FreezeScreeningMatchRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

/**
 * One correspondence already ruled upon: the value that was screened and the register entry it
 * matched.
 *
 * The pair — not the subject alone — is the unit of a clearance. Clearing a subject outright would
 * make every later designation against that same subject invisible, which is precisely the failure
 * an asset-freeze control exists to prevent.
 */
data class ClearedPair(
    val screenedNormalizedName: String,
    val sanctionedIdRegistre: Int,
)

/**
 * The correspondences already ruled out for one dossier, and the decisions that ruled them out.
 *
 * @param pairs every (screened name, register entry) couple covered by a `FALSE_POSITIVE` closure.
 * @param alertIds the alerts whose closure granted the coverage — journalled with each dismissal so
 *   an auditor can walk from a set-aside correspondence back to the decision that set it aside.
 */
data class FreezeClearance(
    val pairs: Set<ClearedPair>,
    val alertIds: List<UUID>,
) {
    /**
     * Whether every correspondence just found was already ruled out.
     *
     * A single uncovered couple — a register update, a newly declared dirigeant — makes the whole
     * screening a hit again: a past ruling speaks only for what it examined.
     */
    fun covers(found: Collection<ClearedPair>): Boolean =
        found.isNotEmpty() && pairs.containsAll(found)

    companion object {
        val NONE = FreezeClearance(emptySet(), emptyList())
    }
}

/**
 * Resolves which freeze correspondences a compliance officer has already ruled out.
 *
 * ### Why this exists
 * Freeze screening is replayed on every approval attempt ([VerificationService.adminApprove] calls
 * [FreezeScreeningOnboardingService.runFreezeCheck] each time). Nothing in that path used to read
 * the officer's ruling, so a `FALSE_POSITIVE` closure changed nothing: the next attempt journalled
 * a fresh `FREEZE_SCREENING_HIT`, opened a duplicate alert — `createOrIgnore` only deduplicates
 * against *open* alerts, and the closed one had freed the slot — and refused the dossier again.
 * The dossier was structurally unapprovable and the officer's decision had no effect on anything.
 *
 * ### What a clearance covers
 * Exactly the (screened name, register entry) pairs the officer had in front of them, and no more:
 *
 * - **Scope** mirrors the alert detail screen. An alert whose subject is an association displays
 *   every correspondence of that association — its own, its representatives', its beneficial
 *   owners' ([FreezeScreeningMatchRepository.findByAssociationIdOrderByScoreDesc]); a beneficial
 *   owner's alert displays only that owner's. The clearance is granted on the same scope, so the
 *   officer never lifts a correspondence they could not read.
 * - **Time** is bounded by the `sequence_no` of the `ALERT_CLOSED` journal entry. Evidence written
 *   after the ruling is, by definition, not what was ruled upon.
 *
 * ### What does not clear
 * Only [ComplianceAlertDecision.FALSE_POSITIVE]. A [ComplianceAlertDecision.SUSPICIOUS] ruling is
 * a confirmed correspondence: the freeze is absolute (art. L.562-1 et s. CMF), no business
 * relationship may be entered into, and the dossier must stay blocked.
 * [ComplianceAlertDecision.LEGITIMATE] does not clear either — a designated party's transaction
 * cannot be "legitimate" for freeze purposes; that decision belongs to the atypicality origins.
 *
 * And only the **latest** ruling on a subject is read: a decision the officer has since revisited
 * must not keep speaking. Not clearing is not the same as blocking — an unfavorable ruling that
 * merely abstained would let the false positive that preceded it go on lifting the correspondence.
 */
@Service
class FreezeClearanceService(
    private val alertRepository: ComplianceAlertRepository,
    private val auditLogRepository: ComplianceAuditLogRepository,
    private val matchRepository: FreezeScreeningMatchRepository,
    private val beneficialOwnerRepository: BeneficialOwnerRepository,
) {

    private val logger = LoggerFactory.getLogger(javaClass)

    /**
     * Every correspondence already ruled out for an association's onboarding — its own, its
     * representatives' and its beneficial owners'.
     *
     * @param associationId UUID of the [org.commonlink.entity.AssociationProfile].
     * @return the clearance, [FreezeClearance.NONE]-equivalent when no `FALSE_POSITIVE` closure
     *   applies — an empty clearance covers nothing, which is the safe default.
     */
    @Transactional(readOnly = true)
    fun forOnboarding(associationId: UUID): FreezeClearance {
        val pairs = mutableSetOf<ClearedPair>()
        val alertIds = mutableListOf<UUID>()

        // Association-scoped alerts also carry representative hits: FreezeHitAlertAdapter maps both
        // ASSOCIATION and REPRESENTATIVE roles onto subject_id = associationId.
        latestRuling(associationId)?.let { (alertId, closureSeq) ->
            alertIds += alertId
            matchRepository
                .findByAssociationIdAndAuditLogSeqRefLessThanEqual(associationId, closureSeq)
                .mapTo(pairs) { ClearedPair(it.screenedNormalizedName, it.sanctionedIdRegistre) }
        }

        // Beneficial owners raise their own alerts, on their own subject id.
        val boIds = beneficialOwnerRepository
            .findAllByAssociationIdOrderByCollectedAtAsc(associationId)
            .mapNotNull { it.id }
        for (boId in boIds) {
            latestRuling(boId)?.let { (alertId, closureSeq) ->
                alertIds += alertId
                matchRepository
                    .findBySubjectIdAndAuditLogSeqRefLessThanEqual(boId, closureSeq)
                    .mapTo(pairs) { ClearedPair(it.screenedNormalizedName, it.sanctionedIdRegistre) }
            }
        }

        return FreezeClearance(pairs, alertIds)
    }

    /**
     * Whether every correspondence recorded by the screening run starting at [runStartSeq] has
     * already been ruled a false positive.
     *
     * Backs the curator's freeze-screen banner, which must show the officer's decision as soon as
     * it is taken rather than at the next approval attempt. The run's correspondences are read from
     * the evidence table, where onboarding rows — the association's, its representatives', its
     * beneficial owners' — all carry the association as grouping context.
     *
     * A run with no evidence row yields false: nothing found is nothing covered, and a screening
     * whose evidence never got written must not read as decided.
     */
    @Transactional(readOnly = true)
    fun coversRunSince(associationId: UUID, runStartSeq: Long): Boolean {
        val found = matchRepository
            .findByAssociationIdAndAuditLogSeqRefGreaterThanEqual(associationId, runStartSeq)
            .map { ClearedPair(it.screenedNormalizedName, it.sanctionedIdRegistre) }
        return forOnboarding(associationId).covers(found)
    }

    /**
     * The subject's **most recent** ruling, and the journal position that dates it — or null when
     * the latest ruling grants nothing.
     *
     * Only the latest counts. A subject can accumulate closures: reading them all and keeping the
     * favorable ones would let a stale `FALSE_POSITIVE` outlive the `SUSPICIOUS` ruling that
     * superseded it, and a confirmed designation would stop blocking. Recency is measured on the
     * journal, not on `updated_at`, because the journal is the hash-chained record.
     *
     * Taking only the latest loses nothing when it *is* favorable: its bound is the highest, and
     * the evidence query is bounded — not anchored — so it already returns everything the earlier
     * rulings covered.
     *
     * An alert closed without a traceable `ALERT_CLOSED` entry is skipped rather than trusted: the
     * clearance would have no bound in time, and an unbounded clearance covers the evidence of the
     * screening currently under way.
     */
    private fun latestRuling(subjectId: UUID): Pair<UUID, Long>? {
        val latest = alertRepository.findBySubjectIdAndOriginAndStatus(
            subjectId = subjectId,
            origin = ComplianceAlertOrigin.FREEZE_HIT_ONBOARDING,
            status = ComplianceAlertStatus.CLOSED,
        ).mapNotNull { alert ->
            val closureSeq = auditLogRepository.findTopBySubjectIdAndEventTypeOrderBySequenceNoDesc(
                alert.id, ComplianceAuditLogService.ALERT_CLOSED,
            )?.sequenceNo
            if (closureSeq == null) {
                logger.warn(
                    "Alert {} is closed but has no ALERT_CLOSED journal entry — ignored when resolving clearances",
                    alert.id,
                )
                null
            } else {
                alert to closureSeq
            }
        }.maxByOrNull { (_, closureSeq) -> closureSeq } ?: return null

        val (alert, closureSeq) = latest
        if (alert.decision != ComplianceAlertDecision.FALSE_POSITIVE) {
            logger.info(
                "Latest ruling on subject {} is {} — no freeze clearance granted",
                subjectId, alert.decision,
            )
            return null
        }
        return alert.id to closureSeq
    }
}
