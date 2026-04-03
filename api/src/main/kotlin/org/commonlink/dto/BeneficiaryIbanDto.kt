package org.commonlink.dto

import org.commonlink.entity.BeneficiaryIban
import org.commonlink.entity.IbanVerificationStatus
import org.commonlink.entity.VopResult
import java.time.Instant
import java.util.UUID

/**
 * Data Transfer Object representing a single IBAN entry for a beneficiary.
 *
 * Exposes only the fields needed by the frontend — no JPA internals or raw VOP responses.
 *
 * @param id UUID of the [BeneficiaryIban] record.
 * @param iban The IBAN string in normalised uppercase form.
 * @param status Current verification status of this IBAN.
 * @param vopResult Raw outcome of the last VOP check, null if not yet attempted.
 * @param vopSuggestedName Account holder name suggested by the bank, null if not available.
 * @param verifiedAt Timestamp of the last VOP check completion, null if not yet run.
 */
data class BeneficiaryIbanDto(
    val id: UUID,
    val iban: String,
    val status: IbanVerificationStatus,
    val vopResult: VopResult?,
    val vopSuggestedName: String?,
    val verifiedAt: Instant?
)

/**
 * Converts a [BeneficiaryIban] entity to a [BeneficiaryIbanDto].
 */
fun BeneficiaryIban.toDto() = BeneficiaryIbanDto(
    id = id!!,
    iban = iban,
    status = status,
    vopResult = vopResult,
    vopSuggestedName = vopSuggestedName,
    verifiedAt = verifiedAt
)
