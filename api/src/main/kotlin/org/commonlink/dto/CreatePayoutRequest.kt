package org.commonlink.dto

import jakarta.validation.constraints.DecimalMin
import jakarta.validation.constraints.Digits
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Size
import org.commonlink.entity.PayoutKind
import java.math.BigDecimal
import java.util.UUID

/**
 * Request body for creating a new [org.commonlink.entity.Payout].
 *
 * Validation mirrors the frontend form: amount must be positive, label must be meaningful.
 * All three fields (payeeId, payeeIbanId, kind) are independently nullable-safe on the wire
 * but enforced non-null here via @NotNull.
 */
data class CreatePayoutRequest(
    @field:NotNull
    val payeeId: UUID?,

    @field:NotNull
    val payeeIbanId: UUID?,

    /**
     * Transfer amount in euros.
     *
     * [Digits] matches the `NUMERIC(12,2)` column exactly. Without it the balance check ran on the
     * **unrounded** value while the insert rounded: `10.009` passed a check for 10.00 of available
     * funds and was stored as `10.01`. A fraction of a cent per payout, but a balance guard that
     * validates a number other than the one it stores is not a guard.
     */
    @field:NotNull
    @field:DecimalMin("0.01")
    @field:Digits(integer = 10, fraction = 2)
    val amount: BigDecimal?,

    /**
     * Ignored — the stored kind is derived from [typeCode] by [PayoutKind.fromTypeCode].
     *
     * Kept on the wire so the contract does not break, and still required so a caller cannot
     * pretend the field never existed. Trusting it filed a salary as an operating cost whenever
     * the two disagreed, and every click here is replayable.
     */
    @field:NotNull
    val kind: PayoutKind?,

    @field:NotBlank
    @field:Size(max = 50)
    val typeCode: String?,

    /** Justification text — object of payment, invoice reference, etc. Min 6 chars mirrors the maquette textarea. */
    @field:NotBlank
    @field:Size(min = 6, max = 500)
    val label: String?,
)
