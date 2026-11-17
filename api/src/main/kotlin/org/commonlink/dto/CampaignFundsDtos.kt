package org.commonlink.dto

import io.swagger.v3.oas.annotations.media.Schema
import org.commonlink.entity.FundsState
import org.commonlink.entity.ReceiptConfirmationMode
import org.commonlink.payment.PaymentProviderId
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate

/** One HelloAsso payout carrying donations of the campaign. Amounts in euros. */
@Schema(description = "A HelloAsso payout carrying donations of the campaign")
data class CampaignCashOutDto(
    val idCashOut: Long,
    @field:Schema(description = "HelloAsso's payout date (Europe/Paris), or the day the payout was first seen")
    val cashOutDate: LocalDate?,
    @field:Schema(description = "Net amount of the whole payout at HelloAsso (may include other payments); null if unknown")
    val netTotal: BigDecimal?,
    @field:Schema(description = "Part of the payout coming from this campaign's donations (withdrawn donations excluded)")
    val campaignAmount: BigDecimal,
    @field:Schema(description = "IN_TRANSIT while one donation of the campaign is not arrived yet, AVAILABLE once all are")
    val state: FundsState,
    @field:Schema(description = "Day the donations become available on their own (settlement delay)")
    val autoAvailableOn: LocalDate?,
    val confirmedAt: Instant?,
    @field:Schema(description = "MANUAL (association), AUTO (settlement delay) or CURATOR_RELEASE; null while in transit")
    val confirmedBy: ReceiptConfirmationMode?,
)

/** Donations made available by the CommonLink team (curator release) before HelloAsso paid them out. */
@Schema(description = "Donations released by the CommonLink team without a HelloAsso payout")
data class CampaignManualReleaseDto(
    val releasedAt: Instant,
    val campaignAmount: BigDecimal,
)

/**
 * Response of `GET /api/association/campaigns/{campaignId}/funds`: where the campaign's donations stand between the
 * payment provider and the association's bank account, computed server side (the front adds nothing). Amounts in
 * euros. Segments: `atProvider + payoutPreparing + inTransit + available = raised − withdrawn`.
 */
@Schema(description = "Where the campaign's donations stand, and what can be paid with them")
data class CampaignFundsResponse(
    @field:Schema(description = "The association's current payment provider")
    val provider: PaymentProviderId,
    @field:Schema(description = "Confirmed donations, every provider")
    val raised: BigDecimal,
    @field:Schema(description = "HelloAsso donations still held by HelloAsso")
    val atProvider: BigDecimal,
    @field:Schema(description = "HelloAsso donations whose payout HelloAsso is preparing")
    val payoutPreparing: BigDecimal,
    @field:Schema(description = "HelloAsso donations paid out by HelloAsso, not arrived yet")
    val inTransit: BigDecimal,
    @field:Schema(description = "Donations arrived on the association's bank account (every Mollie donation)")
    val available: BigDecimal,
    @field:Schema(description = "HelloAsso donations refunded or contested")
    val withdrawn: BigDecimal,
    @field:Schema(description = "Confirmed payouts")
    val paidOut: BigDecimal,
    @field:Schema(description = "Payouts not settled yet (reserved)")
    val pendingPayouts: BigDecimal,
    @field:Schema(description = "What a new payment can use: available − paid out − pending, never negative")
    val disbursable: BigDecimal,
    val cashOuts: List<CampaignCashOutDto>,
    val manualReleases: List<CampaignManualReleaseDto>,
    @field:Schema(description = "End of the association's last HelloAsso tracking pass; null before the first one")
    val lastSyncAt: Instant?,
    @field:Schema(description = "Whether the last tracking pass failed")
    val syncError: Boolean,
    @field:Schema(description = "Why it failed: RATE_LIMITED, UNAUTHORIZED, UNAVAILABLE or NOT_LINKED; null if it did not")
    val syncErrorCode: String?,
    @field:Schema(description = "From when a refresh will run a new pass; null when possible now or nothing to track")
    val nextRefreshAvailableAt: Instant?,
)
