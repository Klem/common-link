package org.commonlink.dto

import org.commonlink.entity.PayeeIban
import org.commonlink.entity.IbanVerificationStatus
import org.commonlink.entity.VopResult
import java.time.Instant
import java.util.UUID

/**
 * Data Transfer Object representing a single IBAN entry for a payee.
 *
 * Exposes only the fields needed by the frontend — no JPA internals or raw VOP responses.
 *
 * @param id UUID of the [PayeeIban] record.
 * @param iban The IBAN string in normalised uppercase form.
 * @param status Current verification status of this IBAN.
 * @param vopResult Raw outcome of the last VOP check, null if not yet attempted.
 * @param vopSuggestedName Account holder name suggested by the bank, null if not available.
 * @param verifiedAt Timestamp of the last VOP check completion, null if not yet run.
 * @param active Whether this IBAN can still be used to receive payouts. A VERIFIED IBAN that has
 *   already received a payout can only be disabled (not deleted); see [PayeeIban.active].
 */
data class PayeeIbanDto(
    val id: UUID,
    val iban: String,
    val status: IbanVerificationStatus,
    val vopResult: VopResult?,
    val vopSuggestedName: String?,
    val verifiedAt: Instant?,
    val active: Boolean
)

/**
 * Converts a [PayeeIban] entity to a [PayeeIbanDto].
 */
fun PayeeIban.toDto() = PayeeIbanDto(
    id = id!!,
    iban = iban,
    status = status,
    vopResult = vopResult,
    vopSuggestedName = vopSuggestedName,
    verifiedAt = verifiedAt,
    active = active
)
