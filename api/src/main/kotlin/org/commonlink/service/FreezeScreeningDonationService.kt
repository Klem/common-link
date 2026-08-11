package org.commonlink.service

import org.commonlink.config.SanctionsProperties
import org.commonlink.entity.ComplianceAuditSubjectType
import org.commonlink.entity.SanctionedNature
import org.commonlink.repository.SanctionedEntityRepository
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
 * ### Log hygiene
 * Donor names are never written to application logs. Per-screening traces belong exclusively in the
 * compliance audit journal ([ComplianceAuditLogService]).
 *
 * ### Alert extension point
 * On a HIT, [FreezeHitAlertPort.onFreezeHit] is called with [FreezeHitRole.DONOR] before returning
 * [ScreeningOutcome.HIT]. [NoOpFreezeHitAlertPort] is active until prompt 16.
 */
@Service
class FreezeScreeningDonationService(
    private val screeningService: SanctionScreeningService,
    private val auditLogService: ComplianceAuditLogService,
    private val sanctionedEntityRepository: SanctionedEntityRepository,
    private val props: SanctionsProperties,
    private val alertPort: FreezeHitAlertPort,
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
            return ScreeningOutcome.UNAVAILABLE
        }

        val rawMatches = screeningService.screen(
            name = identity.fullName,
            nature = SanctionedNature.PHYSICAL_PERSON,
        )
        val confirmedMatches = rawMatches.filter { isConfirmedHit(it, identity.birthDate) }

        if (confirmedMatches.isNotEmpty()) {
            auditLogService.appendFreezeScreeningHit(
                subjectType = ComplianceAuditSubjectType.DONOR,
                subjectId = donorProfileId,
                registryPublicationDate = publicationDate,
                scoreThreshold = props.scoreThreshold,
                matchCount = confirmedMatches.size,
                topScore = confirmedMatches.first().score,
                nature = SanctionedNature.PHYSICAL_PERSON.name,
            )
            try {
                alertPort.onFreezeHit(
                    associationId,
                    listOf(FreezeHitTarget(role = FreezeHitRole.DONOR, subjectId = donorProfileId)),
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
     * A match is discarded (false positive excluded by DOB) only when **both** the register entry
     * and the donor supply a parseable year and they differ. Conservative: when the register has no
     * DOB or an unparseable one, the match is retained for human review.
     */
    private fun isConfirmedHit(match: ScreeningMatch, donorBirthDate: LocalDate): Boolean {
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
