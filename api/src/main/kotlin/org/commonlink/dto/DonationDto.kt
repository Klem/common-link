package org.commonlink.dto

import org.commonlink.entity.Donation
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

/**
 * Public representation of a single donation.
 *
 * [providerRef] is the payment provider reference (e.g. "monerium:<uuid>" or "stripe:pi_…").
 * It is on-chain proof and is always exposed regardless of donor anonymity.
 * [onChain] is true when [confirmedAt] is set, indicating the donation has been recorded on-chain.
 */
data class DonationDto(
    val id: UUID,
    val amount: BigDecimal,
    val providerRef: String,
    val confirmedAt: Instant?,
    val createdAt: Instant,
    val onChain: Boolean,
)

fun Donation.toDto() = DonationDto(
    id          = id!!,
    amount      = amount,
    providerRef = providerRef,
    confirmedAt = confirmedAt,
    createdAt   = createdAt,
    onChain     = confirmedAt != null,
)
