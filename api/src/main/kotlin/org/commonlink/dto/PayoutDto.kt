package org.commonlink.dto

import org.commonlink.entity.Payout
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
)

fun Payout.toDto() = PayoutDto(
    id = id,
    campaignId = campaign.id!!,
    payeeId = payee.id!!,
    payeeName = payee.name,
    payeeIbanId = payeeIban.id!!,
    ibanValue = payeeIban.iban,
    amount = amount,
    kind = kind,
    typeCode = typeCode,
    label = label,
    status = status,
    createdAt = createdAt,
    confirmedAt = confirmedAt,
    onchainJobId = onchainJobId,
)
