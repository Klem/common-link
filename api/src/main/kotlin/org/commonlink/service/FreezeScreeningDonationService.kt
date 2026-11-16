package org.commonlink.service

import org.commonlink.config.SanctionsProperties
import org.commonlink.entity.ComplianceAlertOrigin
import org.commonlink.entity.ComplianceAlertSubjectType
import org.commonlink.entity.ComplianceAuditSubjectType
import org.commonlink.entity.SanctionedNature
import org.commonlink.repository.SanctionedEntityRepository
import org.commonlink.util.NameNormalizer
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.LocalDate
import java.util.UUID

/**
 * Mandatory asset-freeze screening of a donor before any Mollie payment is created
 * (LCB-FT, art. L.561-5 CMF).
 *
 * A non-[ScreeningOutcome.CLEAR] result — including [ScreeningOutcome.UNAVAILABLE] — must cause the
 * caller to refuse the donation without creating a Mollie payment or persisting a donation row.
 * An uncompletable check is not a favorable check.
 *
 * ### DOB disambiguation
 * The asset-freeze register carries only a **partial** date of birth (format "DD/MM/YYYY",
 * "MM/YYYY", or "YYYY"). [org.commonlink.service.SanctionScreeningService.screen] does not filter
 * on DOB — it returns all name matches above the threshold regardless of date. This service applies
 * a conservative post-filter: a match is **discarded** only when both the register entry and the
 * donor supply a parseable year component and they differ. When the register entry has no DOB, or
 * the DOB is not parseable, the match is retained (false positive risk &lt; false negative risk).
 *
 * The donor's DOB is facultative — the widget form no longer requires it. When it is absent the
 * post-filter is inert: **every** name match above the threshold is retained, so a donor who is a
 * mere homonym of a designated person sees their donation refused until the compliance officer
 * rules the correspondence a false positive. Never the reverse: an absent DOB can only make the
 * screening stricter.
 *
 * ### Prior rulings
 * The screening runs on every donation attempt. When every correspondence it finds was already
 * ruled a false positive for this donor profile ([FreezeClearanceService.forDonor]), the hit is
 * dismissed — journalled as `FREEZE_SCREENING_HIT_CLEARED`, never silent — no alert is raised and
 * the outcome is [ScreeningOutcome.CLEAR]. A single uncovered correspondence (another name under the
 * same e-mail, a register update), or any donor freeze alert still open on the profile, keeps the
 * hit. Without this the officer's ruling had no effect: the homonym stayed refused forever and each
 * attempt opened a new alert.
 *
 * ### Log hygiene
 * Donor names are never written to application logs. Per-screening traces belong exclusively in the
 * compliance audit journal ([ComplianceAuditLogService]).
 *
 * ### Alerts
 * On a HIT, [FreezeHitAlertPort.onFreezeHit] is called with [FreezeHitRole.DONOR] before returning
 * [ScreeningOutcome.HIT]. [FreezeHitAlertAdapter] raises a [org.commonlink.entity.ComplianceAlert].
 */
@Service
class FreezeScreeningDonationService(
    private val screeningService: SanctionScreeningService,
    private val auditLogService: ComplianceAuditLogService,
    private val sanctionedEntityRepository: SanctionedEntityRepository,
    private val evidenceRecorder: FreezeScreeningEvidenceRecorder,
    private val props: SanctionsProperties,
    private val alertPort: FreezeHitAlertPort,
    private val clearanceService: FreezeClearanceService,
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    /**
     * Screens the donor against the asset-freeze register and returns the outcome.
     *
     * Always returns — never throws. Any unexpected failure is caught, journalled as
     * [ScreeningOutcome.UNAVAILABLE], and returned to the caller.
     *
     * @param associationId  UUID of the association whose widget is being used (for the alert port).
     * @param donorProfileId UUID of the [org.commonlink.entity.DonorProfile]; used as subject id
     *   in the audit log. No donation exists at this point — [ComplianceAuditSubjectType.DONOR] is
     *   the correct subject type, not DONATION.
     * @param identity       Frozen identity snapshot built from the widget request.
     */
    fun runFreezeCheck(
        associationId: UUID,
        donorProfileId: UUID,
        identity: DonorIdentitySnapshot,
    ): ScreeningOutcome {
        return try {
            doFreezeCheck(associationId, donorProfileId, identity)
        } catch (e: Exception) {
            logger.error(
                "Donor freeze screening failed unexpectedly for donorProfile {}: {}",
                donorProfileId, e.javaClass.simpleName,
            )
            try {
                auditLogService.appendFreezeScreeningUnavailable(
                    subjectType = ComplianceAuditSubjectType.DONOR,
                    subjectId = donorProfileId,
                    reason = e.javaClass.simpleName,
                )
            } catch (logEx: Exception) {
                logger.error(
                    "Failed to record donor freeze screening failure in audit log for donorProfile {}: {}",
                    donorProfileId, logEx.javaClass.simpleName,
                )
            }
            raiseUnavailableAlert(donorProfileId, e.javaClass.simpleName)
            ScreeningOutcome.UNAVAILABLE
        }
    }

    // ── Private ──────────────────────────────────────────────────────────────────────────────────

    private fun doFreezeCheck(
        associationId: UUID,
        donorProfileId: UUID,
        identity: DonorIdentitySnapshot,
    ): ScreeningOutcome {
        val publicationDate = sanctionedEntityRepository.findMaxPublicationDate()
        if (publicationDate == null) {
            auditLogService.appendFreezeScreeningUnavailable(
                subjectType = ComplianceAuditSubjectType.DONOR,
                subjectId = donorProfileId,
                reason = "register empty — ingestion not yet run",
            )
            raiseUnavailableAlert(donorProfileId, "register empty — ingestion not yet run")
            return ScreeningOutcome.UNAVAILABLE
        }

        val rawMatches = screeningService.screen(
            name = identity.fullName,
            nature = SanctionedNature.PHYSICAL_PERSON,
        )
        val confirmedMatches = rawMatches.filter { isConfirmedHit(it, identity.birthDate) }

        if (confirmedMatches.isNotEmpty()) {
            // A prior false-positive ruling on every correspondence found lifts the hit.
            if (dismissIfCleared(donorProfileId, identity.fullName, confirmedMatches, publicationDate)) {
                return ScreeningOutcome.CLEAR
            }
            val seqNo = auditLogService.appendFreezeScreeningHit(
                subjectType = ComplianceAuditSubjectType.DONOR,
                subjectId = donorProfileId,
                registryPublicationDate = publicationDate,
                scoreThreshold = props.scoreThreshold,
                matchCount = confirmedMatches.size,
                topScore = confirmedMatches.first().score,
                nature = SanctionedNature.PHYSICAL_PERSON.name,
            ).sequenceNo
            recordMatches(seqNo, donorProfileId, identity.fullName, confirmedMatches, publicationDate)
            try {
                alertPort.onFreezeHit(
                    associationId,
                    listOf(
                        FreezeHitTarget(
                            role = FreezeHitRole.DONOR,
                            subjectId = donorProfileId,
                            auditLogSeqRef = seqNo,
                        ),
                    ),
                )
            } catch (e: Exception) {
                logger.error(
                    "Freeze hit alert handler failed for donor in association {}: {} — outcome remains HIT",
                    associationId, e.javaClass.simpleName,
                )
            }
            return ScreeningOutcome.HIT
        }

        auditLogService.appendFreezeScreeningClear(
            subjectType = ComplianceAuditSubjectType.DONOR,
            subjectId = donorProfileId,
            registryPublicationDate = publicationDate,
            scoreThreshold = props.scoreThreshold,
            nature = SanctionedNature.PHYSICAL_PERSON.name,
        )
        return ScreeningOutcome.CLEAR
    }

    /**
     * Dismisses the hit when every correspondence found was already ruled a false positive for
     * this donor profile, and journals the dismissal. Returns false — the hit stands — otherwise.
     *
     * The pairs are built exactly as [FreezeScreeningEvidenceRecorder] stores them (normalized
     * screened name, register id), or a clearance could never match. A failure reading the
     * clearance propagates to [runFreezeCheck], which turns it into [ScreeningOutcome.UNAVAILABLE]:
     * an unreadable ruling never lets a payment through.
     */
    private fun dismissIfCleared(
        donorProfileId: UUID,
        screenedName: String,
        matches: List<ScreeningMatch>,
        publicationDate: LocalDate,
    ): Boolean {
        val normalized = NameNormalizer.normalize(screenedName)
        val found = matches.map { ClearedPair(normalized, it.idRegistre) }
        val clearance = clearanceService.forDonor(donorProfileId)
        if (!clearance.covers(found)) return false

        auditLogService.appendFreezeScreeningHitCleared(
            subjectType = ComplianceAuditSubjectType.DONOR,
            subjectId = donorProfileId,
            registryPublicationDate = publicationDate,
            scoreThreshold = props.scoreThreshold,
            matchCount = matches.size,
            topScore = matches.first().score,
            clearedByAlertIds = clearance.alertIds,
        )
        return true
    }

    /**
     * Surfaces an impossible donor screening to the compliance officer.
     *
     * This is the path that failed silently in production: donor screenings erroring with
     * `InvalidDataAccessApiUsageException` were journalled as UNAVAILABLE and never appeared on
     * any screen, so a mandatory control was being skipped with no one able to notice. See
     * [ComplianceAlertOrigin.SCREENING_UNAVAILABLE]. Never throws.
     */
    private fun raiseUnavailableAlert(donorProfileId: UUID, reason: String) {
        try {
            alertPort.onScreeningUnavailable(
                subjectType = ComplianceAlertSubjectType.DONOR,
                subjectId = donorProfileId,
                reason = reason,
            )
        } catch (e: Exception) {
            logger.error(
                "Screening-unavailable alert handler failed for donorProfile {}: {}",
                donorProfileId, e.javaClass.simpleName,
            )
        }
    }

    /**
     * Persists the decision-grade evidence behind one donor `FREEZE_SCREENING_HIT`.
     *
     * See [FreezeScreeningOnboardingService.recordMatches] for the rationale: the journal holds
     * aggregates only, so without this the officer cannot tell which register entry was matched.
     * `associationId` is deliberately left null — a donor screening has no association context;
     * the alert it raises is keyed on the donor profile.
     *
     * Never throws: evidence capture must not mask the screening outcome, which is what blocks
     * the payment.
     */
    private fun recordMatches(
        auditLogSeqRef: Long,
        donorProfileId: UUID,
        screenedName: String,
        matches: List<ScreeningMatch>,
        publicationDate: LocalDate,
    ) {
        try {
            evidenceRecorder.record(
                auditLogSeqRef = auditLogSeqRef,
                subjectType = ComplianceAuditSubjectType.DONOR,
                subjectId = donorProfileId,
                associationId = null,
                screenedName = screenedName,
                matches = matches,
                publicationDate = publicationDate,
            )
        } catch (e: Exception) {
            logger.error(
                "Failed to persist donor freeze screening evidence for donorProfile {} (seq {}): {}",
                donorProfileId, auditLogSeqRef, e.javaClass.simpleName,
            )
        }
    }

    /**
     * A match is discarded (false positive excluded by DOB) only when **both** the register entry
     * and the donor supply a parseable year and they differ. Conservative: when the register has no
     * DOB or an unparseable one — or when the donor did not supply one, the widget field being
     * facultative — the match is retained for human review.
     */
    private fun isConfirmedHit(match: ScreeningMatch, donorBirthDate: LocalDate?): Boolean {
        if (donorBirthDate == null) return true
        val registerDob = match.dateOfBirth ?: return true
        val registerYear = extractYear(registerDob) ?: return true
        return registerYear == donorBirthDate.year
    }

    /**
     * Extracts the year from a partial DG Trésor date string: "DD/MM/YYYY", "MM/YYYY", or "YYYY".
     * Returns null when the string is blank or its last slash-delimited component is not 4 digits.
     */
    private fun extractYear(dob: String): Int? {
        val trimmed = dob.trim()
        if (trimmed.isBlank()) return null
        val yearStr = trimmed.split("/").last()
        return if (yearStr.length == 4) yearStr.toIntOrNull() else null
    }
}
