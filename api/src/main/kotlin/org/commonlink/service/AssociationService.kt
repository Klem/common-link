package org.commonlink.service

import org.commonlink.dto.AssociationProfileDto
import org.commonlink.dto.UpdateAssociationProfileRequest
import org.commonlink.dto.toDto
import org.commonlink.entity.OnchainJobAction
import org.commonlink.exception.NotFoundException
import org.commonlink.exception.UserNotFoundException
import org.commonlink.onchain.OnchainCodec
import org.commonlink.repository.AssociationProfileRepository
import org.commonlink.repository.MoneriumConnectionRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.web3j.utils.Numeric
import java.util.UUID

/**
 * Business logic for managing association profiles.
 *
 * An association profile is created during registration and holds the public-facing
 * identity of a non-profit organisation (name, SIREN identifier, location, description).
 * This service is used by the association dashboard endpoints and curator moderation.
 */
@Service
class AssociationService(
    private val associationProfileRepository: AssociationProfileRepository,
    private val connectionRepo: MoneriumConnectionRepository,
    private val outbox: OnchainOutboxService,
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    /** Returns true if an association profile with the given id exists. */
    fun existsById(id: UUID): Boolean = associationProfileRepository.existsById(id)

    /**
     * Returns the SIREN/RNA identifier for the association profile with the given id.
     *
     * @throws org.commonlink.exception.NotFoundException if the profile does not exist.
     */
    fun getIdentifier(id: UUID): String =
        associationProfileRepository.findById(id)
            .orElseThrow { NotFoundException("Association not found: $id") }
            .identifier

    /**
     * Retrieves the association profile for the given user.
     *
     * @param userId UUID of the authenticated association user.
     * @return [AssociationProfileDto] with the profile data.
     * @throws UserNotFoundException if no profile exists for this user.
     */
    fun getProfile(userId: UUID): AssociationProfileDto =
        associationProfileRepository.findByUserId(userId)
            .orElseThrow { UserNotFoundException("Association profile not found for user $userId") }
            .toDto()

    /**
     * Partially updates the association profile with the non-null fields from the request.
     *
     * Only the fields explicitly provided by the caller are updated; null fields in [req]
     * are ignored, preserving the existing values.
     *
     * @param userId UUID of the authenticated association user.
     * @param req Partial update request; null fields are left unchanged.
     * @return Updated [AssociationProfileDto].
     * @throws UserNotFoundException if no profile exists for this user.
     */
    @Transactional
    fun updateProfile(userId: UUID, req: UpdateAssociationProfileRequest): AssociationProfileDto {
        val profile = associationProfileRepository.findByUserId(userId)
            .orElseThrow { UserNotFoundException("Association profile not found for user $userId") }
        req.contactName?.let { profile.contactName = it }
        req.city?.let { profile.city = it }
        req.postalCode?.let { profile.postalCode = it }
        req.description?.let { profile.description = it }
        return associationProfileRepository.save(profile).toDto()
    }

    /**
     * Marks the association as curator-verified and enqueues an on-chain
     * [OnchainJobAction.VERIFY_ASSOCIATION] job if a Monerium wallet is linked.
     *
     * Idempotent — calling twice produces at most one on-chain job (via correlationKey).
     *
     * @param associationId UUID of the [org.commonlink.entity.AssociationProfile].
     * @throws IllegalArgumentException if no profile exists for this id.
     */
    @Transactional
    fun markVerified(associationId: UUID) {
        val association = associationProfileRepository.findById(associationId)
            .orElseThrow { IllegalArgumentException("Association not found: $associationId") }
        association.verified = true
        associationProfileRepository.save(association)

        val walletAddress = connectionRepo.findByAssociationId(associationId)?.walletAddress
        if (walletAddress == null) {
            logger.warn(
                "Association {} verified but has no linked wallet — on-chain VERIFY_ASSOCIATION skipped",
                associationId,
            )
            return
        }
        outbox.enqueue(
            action = OnchainJobAction.VERIFY_ASSOCIATION,
            payload = VerifyAssociationPayload(
                address = walletAddress,
                sirenHashHex = Numeric.toHexString(OnchainCodec.keccakSiren(association.identifier)),
            ),
            correlationKey = "VERIFY_ASSOCIATION:$associationId",
        )
        logger.info("Enqueued VERIFY_ASSOCIATION for association {} (wallet={})", associationId, walletAddress)
    }

    /**
     * Revokes curator verification for the association and enqueues an on-chain
     * [OnchainJobAction.REVOKE_ASSOCIATION] job if a Monerium wallet is linked.
     *
     * @param associationId UUID of the [org.commonlink.entity.AssociationProfile].
     * @throws IllegalArgumentException if no profile exists for this id.
     */
    @Transactional
    fun revokeAssociation(associationId: UUID) {
        val association = associationProfileRepository.findById(associationId)
            .orElseThrow { IllegalArgumentException("Association not found: $associationId") }
        association.verified = false
        associationProfileRepository.save(association)

        val walletAddress = connectionRepo.findByAssociationId(associationId)?.walletAddress
        if (walletAddress == null) {
            logger.warn(
                "Association {} revoked but has no linked wallet — on-chain REVOKE_ASSOCIATION skipped",
                associationId,
            )
            return
        }
        outbox.enqueue(
            action = OnchainJobAction.REVOKE_ASSOCIATION,
            payload = AddressOnlyPayload(address = walletAddress),
            correlationKey = "REVOKE_ASSOCIATION:$associationId",
        )
        logger.info("Enqueued REVOKE_ASSOCIATION for association {} (wallet={})", associationId, walletAddress)
    }

    /**
     * Restores curator verification after a prior revocation and enqueues an on-chain
     * [OnchainJobAction.RESTORE_ASSOCIATION] job if a Monerium wallet is linked.
     *
     * @param associationId UUID of the [org.commonlink.entity.AssociationProfile].
     * @throws IllegalArgumentException if no profile exists for this id.
     */
    @Transactional
    fun restoreAssociation(associationId: UUID) {
        val association = associationProfileRepository.findById(associationId)
            .orElseThrow { IllegalArgumentException("Association not found: $associationId") }
        association.verified = true
        associationProfileRepository.save(association)

        val walletAddress = connectionRepo.findByAssociationId(associationId)?.walletAddress
        if (walletAddress == null) {
            logger.warn(
                "Association {} restored but has no linked wallet — on-chain RESTORE_ASSOCIATION skipped",
                associationId,
            )
            return
        }
        outbox.enqueue(
            action = OnchainJobAction.RESTORE_ASSOCIATION,
            payload = AddressOnlyPayload(address = walletAddress),
            correlationKey = "RESTORE_ASSOCIATION:$associationId",
        )
        logger.info("Enqueued RESTORE_ASSOCIATION for association {} (wallet={})", associationId, walletAddress)
    }
}
