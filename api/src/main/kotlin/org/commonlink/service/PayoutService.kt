package org.commonlink.service

import org.commonlink.dto.CreatePayoutRequest
import org.commonlink.dto.PayoutDto
import org.commonlink.dto.PayoutSummaryDto
import org.commonlink.dto.toDto
import org.commonlink.entity.IbanVerificationStatus
import org.commonlink.entity.PayeeIban
import org.commonlink.entity.Payout
import org.commonlink.entity.PayoutBlockingReason
import org.commonlink.entity.PayoutStatus
import org.commonlink.exception.ConflictException
import org.commonlink.exception.NotFoundException
import org.commonlink.exception.UserNotFoundException
import org.commonlink.repository.AssociationProfileRepository
import org.commonlink.repository.CampaignRepository
import org.commonlink.repository.DonationRepository
import org.commonlink.repository.PayeeIbanRepository
import org.commonlink.repository.PayeeRepository
import org.commonlink.repository.PayoutRepository
import org.slf4j.LoggerFactory
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service
import java.math.BigDecimal
import java.util.UUID

/**
 * Business logic for campaign outgoing payments (payouts).
 *
 * Validation rules mirrored in the frontend (Step 5):
 * - amount > 0 (enforced by [CreatePayoutRequest] @DecimalMin)
 * - payee IBAN belongs to the requested payee
 * - campaign belongs to the requesting association
 * - only PENDING payouts can be confirmed
 * - no active [PayoutBlockingReason] (IBAN unverified, insufficient balance, description too short) — see [computeBlockingReasons]
 */
@Service
class PayoutService(
    private val payoutRepository: PayoutRepository,
    private val campaignRepository: CampaignRepository,
    private val associationProfileRepository: AssociationProfileRepository,
    private val payeeRepository: PayeeRepository,
    private val payeeIbanRepository: PayeeIbanRepository,
    private val donationRepository: DonationRepository,
    private val confirmer: PayoutConfirmer,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    /**
     * Creates a new PENDING payout for [campaignId].
     *
     * @throws NotFoundException if campaign, payee, or IBAN do not belong to [associationId].
     * @throws IllegalArgumentException if the IBAN does not belong to the requested payee.
     * @throws ConflictException if a [PayoutBlockingReason] applies (unverified IBAN, insufficient balance).
     */
    fun create(campaignId: UUID, request: CreatePayoutRequest, userId: UUID): PayoutDto {
        val associationId = resolveAssociationId(userId)
        val campaign = campaignRepository.findById(campaignId)
            .orElseThrow { NotFoundException("Campaign not found: $campaignId") }
        if (campaign.association.id != associationId) throw NotFoundException("Campaign not found: $campaignId")

        val payee = payeeRepository.findById(request.payeeId!!)
            .orElseThrow { NotFoundException("Payee not found: ${request.payeeId}") }

        val payeeIban = payeeIbanRepository.findById(request.payeeIbanId!!)
            .orElseThrow { NotFoundException("IBAN not found: ${request.payeeIbanId}") }
        if (payeeIban.payee.id != payee.id) throw NotFoundException("IBAN ${request.payeeIbanId} does not belong to payee ${request.payeeId}")

        val blockingReasons = blockingReasonsFor(campaignId, payeeIban, request.amount!!, request.label!!)
        if (blockingReasons.isNotEmpty()) {
            throw ConflictException("Payout blocked: ${blockingReasons.joinToString()}")
        }

        val payout = payoutRepository.save(
            Payout(
                campaign        = campaign,
                payee           = payee,
                payeeIbanId     = payeeIban.id!!,
                payeeIbanValue  = payeeIban.iban,
                amount          = request.amount,
                kind            = request.kind!!,
                typeCode        = request.typeCode!!,
                label           = request.label!!,
            )
        )
        log.info("Payout {} created (PENDING) for campaign {}", payout.id, campaignId)
        return payout.toDto()
    }

    /**
     * Confirms a PENDING payout, setting it to CONFIRMED and enqueuing an on-chain job.
     *
     * @throws NotFoundException if payout or campaign cannot be found for [associationId].
     * @throws ConflictException if the payout is not in PENDING status.
     */
    fun confirm(campaignId: UUID, payoutId: UUID, userId: UUID): PayoutDto {
        val associationId = resolveAssociationId(userId)
        val payout = payoutRepository.findByCampaignIdAndIdAndCampaignAssociationId(
            campaignId, payoutId, associationId
        ) ?: throw NotFoundException("Payout not found: $payoutId")

        if (payout.status != PayoutStatus.PENDING) {
            throw ConflictException("Payout $payoutId is already ${payout.status}")
        }

        return confirmer.confirmAndEnqueue(payout).toDto()
    }

    /**
     * Returns a paginated list of payouts for [campaignId], ordered by creation date descending.
     *
     * @throws NotFoundException if campaign does not belong to [associationId].
     */
    fun list(campaignId: UUID, userId: UUID, pageable: Pageable): Page<PayoutDto> {
        val associationId = resolveAssociationId(userId)
        assertCampaignOwnership(campaignId, associationId)
        return payoutRepository.findByCampaignIdOrderByCreatedAtDesc(campaignId, pageable).map { it.toDto() }
    }

    /**
     * Returns a single payout.
     *
     * @throws NotFoundException if payout cannot be found for [campaignId] / [associationId].
     */
    fun get(campaignId: UUID, payoutId: UUID, userId: UUID): PayoutDto {
        val associationId = resolveAssociationId(userId)
        return (payoutRepository.findByCampaignIdAndIdAndCampaignAssociationId(campaignId, payoutId, associationId)
            ?: throw NotFoundException("Payout not found: $payoutId")).toDto()
    }

    /**
     * Returns aggregated KPIs for the Payments tab.
     *
     * @throws NotFoundException if campaign does not belong to [associationId].
     */
    fun getSummary(campaignId: UUID, userId: UUID): PayoutSummaryDto {
        val associationId = resolveAssociationId(userId)
        assertCampaignOwnership(campaignId, associationId)

        val confirmedAmount = payoutRepository.sumAmountByCampaignIdAndStatus(campaignId, PayoutStatus.CONFIRMED)
            ?: BigDecimal.ZERO
        val pendingAmount   = payoutRepository.sumAmountByCampaignIdAndStatus(campaignId, PayoutStatus.PENDING)
            ?: BigDecimal.ZERO
        val txTotal         = payoutRepository.countByCampaignId(campaignId)
        val txConfirmed     = payoutRepository.countByCampaignIdAndStatus(campaignId, PayoutStatus.CONFIRMED)

        return PayoutSummaryDto(
            confirmedAmount  = confirmedAmount,
            confirmedCount   = txConfirmed,
            pendingAmount    = pendingAmount,
            txTotal          = txTotal,
            txConfirmed      = txConfirmed,
            availableBalance = computeAvailableBalance(campaignId),
        )
    }

    /**
     * Returns the [PayoutBlockingReason]s currently preventing [amount] from being paid out of
     * [payeeIbanId] on [campaignId] — used by the frontend to explain a disabled submit button.
     *
     * @throws NotFoundException if campaign or IBAN cannot be found for [userId]'s association.
     */
    fun computeBlockingReasons(campaignId: UUID, payeeIbanId: UUID, amount: BigDecimal, label: String, userId: UUID): List<PayoutBlockingReason> {
        val associationId = resolveAssociationId(userId)
        assertCampaignOwnership(campaignId, associationId)
        val payeeIban = payeeIbanRepository.findById(payeeIbanId)
            .orElseThrow { NotFoundException("IBAN not found: $payeeIbanId") }
        return blockingReasonsFor(campaignId, payeeIban, amount, label)
    }

    private fun blockingReasonsFor(campaignId: UUID, payeeIban: PayeeIban, amount: BigDecimal, label: String): List<PayoutBlockingReason> {
        val reasons = mutableListOf<PayoutBlockingReason>()
        if (payeeIban.status != IbanVerificationStatus.VERIFIED) {
            reasons += PayoutBlockingReason.IBAN_NOT_VERIFIED
        }
        if (amount > computeAvailableBalance(campaignId)) {
            reasons += PayoutBlockingReason.INSUFFICIENT_BALANCE
        }
        if (label.trim().length < 16) {
            reasons += PayoutBlockingReason.DESCRIPTION_TOO_SHORT
        }
        return reasons
    }

    /** Available funds = total confirmed donations - confirmed payouts, for [campaignId]. */
    private fun computeAvailableBalance(campaignId: UUID): BigDecimal {
        val confirmedAmount = payoutRepository.sumAmountByCampaignIdAndStatus(campaignId, PayoutStatus.CONFIRMED)
            ?: BigDecimal.ZERO
        val totalRaised = donationRepository.sumConfirmedAmountByCampaignId(campaignId) ?: BigDecimal.ZERO
        return totalRaised - confirmedAmount
    }

    private fun assertCampaignOwnership(campaignId: UUID, associationId: UUID) {
        val campaign = campaignRepository.findById(campaignId)
            .orElseThrow { NotFoundException("Campaign not found: $campaignId") }
        if (campaign.association.id != associationId) throw NotFoundException("Campaign not found: $campaignId")
    }

    private fun resolveAssociationId(userId: UUID): UUID =
        associationProfileRepository.findByUserId(userId)
            .orElseThrow { UserNotFoundException("Association profile not found for user $userId") }
            .id!!
}
