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
import org.springframework.beans.factory.annotation.Value
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
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
 * - no active [PayoutBlockingReason] (IBAN unverified or disabled, insufficient balance, description too short) — see [computeBlockingReasons]
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
    private val bridgeInitiation: BridgePaymentInitiationService,
    @Value("\${app.frontend-url:http://localhost:3000}") private val frontendUrl: String,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    /**
     * Creates a new PENDING payout for [campaignId].
     *
     * @throws NotFoundException if campaign, payee, or IBAN do not belong to the user's association.
     * @throws IllegalArgumentException if the IBAN does not belong to the requested payee.
     * @throws ConflictException if a [PayoutBlockingReason] applies (unverified IBAN, insufficient
     *         balance, description too short).
     */
    @Transactional
    fun create(campaignId: UUID, request: CreatePayoutRequest, userId: UUID): PayoutDto {
        val associationId = resolveAssociationId(userId)
        // Lock the campaign row for the duration of the transaction so concurrent create/confirm
        // requests serialize and each sees the balance already reserved by the others (H2 TOCTOU).
        val campaign = campaignRepository.findByIdForUpdate(campaignId)
            ?: throw NotFoundException("Campaign not found: $campaignId")
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
     * Confirms a PENDING payout by initiating the real SEPA transfer at Bridge.
     *
     * The association is the debtor: the returned [PayoutDto.bridgeCheckoutUrl] must be opened so
     * it can authorise the transfer with its own bank, after which the funds move directly to the
     * payee's IBAN. The payout therefore stays **PENDING** here — it only becomes CONFIRMED, and
     * the on-chain attestation only gets published, once Bridge reports the transfer settled
     * through [BridgeWebhookService].
     *
     * Deliberately **not** `@Transactional`: the Bridge call must not run inside a database
     * transaction. The phases and the reasoning behind their order are documented on
     * [PayoutConfirmer]; this method only sequences them.
     *
     * @param campaignId Campaign owning the payout.
     * @param payoutId Payout to confirm.
     * @param userId Authenticated association user.
     * @return the payout, carrying the bank-authorisation URL.
     * @throws NotFoundException if payout or campaign cannot be found for the user's association.
     * @throws ConflictException if the payout is not PENDING, its IBAN is no longer VERIFIED, or
     *         confirming it would exceed the confirmable balance.
     * @throws org.commonlink.exception.BadGatewayException if Bridge is unreachable or refuses the
     *         initiation — nothing has been debited, the reservation is released and the payout is
     *         left PENDING so it can be confirmed again.
     */
    fun confirm(campaignId: UUID, payoutId: UUID, userId: UUID): PayoutDto {
        val associationId = resolveAssociationId(userId)
        assertCampaignOwnership(campaignId, associationId)

        // Phase 1 — validate and read what the initiation needs; the transaction ends here.
        val context = confirmer.loadForConfirm(campaignId, payoutId, associationId)

        // Phase 2 — engage the amount under the campaign lock: last point at which nothing is initiated.
        confirmer.reserve(campaignId, payoutId)

        // Phase 3 — create the initiation, then record the outcome.
        val link = try {
            bridgeInitiation.createPaymentLink(
                payoutId = payoutId,
                payerName = context.payerName,
                payerReference = context.payerReference,
                payeeName = context.payeeName,
                payeeIban = context.payeeIban,
                amount = context.amount,
                label = context.label,
                senderIban = null,
                callbackUrl = bridgeCallbackUrl(campaignId),
            )
        } catch (ex: Exception) {
            // Release the reservation rather than fail the payout: with Open Banking initiation
            // nothing can be debited until the association authorises the transfer at its bank, and
            // that requires the authorisation URL — which this failure means we never obtained. The
            // payout stays PENDING so the association can retry once Bridge answers again; FAILED
            // would be terminal, because loadForConfirm only accepts PENDING.
            confirmer.releaseReservation(payoutId, ex.message ?: "Bridge initiation failed")
            throw ex
        }

        return confirmer.attachPaymentLink(payoutId, link).toDto()
    }

    /** Where Bridge returns the association once the bank flow is over. */
    private fun bridgeCallbackUrl(campaignId: UUID) =
        "$frontendUrl/dashboard/association/campaigns/$campaignId?tab=payments"

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

    /**
     * No Bridge-side precondition is checked here: the beneficiary is passed inline to Bridge at
     * initiation time (dynamic beneficiary), so a VERIFIED IBAN is on its own a valid transfer
     * destination — nothing has to be pre-registered anywhere.
     */
    private fun blockingReasonsFor(
        campaignId: UUID,
        payeeIban: PayeeIban,
        amount: BigDecimal,
        label: String,
    ): List<PayoutBlockingReason> {
        val reasons = mutableListOf<PayoutBlockingReason>()
        if (payeeIban.status != IbanVerificationStatus.VERIFIED || !payeeIban.active) {
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

    /**
     * Available funds still allocatable = confirmed donations − CONFIRMED payouts − **PENDING** payouts.
     * PENDING payouts are reserved (H2): without this, N pending payouts each ≤ balance could be created
     * and later confirmed to collectively exceed the funds raised.
     */
    private fun computeAvailableBalance(campaignId: UUID): BigDecimal {
        val pending = payoutRepository.sumAmountByCampaignIdAndStatus(campaignId, PayoutStatus.PENDING)
            ?: BigDecimal.ZERO
        return computeConfirmableBalance(campaignId) - pending
    }

    /**
     * Funds confirmable right now = confirmed donations − already-CONFIRMED payouts (PENDING excluded).
     * Used at confirm time: the payout being confirmed is itself still PENDING, so it must be checked
     * against the balance net of *other* CONFIRMED payouts only.
     */
    private fun computeConfirmableBalance(campaignId: UUID): BigDecimal {
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
