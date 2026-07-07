package org.commonlink.dto

import jakarta.validation.constraints.DecimalMin
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

    @field:NotNull
    @field:DecimalMin("0.01")
    val amount: BigDecimal?,

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
