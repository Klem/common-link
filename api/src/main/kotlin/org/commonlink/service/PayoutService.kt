package org.commonlink.service

import org.commonlink.dto.CreatePayoutRequest
import org.commonlink.dto.PayoutDto
import org.commonlink.dto.PayoutSummaryDto
import org.commonlink.dto.toDto
import org.commonlink.entity.Payout
import org.commonlink.entity.PayoutStatus
import org.commonlink.exception.ConflictException
import org.commonlink.exception.NotFoundException
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
 */
@Service
class PayoutService(
    private val payoutRepository: PayoutRepository,
    private val campaignRepository: CampaignRepository,
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
     */
    fun create(campaignId: UUID, request: CreatePayoutRequest, associationId: UUID): PayoutDto {
        val campaign = campaignRepository.findById(campaignId)
            .orElseThrow { NotFoundException("Campaign not found: $campaignId") }
        if (campaign.association.id != associationId) throw NotFoundException("Campaign not found: $campaignId")

        val payee = payeeRepository.findById(request.payeeId!!)
            .orElseThrow { NotFoundException("Payee not found: ${request.payeeId}") }

        val payeeIban = payeeIbanRepository.findById(request.payeeIbanId!!)
            .orElseThrow { NotFoundException("IBAN not found: ${request.payeeIbanId}") }
        if (payeeIban.payee.id != payee.id) throw NotFoundException("IBAN ${request.payeeIbanId} does not belong to payee ${request.payeeId}")

        val payout = payoutRepository.save(
            Payout(
                campaign  = campaign,
                payee     = payee,
                payeeIban = payeeIban,
                amount    = request.amount!!,
                kind      = request.kind!!,
                typeCode  = request.typeCode!!,
                label     = request.label!!,
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
    fun confirm(campaignId: UUID, payoutId: UUID, associationId: UUID): PayoutDto {
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
    fun list(campaignId: UUID, associationId: UUID, pageable: Pageable): Page<PayoutDto> {
        assertCampaignOwnership(campaignId, associationId)
        return payoutRepository.findByCampaignIdOrderByCreatedAtDesc(campaignId, pageable).map { it.toDto() }
    }

    /**
     * Returns a single payout.
     *
     * @throws NotFoundException if payout cannot be found for [campaignId] / [associationId].
     */
    fun get(campaignId: UUID, payoutId: UUID, associationId: UUID): PayoutDto =
        (payoutRepository.findByCampaignIdAndIdAndCampaignAssociationId(campaignId, payoutId, associationId)
            ?: throw NotFoundException("Payout not found: $payoutId")).toDto()

    /**
     * Returns aggregated KPIs for the Payments tab.
     *
     * @throws NotFoundException if campaign does not belong to [associationId].
     */
    fun getSummary(campaignId: UUID, associationId: UUID): PayoutSummaryDto {
        assertCampaignOwnership(campaignId, associationId)

        val confirmedAmount = payoutRepository.sumAmountByCampaignIdAndStatus(campaignId, PayoutStatus.CONFIRMED)
            ?: BigDecimal.ZERO
        val pendingAmount   = payoutRepository.sumAmountByCampaignIdAndStatus(campaignId, PayoutStatus.PENDING)
            ?: BigDecimal.ZERO
        val txTotal         = payoutRepository.countByCampaignId(campaignId)
        val txConfirmed     = payoutRepository.countByCampaignIdAndStatus(campaignId, PayoutStatus.CONFIRMED)
        val totalRaised     = donationRepository.sumConfirmedAmountByCampaignId(campaignId) ?: BigDecimal.ZERO

        return PayoutSummaryDto(
            confirmedAmount  = confirmedAmount,
            confirmedCount   = txConfirmed,
            pendingAmount    = pendingAmount,
            txTotal          = txTotal,
            txConfirmed      = txConfirmed,
            availableBalance = totalRaised - confirmedAmount,
        )
    }

    private fun assertCampaignOwnership(campaignId: UUID, associationId: UUID) {
        val campaign = campaignRepository.findById(campaignId)
            .orElseThrow { NotFoundException("Campaign not found: $campaignId") }
        if (campaign.association.id != associationId) throw NotFoundException("Campaign not found: $campaignId")
    }
}
