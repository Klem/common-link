package org.commonlink.dto

import io.swagger.v3.oas.annotations.media.Schema
import org.commonlink.entity.FundsState
import org.commonlink.entity.HelloAssoConnectionState
import org.commonlink.entity.ReceiptConfirmationMode
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

/** One donation of a HelloAsso payout made available, in a transfer receipt confirmation response. Amounts in euros. */
@Schema(description = "A donation of the payout, available for payments")
data class CashOutReceiptDonationDto(
    val donationId: UUID,
    val campaignId: UUID,
    val amount: BigDecimal,
    @field:Schema(description = "When it became available for payments")
    val availableAt: Instant?,
    @field:Schema(description = "MANUAL (this confirmation), AUTO (settlement delay) or CURATOR_RELEASE")
    val confirmationMode: ReceiptConfirmationMode?,
)

/** Response of `POST /api/association/cash-outs/{idCashOut}/receipt`. */
@Schema(description = "HelloAsso payout confirmed as received: its donations available for payments")
data class CashOutReceiptResponse(
    val idCashOut: Long,
    @field:Schema(description = "Net amount of the payout at HelloAsso in euros (may include other payments); null if unknown")
    val netTotal: BigDecimal?,
    val donations: List<CashOutReceiptDonationDto>,
)

/** Response of `GET /api/association/helloasso/disconnect-preview`. Amount in euros. */
@Schema(description = "Donations HelloAsso has not paid out yet, shown before disconnecting")
data class HelloAssoDisconnectPreviewResponse(
    val notYetPaidOutCount: Long,
    val notYetPaidOutAmount: BigDecimal,
)

/** A HelloAsso donation a curator may release, in `GET /api/admin/associations/{id}/helloasso-funds`. */
@Schema(description = "HelloAsso donation not available yet")
data class HelloAssoReleasableDonationDto(
    val donationId: UUID,
    val campaignId: UUID,
    val campaignName: String,
    val amount: BigDecimal,
    val fundsState: FundsState,
    @field:Schema(description = "HelloAsso's raw payout state at the last read")
    val cashOutState: String?,
    val idCashOut: Long?,
    val lastCheckedAt: Instant?,
)

/** Response of `GET /api/admin/associations/{id}/helloasso-funds`. */
@Schema(description = "HelloAsso donations of an association not available yet, and its HelloAsso link state")
data class HelloAssoReleasableFundsResponse(
    @field:Schema(description = "HelloAsso link state; null when never linked")
    val connectionState: HelloAssoConnectionState?,
    val donations: List<HelloAssoReleasableDonationDto>,
)

/** Response of `POST /api/admin/associations/{id}/helloasso-funds/release`. Amount in euros. */
@Schema(description = "HelloAsso donations released by a curator")
data class HelloAssoFundsReleaseResponse(
    val evidenceId: UUID,
    val donationIds: List<UUID>,
    val amount: BigDecimal,
    val releasedAt: Instant,
)
