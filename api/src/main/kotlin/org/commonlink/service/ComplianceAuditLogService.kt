package org.commonlink.service

import com.fasterxml.jackson.databind.ObjectMapper
import org.commonlink.dto.FreezeScreenStatus
import org.commonlink.dto.FreezeScreenStatusDto
import org.commonlink.entity.ComplianceAuditLog
import org.commonlink.entity.ComplianceAuditSubjectType
import org.commonlink.repository.ComplianceAuditLogRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import java.security.MessageDigest
import java.time.Instant
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.util.UUID

/**
 * Sole write path for the LCB-FT compliance audit journal (`compliance_audit_log`, V51
 * migration). No other code inserts into this table directly — every feature that needs to
 * prove a control happened (freeze screening, alert review, publication refusals, ...) calls
 * [append] here, so the platform has exactly one append-only, hash-chained journal instead of
 * one per feature.
 *
 * Never pass a secret, an encryption key, or a télédéclarant number in [payload] — this journal
 * has no field-level encryption and is not the place for it.
 *
 * ### Hash chain
 *
 * `row_hash` = SHA-256 over the canonical, length-prefixed concatenation of `sequence_no`,
 * `event_type`, `subject_type`, `subject_id`, `payload`, `actor_user_id`, `occurred_at` and
 * `prev_hash`, in that fixed order (see [canonicalBytes]). Each field is encoded as
 * `<utf8 byte length>:<field>` before concatenation — a plain separator such as `|` would let a
 * crafted `payload` shift field boundaries and forge a matching hash; length-prefixing makes the
 * encoding injective regardless of field content. This exact order and encoding is the contract:
 * changing either breaks reproducibility against previously written rows.
 *
 * `occurred_at` is truncated to microseconds before both hashing and persisting, because
 * `Instant.now()` carries nanosecond precision in memory but `TIMESTAMPTZ` only stores
 * microseconds — hashing the untruncated value would make every row fail [verifyChain] as soon
 * as it is read back from the database.
 *
 * ### Concurrency
 *
 * [append] takes a row lock on the single row of `compliance_audit_log_lock`
 * ([ComplianceAuditLogRepository.acquireWriteLock], a plain `SELECT ... FOR UPDATE` — portable,
 * unlike `pg_advisory_xact_lock`) before reading the current tail of the chain, so concurrent
 * writers serialize instead of racing to read the same "previous" row. The lock is taken
 * *before* [ComplianceAuditLogRepository.nextSequenceValue]: `nextval()` is not transactional and
 * never rolls back, so drawing it under the lock means a gap in `sequence_no` can only happen on
 * a rollback, not a race. Default `READ COMMITTED` isolation is required (not `REPEATABLE
 * READ`/`SERIALIZABLE`): those isolation levels snapshot at transaction start, before the lock is
 * acquired, which would make the tail read stale.
 */
@Service
class ComplianceAuditLogService(
    private val repo: ComplianceAuditLogRepository,
    private val objectMapper: ObjectMapper,
) {

    companion object {
        const val FREEZE_SCREENING_CLEAR = "FREEZE_SCREENING_CLEAR"
        const val FREEZE_SCREENING_HIT = "FREEZE_SCREENING_HIT"
        const val FREEZE_SCREENING_UNAVAILABLE = "FREEZE_SCREENING_UNAVAILABLE"

        /**
         * A correspondence was found again, and set aside because a compliance officer had already
         * ruled it a false positive. Distinct from [FREEZE_SCREENING_CLEAR], which states that
         * nothing matched: conflating the two would make the journal claim a screening found
         * nothing when it did.
         */
        const val FREEZE_SCREENING_HIT_CLEARED = "FREEZE_SCREENING_HIT_CLEARED"

        /** All event types written by the freeze-screening journal helpers, for use in queries. */
        val FREEZE_SCREENING_EVENT_TYPES = listOf(
            FREEZE_SCREENING_CLEAR,
            FREEZE_SCREENING_HIT,
            FREEZE_SCREENING_HIT_CLEARED,
            FREEZE_SCREENING_UNAVAILABLE,
        )

        const val SANCTION_SYNC_FAILURE = "SANCTION_SYNC_FAILURE"

        const val ALERT_OPENED = "ALERT_OPENED"
        const val ALERT_IN_REVIEW = "ALERT_IN_REVIEW"
        const val ALERT_CLOSED = "ALERT_CLOSED"

        /** A public visitor reported a campaign's content (IC-44). Written by [org.commonlink.service.CampaignReportService]. */
        const val CAMPAIGN_REPORTED = "CAMPAIGN_REPORTED"

        /**
         * A compliance officer lifted a `SUSPENDED` association back to `ACTIVE`. Written by
         * [org.commonlink.service.AssociationComplianceStatusService.reactivate]. Deliberately
         * distinct from [ALERT_CLOSED]: the alert that caused the suspension stays `CLOSED` with
         * its original `SUSPICIOUS` decision as a historical record — reactivation is a separate
         * fact recorded afterwards, not a reversal of that decision.
         */
        const val ASSOCIATION_REACTIVATED = "ASSOCIATION_REACTIVATED"
    }

    /**
     * Appends one event to the journal and returns the persisted row.
     *
     * @param payload any object Jackson can serialize; stored as JSON text.
     * @param subjectId the target business object (association, donation, campaign, alert), or
     *   null when the event has no single target.
     * @param actorUserId the acting user, or null for an automated process.
     */
    @Transactional
    fun append(
        eventType: String,
        subjectType: ComplianceAuditSubjectType,
        payload: Any,
        subjectId: UUID? = null,
        actorUserId: UUID? = null,
    ): ComplianceAuditLog {
        repo.acquireWriteLock()
        val prevHash = repo.findTopByOrderBySequenceNoDesc()?.rowHash
        val sequenceNo = repo.nextSequenceValue()
        val occurredAt = Instant.now().truncatedTo(ChronoUnit.MICROS)
        val payloadJson = objectMapper.writeValueAsString(payload)
        val rowHash = sha256Hex(
            canonicalBytes(sequenceNo, eventType, subjectType, subjectId, payloadJson, actorUserId, occurredAt, prevHash),
        )
        return repo.save(
            ComplianceAuditLog(
                sequenceNo = sequenceNo,
                eventType = eventType,
                subjectType = subjectType,
                subjectId = subjectId,
                payload = payloadJson,
                actorUserId = actorUserId,
                occurredAt = occurredAt,
                prevHash = prevHash,
                rowHash = rowHash,
            ),
        )
    }

    /**
     * Logs an out-of-scope perimeter refusal in a **new, independent transaction** so the journal
     * entry is committed even if the caller's transaction is subsequently rolled back (e.g. because
     * the caller throws [org.commonlink.exception.ConflictException] after this method returns).
     *
     * Called from [org.commonlink.service.VerificationService.adminApprove] when the latest
     * registry scan reveals a legal category outside the accepted scope. The method returns normally;
     * the caller is responsible for throwing the user-facing exception.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun appendOutOfScopeRefusal(associationId: UUID, legalCategory: String) {
        append(
            eventType = "SCOPE_VERDICT_UNFAVORABLE",
            subjectType = ComplianceAuditSubjectType.ASSOCIATION,
            subjectId = associationId,
            payload = mapOf("legalCategory" to legalCategory, "verdict" to "OUT_OF_SCOPE"),
        )
    }

    /**
     * Logs a "no legal representative" approval refusal in a **new, independent transaction** so the
     * journal entry is committed even if the caller's transaction rolls back.
     *
     * Called from [org.commonlink.service.VerificationService.adminApprove] when no confirmed
     * legal representative exists for the association (art. R.561-3 CMF, décret n°2024-720).
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun appendNoRepresentativeRefusal(associationId: UUID) {
        append(
            eventType = "NO_REPRESENTATIVE_APPROVAL_REFUSED",
            subjectType = ComplianceAuditSubjectType.ASSOCIATION,
            subjectId = associationId,
            payload = mapOf("reason" to "no confirmed legal representative"),
        )
    }

    // -----------------------------------------------------------------------------------------
    // Freeze-screening journal helpers
    // -----------------------------------------------------------------------------------------

    /**
     * Records that a freeze screening ran and found **no match** above the configured threshold.
     * Committed in a **new, independent transaction** ([Propagation.REQUIRES_NEW]) so the journal
     * entry survives even if the caller's transaction is subsequently rolled back — and to avoid
     * holding the write lock across a caller transaction that may later call [appendFreezeScreeningHit]
     * (both methods take the same row lock; mixing REQUIRED and REQUIRES_NEW on the same lock in one
     * caller transaction causes a deadlock).
     *
     * **Callers must ensure the register is not empty before calling this method.** An empty register
     * means no check was actually performed — use [appendFreezeScreeningUnavailable] instead
     * (e.g. `reason = "register empty — ingestion not yet run"`). Recording a clear result against
     * an empty register would be a false negative.
     *
     * @param registryPublicationDate Publication date of the register snapshot consulted, as
     *   returned by `SanctionedEntityRepository.findMaxPublicationDate()`. A null value indicates
     *   the register is empty, which is an [appendFreezeScreeningUnavailable] case — see above.
     * @param scoreThreshold The JaroWinkler threshold applied during the check.
     * @param nature Optional nature filter applied (e.g. PHYSICAL_PERSON), or null for the full
     *   register.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun appendFreezeScreeningClear(
        subjectType: ComplianceAuditSubjectType,
        subjectId: UUID,
        registryPublicationDate: LocalDate?,
        scoreThreshold: Double,
        nature: String? = null,
    ): ComplianceAuditLog = append(
        eventType = FREEZE_SCREENING_CLEAR,
        subjectType = subjectType,
        subjectId = subjectId,
        payload = mapOf(
            "registryPublicationDate" to registryPublicationDate?.toString(),
            "scoreThreshold" to scoreThreshold,
            "matchCount" to 0,
            "nature" to nature,
        ),
    )

    /**
     * Records that a freeze screening ran and found **one or more matches** above the threshold.
     * Committed in a **new, independent transaction** ([Propagation.REQUIRES_NEW]) so the journal
     * entry survives even if the caller subsequently rolls back after raising a block or alert.
     *
     * **Never pass a matched name in any parameter** — [idRegistre] in the caller's `ScreeningMatch`
     * is the correct reference to include in the payload; `ScreeningMatch.nom` must not be passed
     * here.
     *
     * @param matchCount  Number of register entries above the threshold.
     * @param topScore    Highest JaroWinkler score among the matches.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun appendFreezeScreeningHit(
        subjectType: ComplianceAuditSubjectType,
        subjectId: UUID,
        registryPublicationDate: LocalDate?,
        scoreThreshold: Double,
        matchCount: Int,
        topScore: Double,
        nature: String? = null,
    ): ComplianceAuditLog = append(
        eventType = FREEZE_SCREENING_HIT,
        subjectType = subjectType,
        subjectId = subjectId,
        payload = mapOf(
            "registryPublicationDate" to registryPublicationDate?.toString(),
            "scoreThreshold" to scoreThreshold,
            "matchCount" to matchCount,
            "topScore" to topScore,
            "nature" to nature,
        ),
    )

    /**
     * Records that a freeze screening found matches which a prior `FALSE_POSITIVE` ruling had
     * already set aside. Committed in a **new, independent transaction**
     * ([Propagation.REQUIRES_NEW]), like every other freeze-screening helper.
     *
     * Writing this entry is not optional. `docs/legal/E4-journal-controles-de-gel.md` §4.4 makes
     * failures journalable on the ground that a journal silent on them cannot distinguish "no
     * match" from "no control"; a correspondence dismissed in silence reintroduces exactly that
     * ambiguity — the journal would show a screening that found nothing, when it found the same
     * entries as before and an officer had ruled on them.
     *
     * @param clearedByAlertIds Alerts whose closure grants the clearance — the officer's decisions
     *   the auditor must be able to walk back to.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun appendFreezeScreeningHitCleared(
        subjectType: ComplianceAuditSubjectType,
        subjectId: UUID,
        registryPublicationDate: LocalDate?,
        scoreThreshold: Double,
        matchCount: Int,
        topScore: Double,
        clearedByAlertIds: List<UUID>,
    ): ComplianceAuditLog = append(
        eventType = FREEZE_SCREENING_HIT_CLEARED,
        subjectType = subjectType,
        subjectId = subjectId,
        payload = mapOf(
            "registryPublicationDate" to registryPublicationDate?.toString(),
            "scoreThreshold" to scoreThreshold,
            "matchCount" to matchCount,
            "topScore" to topScore,
            "clearedByAlertIds" to clearedByAlertIds.map { it.toString() },
        ),
    )

    /**
     * Records that a freeze screening **could not be completed** (register unavailable, ingestion
     * not yet run, network failure, etc.). Committed in a **new, independent transaction**
     * ([Propagation.REQUIRES_NEW]) so the journal entry survives even if the caller's transaction
     * is already marked for rollback when this method is called (typical pattern: catch the
     * exception, call this method, re-throw).
     *
     * A screening that cannot be completed is **not a silent absence** in the journal — recording
     * the failure is the only way to demonstrate later that the platform attempted the check at the
     * required moment.
     *
     * @param subjectId Null is acceptable when the failure occurs before the subject is known
     *   (e.g. registry download failed before any individual check started).
     * @param reason Short description of the failure cause. Must not contain any subject name or
     *   personal data — technical details only (exception class, HTTP status, etc.).
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun appendFreezeScreeningUnavailable(
        subjectType: ComplianceAuditSubjectType,
        subjectId: UUID? = null,
        reason: String,
    ): ComplianceAuditLog = append(
        eventType = FREEZE_SCREENING_UNAVAILABLE,
        subjectType = subjectType,
        subjectId = subjectId,
        payload = mapOf("reason" to reason),
    )

    /**
     * Returns the freeze-screening history for a given subject (association, donation, …), in
     * chronological order. Covers every outcome: clear, hit, hit set aside by a prior ruling, and
     * unavailable.
     *
     * Intended for auditor review and the curator UI (prompt 17). Read-only — never write through
     * this method.
     */
    @Transactional(readOnly = true)
    fun findFreezeScreeningHistory(subjectId: UUID): List<ComplianceAuditLog> =
        repo.findBySubjectIdAndEventTypeInOrderBySequenceNoAsc(subjectId, FREEZE_SCREENING_EVENT_TYPES)

    /**
     * Returns every [CAMPAIGN_REPORTED] entry for an association, in chronological order.
     *
     * Written with `subject_type = ASSOCIATION` and `subject_id = association.id` (not the
     * reported campaign's id — see [org.commonlink.service.CampaignReportService]), so a second
     * report received while a [org.commonlink.entity.ComplianceAlert] is already open is never
     * lost: the alert deduplicates on (origin, subject), the journal does not.
     */
    @Transactional(readOnly = true)
    fun findCampaignReportHistory(associationId: UUID): List<ComplianceAuditLog> =
        repo.findBySubjectIdAndEventTypeInOrderBySequenceNoAsc(associationId, listOf(CAMPAIGN_REPORTED))

    /**
     * Derives the five-state [FreezeScreenStatus] for the **most recent onboarding screening run**
     * of an association, without exposing any match detail (tipping-off prevention).
     *
     * Identification of the run: the most recent event with `subject_type = ASSOCIATION` and
     * `subject_id = associationId` marks the start of the last run; events with a lower
     * `sequence_no` belong to an earlier run and are ignored.
     *
     * BO events use `subject_id = bo.id` (not `associationId`), so they must be queried
     * separately: [beneficialOwnerIds] must include every BO that belongs to this association.
     *
     * @param associationId       The association whose last run is to be inspected.
     * @param beneficialOwnerIds  All beneficial-owner IDs for that association (may be empty).
     */
    @Transactional(readOnly = true)
    fun findLastOnboardingFreezeScreenStatus(
        associationId: UUID,
        beneficialOwnerIds: List<UUID>,
    ): FreezeScreenStatusDto {
        val assocEvents = repo.findBySubjectIdAndEventTypeInOrderBySequenceNoAsc(
            associationId, FREEZE_SCREENING_EVENT_TYPES,
        )

        // The most recent ASSOCIATION-type event is the anchor of the latest run.
        val latestAssocEvent = assocEvents
            .lastOrNull { it.subjectType == ComplianceAuditSubjectType.ASSOCIATION }
            ?: return FreezeScreenStatusDto(FreezeScreenStatus.NOT_PERFORMED, null)

        val runStartSeq = latestAssocEvent.sequenceNo
        val checkedAt = latestAssocEvent.occurredAt

        // All association/declarant events from this run onward.
        val runAssocEvents = assocEvents.filter { it.sequenceNo >= runStartSeq }

        if (runAssocEvents.any { it.eventType == FREEZE_SCREENING_UNAVAILABLE }) {
            return FreezeScreenStatusDto(FreezeScreenStatus.UNAVAILABLE, checkedAt)
        }
        if (runAssocEvents.any { it.eventType == FREEZE_SCREENING_HIT }) {
            return FreezeScreenStatusDto(FreezeScreenStatus.HIT, checkedAt)
        }

        // Check BO events — they carry subject_id = bo.id, not associationId.
        val boRunEvents = beneficialOwnerIds.flatMap { boId ->
            repo.findBySubjectIdAndEventTypeInOrderBySequenceNoAsc(boId, FREEZE_SCREENING_EVENT_TYPES)
                .filter { it.sequenceNo >= runStartSeq }
        }
        if (boRunEvents.any { it.eventType == FREEZE_SCREENING_HIT }) {
            return FreezeScreenStatusDto(FreezeScreenStatus.HIT, checkedAt)
        }

        // Only once no live hit remains: a party whose correspondence was ruled a false positive.
        // Evaluated last so a single undecided hit anywhere still reports HIT.
        if ((runAssocEvents + boRunEvents).any { it.eventType == FREEZE_SCREENING_HIT_CLEARED }) {
            return FreezeScreenStatusDto(FreezeScreenStatus.HIT_CLEARED, checkedAt)
        }

        return FreezeScreenStatusDto(FreezeScreenStatus.PASSED, checkedAt)
    }

    /**
     * `sequence_no` of the event that opens the most recent onboarding screening run for an
     * association — the same anchor [findLastOnboardingFreezeScreenStatus] derives its status from,
     * exposed so callers can scope other records to that run.
     *
     * @return the anchor, or null when no screening has ever run for this association.
     */
    @Transactional(readOnly = true)
    fun findLastOnboardingRunStartSeq(associationId: UUID): Long? =
        repo.findBySubjectIdAndEventTypeInOrderBySequenceNoAsc(associationId, FREEZE_SCREENING_EVENT_TYPES)
            .lastOrNull { it.subjectType == ComplianceAuditSubjectType.ASSOCIATION }
            ?.sequenceNo

    /**
     * Returns the twenty most recent journal entries in reverse sequence order.
     * Intended for the compliance dashboard overview widget. Read-only.
     */
    @Transactional(readOnly = true)
    fun findRecentEntries(): List<ComplianceAuditLog> =
        repo.findTop20ByOrderBySequenceNoDesc()

    // -----------------------------------------------------------------------------------------
    // Scheduled sync journal helper
    // -----------------------------------------------------------------------------------------

    /**
     * Records that a scheduled synchronisation of the asset-freeze register **failed**.
     * Committed in a **new, independent transaction** ([Propagation.REQUIRES_NEW]) so the
     * journal entry is committed even if the caller's transaction rolls back (typical usage:
     * called from the `catch` block of [org.commonlink.service.SanctionSyncExecutor.execute]).
     *
     * **Failure does not stop screening.** The compliance audit log records the failure so the
     * platform can demonstrate that the control attempted a check at the required moment, even
     * when the registry was temporarily unavailable.
     *
     * @param reason Short technical description of the failure cause. Must not contain any
     *   subject name or personal data — exception class and message only.
     * @param lastSuccessAt Timestamp of the last successful synchronisation, or null if the
     *   register has never been successfully synced. Included in the payload so auditors can
     *   assess how stale the register was at the time of the failure.
     */
    /**
     * Records a public campaign report (IC-44). Committed in a **new, independent transaction**
     * ([Propagation.REQUIRES_NEW]) for the same reason every `appendFreezeScreening*` helper is:
     * the caller ([org.commonlink.service.CampaignReportService.report]) subsequently calls
     * [org.commonlink.service.ComplianceAlertService.createOrIgnore], which is itself
     * `REQUIRES_NEW` and appends its own `ALERT_OPENED` entry. Both calls take the same
     * `compliance_audit_log_lock` row lock — if this method held it under the caller's own
     * `REQUIRED` transaction instead, the suspended caller transaction would still be holding the
     * lock when the nested `REQUIRES_NEW` transaction tries to acquire it, deadlocking every
     * first report on an association (a second report while the alert is still open never reaches
     * this method — `createOrIgnore` returns the existing alert before it — which is why this
     * class of bug does not show up until the very first submission).
     *
     * Written with `subject_type = ASSOCIATION` and `subject_id = associationId` — not the
     * reported campaign's id — so [findCampaignReportHistory] can look it up the same way
     * [ComplianceAlertOpenedEvent] and the alert itself are keyed.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun appendCampaignReported(
        associationId: UUID,
        campaignId: UUID,
        message: String,
        reporterEmail: String?,
    ): ComplianceAuditLog = append(
        eventType = CAMPAIGN_REPORTED,
        subjectType = ComplianceAuditSubjectType.ASSOCIATION,
        subjectId = associationId,
        payload = mapOf(
            "campaignId" to campaignId.toString(),
            "message" to message,
            "reporterEmail" to reporterEmail,
        ),
    )

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun appendSyncFailure(reason: String, lastSuccessAt: Instant?): ComplianceAuditLog = append(
        eventType = SANCTION_SYNC_FAILURE,
        subjectType = ComplianceAuditSubjectType.SYSTEM,
        payload = mapOf(
            "reason" to reason,
            "lastSuccessAt" to lastSuccessAt?.toString(),
        ),
    )

    // -----------------------------------------------------------------------------------------

    /**
     * Re-reads the whole chain in `sequence_no` order, recomputes every `row_hash` from each
     * row's own stored fields, and checks both that the recomputed hash matches the stored one
     * and that the stored `prev_hash` matches the actual previous row's hash.
     *
     * @return the `sequence_no` of the first row where either check fails, or null if the entire
     *   chain is intact.
     */
    @Transactional(readOnly = true)
    fun verifyChain(): Long? {
        var expectedPrevHash: String? = null
        for (row in repo.findAllByOrderBySequenceNoAsc()) {
            val recomputed = sha256Hex(
                canonicalBytes(row.sequenceNo, row.eventType, row.subjectType, row.subjectId, row.payload, row.actorUserId, row.occurredAt, row.prevHash),
            )
            if (row.prevHash != expectedPrevHash || recomputed != row.rowHash) {
                return row.sequenceNo
            }
            expectedPrevHash = row.rowHash
        }
        return null
    }

    private fun canonicalBytes(
        sequenceNo: Long,
        eventType: String,
        subjectType: ComplianceAuditSubjectType,
        subjectId: UUID?,
        payload: String,
        actorUserId: UUID?,
        occurredAt: Instant,
        prevHash: String?,
    ): ByteArray {
        val fields = listOf(
            sequenceNo.toString(),
            eventType,
            subjectType.name,
            subjectId?.toString() ?: "",
            payload,
            actorUserId?.toString() ?: "",
            occurredAt.toString(),
            prevHash ?: "",
        )
        val builder = StringBuilder()
        for (field in fields) {
            val bytes = field.toByteArray(Charsets.UTF_8)
            builder.append(bytes.size).append(':').append(field)
        }
        return builder.toString().toByteArray(Charsets.UTF_8)
    }

    private fun sha256Hex(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
}
