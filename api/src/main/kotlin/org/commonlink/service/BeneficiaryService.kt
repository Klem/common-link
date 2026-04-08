package org.commonlink.service

import org.commonlink.dto.AddIbanRequest
import org.commonlink.dto.BeneficiaryDto
import org.commonlink.dto.CreateBeneficiaryRequest
import org.commonlink.dto.VopVerifyResponseDto
import org.commonlink.dto.toDto
import org.commonlink.entity.Beneficiary
import org.commonlink.entity.BeneficiaryIban
import org.commonlink.entity.IbanVerificationStatus
import org.commonlink.entity.VopResult
import org.commonlink.exception.ConflictException
import org.commonlink.exception.NotFoundException
import org.commonlink.exception.UnprocessableEntityException
import org.commonlink.exception.UserNotFoundException
import org.commonlink.repository.AssociationProfileRepository
import org.commonlink.repository.BeneficiaryIbanRepository
import org.commonlink.repository.BeneficiaryRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID

/**
 * Business logic for managing beneficiaries of an association.
 *
 * All operations are scoped to the authenticated association: the [userId] from the JWT is
 * resolved to an [org.commonlink.entity.AssociationProfile] id before any data access.
 * This prevents cross-association data leaks.
 */
@Service
class BeneficiaryService(
    private val beneficiaryRepository: BeneficiaryRepository,
    private val beneficiaryIbanRepository: BeneficiaryIbanRepository,
    private val associationProfileRepository: AssociationProfileRepository,
    private val vopService: VopService
) {

    /**
     * Lists all beneficiaries for the authenticated association.
     *
     * @param userId UUID of the authenticated association user.
     * @return List of [BeneficiaryDto], possibly empty.
     * @throws UserNotFoundException if no association profile exists for this user.
     */
    fun listBeneficiaries(userId: UUID): List<BeneficiaryDto> {
        val associationId = resolveAssociationId(userId)
        return beneficiaryRepository.findAllByAssociationId(associationId).map { it.toDto() }
    }

    /**
     * Creates a new beneficiary under the authenticated association.
     *
     * The SIREN ([CreateBeneficiaryRequest.identifier1]) must be unique per association;
     * a [ConflictException] is thrown if a beneficiary with the same SIREN already exists.
     *
     * @param userId UUID of the authenticated association user.
     * @param req Creation request with beneficiary details.
     * @return [BeneficiaryDto] of the persisted beneficiary.
     * @throws UserNotFoundException if no association profile exists for this user.
     * @throws ConflictException if a beneficiary with the same SIREN already exists.
     */
    @Transactional
    fun createBeneficiary(userId: UUID, req: CreateBeneficiaryRequest): BeneficiaryDto {
        val associationId = resolveAssociationId(userId)
        if (beneficiaryRepository.existsByAssociationIdAndIdentifier1(associationId, req.identifier1)) {
            throw ConflictException("Beneficiary with this identifier already exists for this association")
        }
        val association = associationProfileRepository.findByUserId(userId)
            .orElseThrow { UserNotFoundException("Association profile not found for user $userId") }
        val beneficiary = Beneficiary(
            association = association,
            name = req.name,
            identifier1 = req.identifier1,
            identifier2 = req.identifier2,
            activityCode = req.activityCode,
            category = req.category,
            city = req.city,
            postalCode = req.postalCode,
            active = req.active
        )
        return beneficiaryRepository.save(beneficiary).toDto()
    }

    /**
     * Deletes a beneficiary by id, scoped to the authenticated association.
     *
     * @param userId UUID of the authenticated association user.
     * @param beneficiaryId UUID of the beneficiary to delete.
     * @throws UserNotFoundException if no association profile exists for this user, or the
     *   beneficiary is not found under this association.
     */
    @Transactional
    fun deleteBeneficiary(userId: UUID, beneficiaryId: UUID) {
        val associationId = resolveAssociationId(userId)
        val beneficiary = beneficiaryRepository.findByIdAndAssociationId(beneficiaryId, associationId)
            .orElseThrow { UserNotFoundException("Beneficiary not found") }
        beneficiaryRepository.delete(beneficiary)
    }

    /**
     * Adds an IBAN to a beneficiary after normalising and validating its format via mod-97.
     *
     * The IBAN is uppercased and stripped of spaces before validation. If the mod-97 checksum
     * passes, the status is set to [IbanVerificationStatus.FORMAT_VALID]; otherwise [IbanVerificationStatus.INVALID].
     *
     * @param userId UUID of the authenticated association user.
     * @param beneficiaryId UUID of the beneficiary that will own the new IBAN.
     * @param req Request carrying the raw IBAN string.
     * @return Updated [BeneficiaryDto] including the newly added IBAN.
     * @throws UserNotFoundException if no association profile or beneficiary is found.
     */
    @Transactional
    fun addIban(userId: UUID, beneficiaryId: UUID, req: AddIbanRequest): BeneficiaryDto {
        val associationId = resolveAssociationId(userId)
        val beneficiary = beneficiaryRepository.findByIdAndAssociationId(beneficiaryId, associationId)
            .orElseThrow { UserNotFoundException("Beneficiary not found") }

        val normalised = req.iban.uppercase().replace("\\s".toRegex(), "")
        val status = if (isValidIban(normalised)) IbanVerificationStatus.FORMAT_VALID else IbanVerificationStatus.INVALID

        val ibanEntry = BeneficiaryIban(
            beneficiary = beneficiary,
            iban = normalised,
            status = status
        )
        beneficiary.ibans.add(ibanEntry)
        return beneficiaryRepository.save(beneficiary).toDto()
    }

    /**
     * Removes an IBAN entry from a beneficiary, scoped to the authenticated association.
     *
     * @param userId UUID of the authenticated association user.
     * @param beneficiaryId UUID of the beneficiary that owns the IBAN.
     * @param ibanId UUID of the IBAN entry to remove.
     * @throws UserNotFoundException if any of the expected records are not found.
     */
    @Transactional
    fun deleteIban(userId: UUID, beneficiaryId: UUID, ibanId: UUID) {
        val associationId = resolveAssociationId(userId)
        beneficiaryRepository.findByIdAndAssociationId(beneficiaryId, associationId)
            .orElseThrow { UserNotFoundException("Beneficiary not found") }
        val ibanEntry = beneficiaryIbanRepository.findByIdAndBeneficiaryId(ibanId, beneficiaryId)
            .orElseThrow { UserNotFoundException("IBAN not found") }
        beneficiaryIbanRepository.delete(ibanEntry)
    }

    /**
     * Verifies a beneficiary IBAN via VOP (Verification of Payee) and persists the result.
     *
     * The IBAN must be in [IbanVerificationStatus.FORMAT_VALID] status before VOP can be
     * initiated — this is enforced server-side even though the frontend also guards the action,
     * because API calls can be replayed independently of the UI.
     *
     * After the VOP call, the IBAN is updated with:
     * - [BeneficiaryIban.vopResult], [BeneficiaryIban.vopSuggestedName], [BeneficiaryIban.vopRawResponse]
     * - [BeneficiaryIban.verifiedAt] set to the current instant
     * - [BeneficiaryIban.status] derived from the VOP outcome:
     *   MATCH → VERIFIED, CLOSE_MATCH → CLOSE_MATCH, NO_MATCH → NO_MATCH, NOT_POSSIBLE → NOT_POSSIBLE
     *
     * @param userId UUID of the authenticated association user.
     * @param beneficiaryId UUID of the beneficiary that owns the IBAN.
     * @param ibanId UUID of the IBAN entry to verify.
     * @return [VopVerifyResponseDto] with the updated status, VOP result, and a human-readable message.
     * @throws UserNotFoundException if the association profile or beneficiary is not found.
     * @throws NotFoundException if the IBAN entry is not found.
     * @throws UnprocessableEntityException if the IBAN status is not [IbanVerificationStatus.FORMAT_VALID].
     */
    @Transactional
    fun verifyIbanVop(userId: UUID, beneficiaryId: UUID, ibanId: UUID): VopVerifyResponseDto {
        val associationId = resolveAssociationId(userId)
        beneficiaryRepository.findByIdAndAssociationId(beneficiaryId, associationId)
            .orElseThrow { UserNotFoundException("Beneficiary not found") }
        val ibanEntry = beneficiaryIbanRepository.findByIdAndBeneficiaryId(ibanId, beneficiaryId)
            .orElseThrow { NotFoundException("IBAN not found") }

        if (ibanEntry.status != IbanVerificationStatus.FORMAT_VALID) {
            throw UnprocessableEntityException("IBAN must have FORMAT_VALID status before VOP verification")
        }

        val beneficiary = ibanEntry.beneficiary
        val result = vopService.verify(ibanEntry.iban, beneficiary.name)

        ibanEntry.vopResult = result.result
        ibanEntry.vopSuggestedName = result.suggestedName
        ibanEntry.vopRawResponse = result.rawResponse
        ibanEntry.verifiedAt = Instant.now()
        ibanEntry.status = when (result.result) {
            VopResult.MATCH -> IbanVerificationStatus.VERIFIED
            VopResult.CLOSE_MATCH -> IbanVerificationStatus.CLOSE_MATCH
            VopResult.NO_MATCH -> IbanVerificationStatus.NO_MATCH
            VopResult.NOT_POSSIBLE -> IbanVerificationStatus.NOT_POSSIBLE
        }
        beneficiaryIbanRepository.save(ibanEntry)

        val message = when (result.result) {
            VopResult.MATCH -> "VOP verification successful: account holder name matches."
            VopResult.CLOSE_MATCH -> "VOP close match — name differs slightly. Suggested: ${result.suggestedName}"
            VopResult.NO_MATCH -> "VOP verification failed: account holder name does not match."
            VopResult.NOT_POSSIBLE -> "VOP verification not possible: the bank does not support VOP for this IBAN."
        }

        return VopVerifyResponseDto(
            ibanId = ibanEntry.id!!,
            iban = ibanEntry.iban,
            status = ibanEntry.status,
            vopResult = ibanEntry.vopResult,
            suggestedName = ibanEntry.vopSuggestedName,
            message = message
        )
    }

    /**
     * Validates an IBAN using the ISO 13616 mod-97 algorithm.
     *
     * Steps:
     * 1. Check basic regex structure: country code (2 letters) + check digits (2 digits) + BBAN (10–30 alphanumeric).
     * 2. Move the first 4 characters to the end.
     * 3. Replace each letter with its numeric value (A=10 … Z=35).
     * 4. Compute the integer remainder mod 97 — valid if and only if the result is 1.
     *
     * @param iban Normalised IBAN (uppercase, no spaces).
     * @return `true` if the IBAN passes the mod-97 checksum, `false` otherwise.
     */
    private fun isValidIban(iban: String): Boolean {
        if (!iban.matches(Regex("^[A-Z]{2}[0-9]{2}[A-Z0-9]{10,30}$"))) return false
        val rearranged = iban.substring(4) + iban.substring(0, 4)
        val numeric = rearranged.map { c ->
            if (c.isLetter()) (c - 'A' + 10).toString() else c.toString()
        }.joinToString("")
        return numeric.toBigInteger().mod(97.toBigInteger()) == 1.toBigInteger()
    }

    /**
     * Resolves the association profile id for the given user.
     *
     * @param userId UUID of the authenticated user.
     * @return UUID of the corresponding [org.commonlink.entity.AssociationProfile].
     * @throws UserNotFoundException if no profile exists for this user.
     */
    private fun resolveAssociationId(userId: UUID): UUID =
        associationProfileRepository.findByUserId(userId)
            .orElseThrow { UserNotFoundException("Association profile not found for user $userId") }
            .id!!

}
