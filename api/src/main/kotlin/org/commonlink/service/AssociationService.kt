package org.commonlink.service

import org.commonlink.dto.AssociationProfileDto
import org.commonlink.dto.UpdateAssociationProfileRequest
import org.commonlink.dto.toDto
import org.commonlink.entity.OnchainJobAction
import org.commonlink.entity.VerificationStatus
import org.commonlink.exception.ConflictException
import org.commonlink.exception.NotFoundException
import java.time.Instant
import org.commonlink.exception.UserNotFoundException
import org.commonlink.onchain.OnchainCodec
import org.commonlink.repository.AssociationProfileRepository
import org.commonlink.repository.CampaignRepository
import org.commonlink.repository.MoneriumConnectionRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.web3j.utils.Numeric
import java.security.SecureRandom
import java.util.Base64
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
    private val campaignRepository: CampaignRepository,
    private val connectionRepo: MoneriumConnectionRepository,
    private val outbox: OnchainOutboxService,
    private val onboardingGate: OnboardingGateService,
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

        // SIREN and creation year are immutable once the association is VERIFIED (legal identifiers).
        // Guard triggers only when a *different* value is submitted — resending the stored value is a no-op.
        if (profile.verificationStatus == VerificationStatus.VERIFIED &&
            ((req.siren != null && req.siren != profile.siren) ||
             (req.creationYear != null && req.creationYear != profile.creationYear))) {
            throw ConflictException("SIREN and creation year cannot be modified once the association is verified")
        }
        // Contact name and email are immutable once the Mollie KYC flow has been *started* — they are
        // submitted to Mollie when the client-link is created, before the OAuth callback, so waiting for
        // completion would leave a window where CommonLink and Mollie disagree.
        // Guard triggers only when a *different* value is submitted — resending the stored value is a no-op,
        // which matters because the infos form autosaves and may PATCH unchanged fields.
        if (onboardingGate.isMollieKycStarted(userId) &&
            ((req.contactName != null && req.contactName != profile.contactName) ||
             (req.contactEmail != null && req.contactEmail != profile.contactEmail))) {
            throw ConflictException("Contact name and email cannot be modified once the Mollie KYC flow has started")
        }

        req.contactName?.let { profile.contactName = it }
        req.city?.let { profile.city = it }
        req.postalCode?.let { profile.postalCode = it }
        req.description?.let { profile.description = it }
        // A cleared form field arrives as "", which must land as NULL: downstream registry lookups
        // read blank as "no SIREN", and an empty query term is rejected by Recherche d'entreprises.
        req.siren?.let { profile.siren = it.trim().ifBlank { null } }
        req.creationYear?.let { profile.creationYear = it }
        req.contactEmail?.let { profile.contactEmail = it }
        req.phone?.let { profile.phone = it }
        req.widgetDestinationCampaignId?.let { campaignId ->
            // Chain guard: a destination campaign is a widget setting — the bank account (Mollie)
            // must be completed first. Only enforced when a destination is actually being set,
            // so the general profile edit (infos tab) is unaffected.
            onboardingGate.requireBankReady(userId)
            val campaign = campaignRepository.findByIdAndAssociationId(campaignId, profile.id!!)
                .orElseThrow { NotFoundException("Campaign not found: $campaignId") }
            profile.widgetDestinationCampaign = campaign
        }
        req.addressLine1?.let { profile.addressLine1 = it }
        req.legalObject?.let { profile.legalObject = it }
        req.signerName?.let { profile.signerName = it }
        req.signerRole?.let { profile.signerRole = it }
        return associationProfileRepository.save(profile).toDto()
    }

    /**
     * Generates a new widget token for the association, revoking any existing one.
     *
     * The token is a cryptographically random opaque string prefixed with `clk_`.
     * Rotation is implicit — a new call replaces the previous token, immediately
     * invalidating any existing embeds that reference the old token.
     *
     * @param userId UUID of the authenticated association user.
     * @return The newly generated widget token.
     * @throws UserNotFoundException if no profile exists for this user.
     * @throws org.commonlink.exception.ConflictException if the bank account (Mollie) is not completed.
     */
    @Transactional
    fun generateWidgetToken(userId: UUID): String {
        onboardingGate.requireBankReady(userId)
        val profile = associationProfileRepository.findByUserId(userId)
            .orElseThrow { UserNotFoundException("Association profile not found for user $userId") }
        val bytes = ByteArray(24).also { SecureRandom().nextBytes(it) }
        val token = "clk_" + Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
        profile.widgetToken = token
        associationProfileRepository.save(profile)
        logger.info("Widget token rotated for association {}", profile.id)
        return token
    }

    /**
     * Updates widget configuration: stores the normalized origin of [widgetAllowedOrigin].
     * Null clears the setting, disabling post-payment redirects.
     *
     * @param userId UUID of the authenticated association user.
     * @param widgetAllowedOrigin Raw origin URL supplied by the association. Normalized to scheme+host.
     * @throws UserNotFoundException if no profile exists for this user.
     * @throws org.commonlink.exception.ConflictException if the bank account (Mollie) is not completed.
     */
    @Transactional
    fun updateWidgetConfig(userId: UUID, widgetAllowedOrigin: String?) {
        onboardingGate.requireBankReady(userId)
        val profile = associationProfileRepository.findByUserId(userId)
            .orElseThrow { UserNotFoundException("Association profile not found for user $userId") }
        profile.widgetAllowedOrigin = normalizeOrigin(widgetAllowedOrigin)
        associationProfileRepository.save(profile)
        logger.info("Widget config updated for association {}", profile.id)
    }

    private fun normalizeOrigin(raw: String?): String? {
        if (raw.isNullOrBlank()) return null
        return try {
            val uri = java.net.URI(raw.trim())
            val scheme = uri.scheme?.takeIf { it == "http" || it == "https" } ?: return null
            val host = uri.host?.takeIf { it.isNotBlank() } ?: return null
            val port = if (uri.port != -1) ":${uri.port}" else ""
            "$scheme://$host$port"
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Disables the donation widget for the association by clearing its token.
     *
     * Any existing embed using the old token will return 404 until a new token is generated.
     *
     * @param userId UUID of the authenticated association user.
     * @throws UserNotFoundException if no profile exists for this user.
     */
    @Transactional
    fun deleteWidgetToken(userId: UUID) {
        val profile = associationProfileRepository.findByUserId(userId)
            .orElseThrow { UserNotFoundException("Association profile not found for user $userId") }
        profile.widgetToken = null
        associationProfileRepository.save(profile)
        logger.info("Widget token deleted for association {}", profile.id)
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
        association.verificationStatus = VerificationStatus.VERIFIED
        association.verifiedAt = Instant.now()
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
        association.verificationStatus = VerificationStatus.UNVERIFIED
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
        association.verificationStatus = VerificationStatus.VERIFIED
        association.verifiedAt = Instant.now()
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
