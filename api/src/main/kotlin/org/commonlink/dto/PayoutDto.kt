package org.commonlink.dto

import org.commonlink.entity.BridgePaymentStatus
import org.commonlink.entity.Payout
import org.commonlink.entity.PayoutErrorCode
import org.commonlink.entity.PayoutKind
import org.commonlink.entity.PayoutStatus
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

/**
 * Response DTO for a single [org.commonlink.entity.Payout].
 *
 * @param id Payout UUID.
 * @param campaignId Campaign the payout belongs to.
 * @param payeeId Payee receiving the funds.
 * @param payeeName Registered name of the payee.
 * @param payeeIbanId IBAN record used for the transfer.
 * @param ibanValue Masked or full IBAN string.
 * @param amount Amount in euros.
 * @param kind High-level category (REMUNERATION or EXPENSE).
 * @param typeCode French plan comptable code, e.g. "60-mat", "64-rem".
 * @param label Justification text provided by the association.
 * @param status Current lifecycle status.
 * @param createdAt When the payout was created.
 * @param confirmedAt When the payout was confirmed; null if still PENDING.
 * @param onchainJobId UUID of the on-chain job enqueued on confirmation; null until confirmed.
 */
data class PayoutDto(
    val id: UUID,
    val campaignId: UUID,
    val payeeId: UUID,
    val payeeName: String,
    val payeeIbanId: UUID,
    val ibanValue: String,
    val amount: BigDecimal,
    val kind: PayoutKind,
    val typeCode: String,
    val label: String,
    val status: PayoutStatus,
    val createdAt: Instant,
    val confirmedAt: Instant?,
    val onchainJobId: UUID?,
    /**
     * State of the real SEPA transfer at Bridge, or null when no transfer has been initiated.
     * Distinct from [status]: it lets the UI show "awaiting bank authorisation" or "in transit"
     * without altering the three-state lifecycle that balance and KPI computations depend on.
     */
    val bridgeStatus: BridgePaymentStatus? = null,
    /**
     * Stable cause of the last failure, which the frontend turns into a sentence.
     *
     * Replaces exposing [org.commonlink.entity.Payout.bridgeLastError], which mixed Bridge's bare
     * ISO codes with our own English messages — one of them carrying a payout id — and was shown
     * verbatim in a tooltip meant for an association.
     */
    val bridgeLastErrorCode: PayoutErrorCode? = null,
    /**
     * URL the association must open to authorise the transfer at its own bank. Non-null while a
     * confirmed payout still awaits that authorisation.
     */
    val bridgeCheckoutUrl: String? = null,
)

fun Payout.toDto() = PayoutDto(
    id = id,
    campaignId = campaign.id!!,
    payeeId = payee.id!!,
    payeeName = payee.name,
    payeeIbanId = payeeIbanId,
    ibanValue = payeeIbanValue,
    amount = amount,
    kind = kind,
    typeCode = typeCode,
    label = label,
    status = status,
    createdAt = createdAt,
    confirmedAt = confirmedAt,
    onchainJobId = onchainJobId,
    bridgeStatus = bridgeStatus,
    bridgeLastErrorCode = bridgeLastErrorCode,
    bridgeCheckoutUrl = bridgeCheckoutUrl,
)
