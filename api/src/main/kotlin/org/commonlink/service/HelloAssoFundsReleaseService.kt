package org.commonlink.service

import org.commonlink.dto.HelloAssoFundsReleaseResponse
import org.commonlink.dto.HelloAssoReleasableDonationDto
import org.commonlink.dto.HelloAssoReleasableFundsResponse
import org.commonlink.entity.ComplianceAuditSubjectType
import org.commonlink.entity.FundsState
import org.commonlink.entity.HelloAssoFundsReleaseEvidence
import org.commonlink.entity.ReceiptConfirmationMode
import org.commonlink.exception.FundsReleaseNotAllowedException
import org.commonlink.exception.NotFoundException
import org.commonlink.exception.UnprocessableEntityException
import org.commonlink.repository.AssociationProfileRepository
import org.commonlink.repository.HelloAssoConnectionRepository
import org.commonlink.repository.HelloAssoDonationFundsRepository
import org.commonlink.repository.HelloAssoFundsReleaseEvidenceRepository
import org.commonlink.util.UploadedFileValidator
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.multipart.MultipartFile
import java.math.BigDecimal
import java.time.Clock
import java.util.UUID

/**
 * Manual release of HelloAsso donations by a curator (CEO decisions Q4 and Q5): when HelloAsso cannot tell that a
 * payout arrived — HelloAsso incident, payout shown "in transit" although received, HelloAsso link disconnected or
 * broken with donations not paid out yet — a curator makes donations available on the association's bank statement
 * line, copied as text **and** given as a file of that single line (never the whole statement).
 *
 * Only donations still tracked and not available (AT_PROVIDER, PAYOUT_PREPARING, IN_TRANSIT) can be released; never a
 * Mollie donation (it has no tracking row). A released row becomes AVAILABLE, so the tracking pass no longer reads it
 * ([HelloAssoCashOutSyncExecutor] only reads rows not yet AVAILABLE nor WITHDRAWN): a later refund at HelloAsso is
 * then only seen through HelloAsso's notification. The FIFO allocation is not touched; the donation simply becomes
 * available now (`fundsAvailableAt`, never back-dated).
 *
 * The evidence is stored in `helloasso_funds_release_evidence` (retention not defined yet, CEO question Q8, no purge);
 * the compliance journal ([ComplianceAuditLogService.HELLOASSO_FUNDS_MANUALLY_RELEASED]) references it by id and
 * never holds the statement line nor the file.
 */
@Service
class HelloAssoFundsReleaseService(
    private val fundsRepository: HelloAssoDonationFundsRepository,
    private val evidenceRepository: HelloAssoFundsReleaseEvidenceRepository,
    private val associationProfileRepository: AssociationProfileRepository,
    private val connectionRepository: HelloAssoConnectionRepository,
    private val complianceAuditLogService: ComplianceAuditLogService,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    /** Time source; replaced by tests. */
    internal var clock: Clock = Clock.systemUTC()

    companion object {
        /** Maximum length of the release reason. */
        const val REASON_MAX_LENGTH = 2000

        /** Maximum length of the bank statement line copied as text (one line, with its label and amount). */
        const val STATEMENT_LINE_MAX_LENGTH = 500

        /** Maximum number of donations released at once. */
        const val MAX_DONATIONS = 500

        /** Accepted evidence file types: a PDF extract or a picture of the statement line. */
        val ALLOWED_MIME = setOf("application/pdf", "image/jpeg", "image/png")

        private val RELEASABLE = setOf(FundsState.AT_PROVIDER, FundsState.PAYOUT_PREPARING, FundsState.IN_TRANSIT)
    }

    /**
     * The association's HelloAsso donations not available yet, for the curator to choose from, with its HelloAsso
     * link state (a disconnected or broken link is the main reason for a release).
     *
     * @throws NotFoundException when the association does not exist.
     */
    @Transactional(readOnly = true)
    fun listReleasable(associationId: UUID): HelloAssoReleasableFundsResponse {
        requireAssociation(associationId)
        val donations = fundsRepository.findTrackableByAssociationId(associationId).map {
            HelloAssoReleasableDonationDto(
                donationId = it.donationId!!,
                campaignId = it.donation.campaign.id!!,
                campaignName = it.donation.campaign.name,
                amount = it.donation.amount,
                fundsState = it.fundsState,
                cashOutState = it.cashOutState,
                idCashOut = it.idCashOut,
                lastCheckedAt = it.lastCheckedAt,
            )
        }
        return HelloAssoReleasableFundsResponse(connectionRepository.findByAssociationId(associationId)?.state, donations)
    }

    /**
     * Releases [donationIds] of [associationId] on behalf of curator [curatorUserId], in one transaction: evidence
     * stored, rows locked in donation id order then made AVAILABLE ([ReceiptConfirmationMode.CURATOR_RELEASE]),
     * `fundsAvailableAt = now` on each donation, one journal entry.
     *
     * @throws UnprocessableEntityException when the reason or the statement line is blank or too long, no donation
     *   is given, or the file is missing, too big (10 MB) or not a real PDF / JPEG / PNG.
     * @throws NotFoundException when the association does not exist, or a donation is not a tracked HelloAsso
     *   donation of this association (no existence leak).
     * @throws FundsReleaseNotAllowedException when a donation is already available or withdrawn. Nothing is changed.
     */
    @Transactional
    fun release(
        associationId: UUID,
        donationIds: List<UUID>,
        reason: String?,
        statementLine: String?,
        file: MultipartFile?,
        curatorUserId: UUID,
    ): HelloAssoFundsReleaseResponse {
        val cleanReason = requireText(reason, "reason", REASON_MAX_LENGTH)
        val cleanLine = requireText(statementLine, "statementLine", STATEMENT_LINE_MAX_LENGTH)
        val ids = donationIds.distinct()
        if (ids.isEmpty()) throw UnprocessableEntityException("At least one donation is required")
        if (ids.size > MAX_DONATIONS) throw UnprocessableEntityException("At most $MAX_DONATIONS donations per release")
        if (file == null || file.isEmpty) throw UnprocessableEntityException("The bank statement line file is required")
        UploadedFileValidator.validate(file, ALLOWED_MIME)
        requireAssociation(associationId)

        val rows = fundsRepository.lockAllForUpdate(ids)
        if (rows.size != ids.size || rows.any { it.donation.campaign.association.id != associationId }) {
            throw NotFoundException("Donation not found for this association")
        }
        val notReleasable = rows.filter { it.fundsState !in RELEASABLE }
        if (notReleasable.isNotEmpty()) {
            throw FundsReleaseNotAllowedException(
                "Donations already available or withdrawn: ${notReleasable.joinToString { it.donationId.toString() }}",
            )
        }

        val evidence = evidenceRepository.save(
            HelloAssoFundsReleaseEvidence(
                associationId = associationId,
                reason = cleanReason,
                statementLine = cleanLine,
                fileName = (file.originalFilename ?: file.name).take(255),
                contentType = file.contentType!!,
                content = file.bytes,
                createdBy = curatorUserId,
            ),
        )
        // PostgreSQL keeps microseconds (rounded): the instant returned is the one stored.
        val now = clock.instant().truncatedTo(java.time.temporal.ChronoUnit.MICROS)
        for (row in rows) {
            row.fundsState = FundsState.AVAILABLE
            row.receiptConfirmedAt = now
            row.receiptConfirmationMode = ReceiptConfirmationMode.CURATOR_RELEASE
            row.receiptConfirmedBy = curatorUserId
            row.updatedAt = now
            row.donation.fundsAvailableAt = now
            fundsRepository.save(row)
        }
        val released = rows.map { it.donationId!! }
        val amount = rows.fold(BigDecimal.ZERO) { sum, row -> sum + row.donation.amount }
        complianceAuditLogService.append(
            eventType = ComplianceAuditLogService.HELLOASSO_FUNDS_MANUALLY_RELEASED,
            subjectType = ComplianceAuditSubjectType.ASSOCIATION,
            payload = mapOf(
                "associationId" to associationId.toString(),
                "donationIds" to released.map { it.toString() },
                "amount" to amount.toPlainString(),
                "reason" to cleanReason,
                "evidenceId" to evidence.id.toString(),
            ),
            subjectId = associationId,
            actorUserId = curatorUserId,
        )
        log.info("Curator {} released {} HelloAsso donation(s) of association {} (evidence {})", curatorUserId, released.size, associationId, evidence.id)
        return HelloAssoFundsReleaseResponse(evidence.id, released, amount, now)
    }

    private fun requireAssociation(associationId: UUID) {
        if (!associationProfileRepository.existsById(associationId)) throw NotFoundException("Association not found")
    }

    private fun requireText(value: String?, field: String, maxLength: Int): String {
        val text = value?.trim().orEmpty()
        if (text.isEmpty()) throw UnprocessableEntityException("$field is required")
        if (text.length > maxLength) throw UnprocessableEntityException("$field exceeds $maxLength characters")
        return text
    }
}
