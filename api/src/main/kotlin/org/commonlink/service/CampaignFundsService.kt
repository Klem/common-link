package org.commonlink.service

import org.commonlink.config.PaymentsProperties
import org.commonlink.dto.CampaignCashOutDto
import org.commonlink.dto.CampaignFundsResponse
import org.commonlink.dto.CampaignManualReleaseDto
import org.commonlink.entity.FundsState
import org.commonlink.entity.HelloAssoDonationFunds
import org.commonlink.entity.PayoutStatus
import org.commonlink.entity.ReceiptConfirmationMode
import org.commonlink.exception.NotFoundException
import org.commonlink.exception.UserNotFoundException
import org.commonlink.repository.AssociationProfileRepository
import org.commonlink.repository.CampaignRepository
import org.commonlink.repository.DonationRepository
import org.commonlink.repository.HelloAssoCashOutSyncRepository
import org.commonlink.repository.HelloAssoDonationFundsRepository
import org.commonlink.repository.PayoutRepository
import org.springframework.stereotype.Service
import java.math.BigDecimal
import java.time.Clock
import java.time.Instant
import java.util.UUID

/**
 * Read model of a campaign's funds for the Payments tab ("Où en sont vos dons"): where its donations stand between
 * the payment provider and the association's bank account, and what a payment can use. Everything is computed here;
 * the front adds nothing.
 *
 * - `raised`: confirmed donations, every provider. A campaign may carry both `mollie:` and `helloasso:` donations
 *   when its association changed provider: each donation counts by its own reference prefix (its tracking row), not by
 *   the association's current [CampaignFundsResponse.provider].
 * - `payoutPreparing`, `inTransit`, `withdrawn`: HelloAsso tracking rows in that [FundsState].
 * - `available`: donations arrived ([DonationRepository.sumAvailableAmountByCampaignId], the base of the payout
 *   balance): every Mollie donation, HelloAsso ones once AVAILABLE, and HelloAsso ones confirmed before the tracking
 *   existed (made available at once, decision T2 = (a)).
 * - `atProvider`: the rest (`raised − withdrawn − payoutPreparing − inTransit − available`), i.e. HelloAsso rows
 *   AT_PROVIDER, plus the unexpected HelloAsso donation confirmed without a trackable payment (never lost, never
 *   available). So the four segments always add up to `raised − withdrawn`.
 * - `disbursable`: [PayoutService.computeAvailableBalance] — the very function of the creation check — clamped at zero.
 * - `cashOuts`: the campaign's donations grouped by HelloAsso payout (withdrawn ones excluded), newest first.
 * - `manualReleases`: donations a curator released before HelloAsso paid them out (no payout id); counted in
 *   `available`, outside `cashOuts`, with no confirmation action.
 * - sync fields: the association's last tracking pass, and when a manual refresh will run a new one.
 */
@Service
class CampaignFundsService(
    private val campaignRepository: CampaignRepository,
    private val associationProfileRepository: AssociationProfileRepository,
    private val donationRepository: DonationRepository,
    private val payoutRepository: PayoutRepository,
    private val fundsRepository: HelloAssoDonationFundsRepository,
    private val syncRepository: HelloAssoCashOutSyncRepository,
    private val payoutService: PayoutService,
    private val cashOutTotalService: HelloAssoCashOutTotalService,
    private val refreshService: HelloAssoCashOutRefreshService,
    private val properties: PaymentsProperties,
) {
    /** Time source; replaced by tests. */
    internal var clock: Clock = Clock.systemUTC()

    /**
     * The funds of [campaignId], for the association of user [userId]. Read only: no HelloAsso call (a payout total
     * not read yet stays `null` until a [refresh] or a receipt confirmation reads it).
     *
     * @throws NotFoundException when the campaign does not exist or belongs to another association.
     */
    fun getFunds(campaignId: UUID, userId: UUID): CampaignFundsResponse {
        val associationId = ownedAssociationId(campaignId, userId)
        return build(campaignId, associationId, nextRefreshAvailableAt(associationId))
    }

    /**
     * Requests a tracking pass for the campaign's association ([HelloAssoCashOutRefreshService.requestRefresh]: at
     * most one per association per interval, guarded in the database), then returns the funds. A request over the
     * limit, or for an association without HelloAsso donation, runs nothing and is not an error. It also reads the
     * campaign's payout totals not known yet (only those), with one access token
     * ([HelloAssoCashOutTotalService.fetchMissing]). Must not run inside a transaction (HelloAsso calls).
     *
     * @throws NotFoundException when the campaign does not exist or belongs to another association.
     */
    fun refresh(campaignId: UUID, userId: UUID): CampaignFundsResponse {
        val associationId = ownedAssociationId(campaignId, userId)
        if (!fundsRepository.existsByAssociationId(associationId)) return build(campaignId, associationId, null)
        val outcome = refreshService.requestRefresh(associationId)
        // Also when the pass itself was limited: the scheduled pass may have found new payouts since.
        cashOutTotalService.fetchMissing(associationId, fundsRepository.findByCampaignId(campaignId).mapNotNull { it.idCashOut })
        return build(campaignId, associationId, outcome.nextRefreshAvailableAt.takeIf { it.isAfter(clock.instant()) })
    }

    private fun build(campaignId: UUID, associationId: UUID, nextRefreshAvailableAt: Instant?): CampaignFundsResponse {
        val provider = associationProfileRepository.findById(associationId).get().paymentProvider
        val rows = fundsRepository.findByCampaignId(campaignId).filter { it.donation.confirmedAt != null }
        fun sumOf(state: FundsState) = rows.filter { it.fundsState == state }.sumAmounts()

        val raised = donationRepository.sumConfirmedAmountByCampaignId(campaignId) ?: BigDecimal.ZERO
        val available = donationRepository.sumAvailableAmountByCampaignId(campaignId) ?: BigDecimal.ZERO
        val preparing = sumOf(FundsState.PAYOUT_PREPARING)
        val inTransit = sumOf(FundsState.IN_TRANSIT)
        val withdrawn = sumOf(FundsState.WITHDRAWN)
        val sync = syncRepository.findById(associationId).orElse(null)

        return CampaignFundsResponse(
            provider = provider,
            raised = raised,
            atProvider = raised - withdrawn - preparing - inTransit - available,
            payoutPreparing = preparing,
            inTransit = inTransit,
            available = available,
            withdrawn = withdrawn,
            paidOut = payoutRepository.sumAmountByCampaignIdAndStatus(campaignId, PayoutStatus.CONFIRMED) ?: BigDecimal.ZERO,
            pendingPayouts = payoutRepository.sumAmountByCampaignIdAndStatus(campaignId, PayoutStatus.PENDING) ?: BigDecimal.ZERO,
            disbursable = payoutService.computeAvailableBalance(campaignId).max(BigDecimal.ZERO),
            cashOuts = cashOuts(associationId, rows),
            manualReleases = rows
                .filter { it.idCashOut == null && it.fundsState == FundsState.AVAILABLE && it.receiptConfirmationMode == ReceiptConfirmationMode.CURATOR_RELEASE }
                .groupBy { it.receiptConfirmedAt!! }
                .map { (releasedAt, released) -> CampaignManualReleaseDto(releasedAt, released.sumAmounts()) }
                .sortedByDescending { it.releasedAt },
            lastSyncAt = sync?.lastSyncAt,
            syncError = sync?.syncError != null,
            syncErrorCode = sync?.syncError,
            nextRefreshAvailableAt = nextRefreshAvailableAt,
        )
    }

    /** Groups the campaign's paid-out donations by HelloAsso payout; a group is AVAILABLE once all its donations are. */
    private fun cashOuts(associationId: UUID, rows: List<HelloAssoDonationFunds>): List<CampaignCashOutDto> =
        rows.filter { it.idCashOut != null && it.fundsState != FundsState.WITHDRAWN }
            .groupBy { it.idCashOut!! }
            .map { (idCashOut, group) ->
                val start = group.mapNotNull { it.cashOutDate ?: it.cashedOutSeenAt }.minOrNull()
                val allAvailable = group.all { it.fundsState == FundsState.AVAILABLE }
                CampaignCashOutDto(
                    idCashOut = idCashOut,
                    cashOutDate = start?.let(BusinessDayCalendar::dayOf),
                    netTotal = cashOutTotalService.storedNetTotal(associationId, idCashOut),
                    campaignAmount = group.sumAmounts(),
                    state = if (allAvailable) FundsState.AVAILABLE else FundsState.IN_TRANSIT,
                    autoAvailableOn = start?.let {
                        BusinessDayCalendar.addBusinessDays(BusinessDayCalendar.dayOf(it), properties.helloasso.cashoutSettlementBusinessDays)
                    },
                    confirmedAt = if (allAvailable) group.mapNotNull { it.receiptConfirmedAt }.maxOrNull() else null,
                    confirmedBy = if (allAvailable) confirmedBy(group) else null,
                )
            }
            .sortedWith(compareByDescending<CampaignCashOutDto> { it.cashOutDate }.thenByDescending { it.idCashOut })

    /** One mode per payout: the association's confirmation first, then a curator's, else the delay. */
    private fun confirmedBy(group: List<HelloAssoDonationFunds>): ReceiptConfirmationMode? {
        val modes = group.mapNotNull { it.receiptConfirmationMode }.toSet()
        return listOf(ReceiptConfirmationMode.MANUAL, ReceiptConfirmationMode.CURATOR_RELEASE, ReceiptConfirmationMode.AUTO)
            .firstOrNull { it in modes }
    }

    /** From when a manual refresh will run a pass; null when it can now, or when nothing is tracked. */
    private fun nextRefreshAvailableAt(associationId: UUID): Instant? {
        if (!fundsRepository.existsByAssociationId(associationId)) return null
        val last = syncRepository.findById(associationId).map { it.lastRefreshRequestedAt }.orElse(null) ?: return null
        return last.plus(properties.helloasso.cashoutRefreshMinInterval).takeIf { it.isAfter(clock.instant()) }
    }

    private fun ownedAssociationId(campaignId: UUID, userId: UUID): UUID {
        val associationId = associationProfileRepository.findByUserId(userId)
            .orElseThrow { UserNotFoundException("Association profile not found for user $userId") }
            .id!!
        val campaign = campaignRepository.findById(campaignId).orElseThrow { NotFoundException("Campaign not found: $campaignId") }
        if (campaign.association.id != associationId) throw NotFoundException("Campaign not found: $campaignId")
        return associationId
    }

    private fun List<HelloAssoDonationFunds>.sumAmounts(): BigDecimal = fold(BigDecimal.ZERO) { acc, row -> acc + row.donation.amount }
}
