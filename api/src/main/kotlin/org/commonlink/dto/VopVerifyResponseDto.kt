package org.commonlink.dto

import org.commonlink.entity.IbanVerificationStatus
import org.commonlink.entity.VopResult
import java.util.UUID

/**
 * Response DTO returned after a VOP (Verification of Payee) check on a beneficiary IBAN.
 *
 * @param ibanId UUID of the [org.commonlink.entity.BeneficiaryIban] entry that was verified.
 * @param iban The IBAN string that was checked (normalised, no spaces).
 * @param status Updated [IbanVerificationStatus] after the VOP check.
 * @param vopResult Raw VOP outcome from the bank; null only when [IbanVerificationStatus.NOT_POSSIBLE].
 * @param suggestedName Account holder name suggested by the bank (only for [VopResult.CLOSE_MATCH]).
 * @param message Human-readable summary of the verification outcome, suitable for display.
 */
data class VopVerifyResponseDto(
    val ibanId: UUID,
    val iban: String,
    val status: IbanVerificationStatus,
    val vopResult: VopResult?,
    val suggestedName: String?,
    val message: String
)
