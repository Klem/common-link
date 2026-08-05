package org.commonlink.service

import org.commonlink.dto.AssociationProfileDto
import org.commonlink.dto.LandingPreviewTokenDto
import org.commonlink.dto.UpdateLandingConfigRequest
import org.commonlink.dto.toDto
import org.commonlink.entity.AssociationLogo
import org.commonlink.exception.NotFoundException
import org.commonlink.exception.UnprocessableEntityException
import org.commonlink.exception.UserNotFoundException
import org.commonlink.repository.AssociationLogoRepository
import org.commonlink.repository.AssociationProfileRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.multipart.MultipartFile
import java.time.Instant
import java.util.UUID

/**
 * Landing page customization for an association: visual theme, logo and section visibility.
 *
 * Kept apart from [AssociationService] (identity, KYC, widget token) because the concerns do not
 * overlap: this service owns everything an association may change about the *presentation* of its
 * donation landing page.
 *
 * Every write is gated on the same prerequisite as the widget ([OnboardingGateService.requireBankReady]):
 * the landing page serves the same donation form on the same campaign, so it must never be
 * configurable earlier than the widget it mirrors.
 */
@Service
class AssociationLandingService(
    private val associationProfileRepository: AssociationProfileRepository,
    private val associationLogoRepository: AssociationLogoRepository,
    private val onboardingGate: OnboardingGateService,
    private val previewTokenService: LandingPreviewTokenService,
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    companion object {
        /** 2 MB — a header logo never needs more, and the limit must be mirrored by the upload zone. */
        const val MAX_LOGO_SIZE: Long = 2 * 1024 * 1024

        /**
         * Accepted logo MIME types. SVG is deliberately excluded: it is an XSS vector when served
         * from our own origin, and the serving endpoint returns the bytes verbatim.
         */
        val LOGO_ALLOWED_MIME = setOf("image/jpeg", "image/png", "image/webp")
    }

    /**
     * Applies a partial landing configuration update. Null fields are left untouched.
     *
     * @param userId UUID of the authenticated association user.
     * @param req Fields to update; all optional.
     * @return The updated profile.
     * @throws UserNotFoundException if no association profile exists for this user.
     * @throws org.commonlink.exception.ConflictException if the bank account (Mollie) is not completed.
     */
    @Transactional
    fun updateLandingConfig(userId: UUID, req: UpdateLandingConfigRequest): AssociationProfileDto {
        onboardingGate.requireBankReady(userId)
        val profile = resolveProfile(userId)

        req.theme?.let { profile.landingTheme = it }
        req.showProject?.let { profile.landingShowProject = it }
        req.showTransparency?.let { profile.landingShowTransparency = it }
        req.showTrust?.let { profile.landingShowTrust = it }

        val saved = associationProfileRepository.save(profile)
        logger.info(
            "Landing config updated for association {}: theme={}, project={}, transparency={}, trust={}",
            saved.id, saved.landingTheme, saved.landingShowProject,
            saved.landingShowTransparency, saved.landingShowTrust,
        )
        return saved.toDto()
    }

    /**
     * Uploads (or replaces) the landing page logo.
     *
     * @param userId UUID of the authenticated association user.
     * @param file Multipart image; JPEG, PNG or WebP, up to [MAX_LOGO_SIZE].
     * @return The updated profile, carrying the public serving path in `landingLogo`.
     * @throws UserNotFoundException if no association profile exists for this user.
     * @throws UnprocessableEntityException if the file is empty, too large or not an accepted type.
     * @throws org.commonlink.exception.ConflictException if the bank account (Mollie) is not completed.
     */
    @Transactional
    fun uploadLogo(userId: UUID, file: MultipartFile): AssociationProfileDto {
        onboardingGate.requireBankReady(userId)
        val profile = resolveProfile(userId)
        validateLogo(file)

        val associationId = profile.id!!
        // Shared primary key: saving over an existing row replaces the previous logo.
        associationLogoRepository.save(
            AssociationLogo(
                associationId = associationId,
                data = file.bytes,
                contentType = file.contentType!!,
                sizeBytes = file.size,
                uploadedAt = Instant.now(),
            )
        )
        profile.landingLogo = logoPath(associationId)
        val saved = associationProfileRepository.save(profile)

        logger.info("Landing logo uploaded: associationId={}, size={}", associationId, file.size)
        return saved.toDto()
    }

    /**
     * Removes the landing page logo. No-op if no logo is set.
     *
     * @param userId UUID of the authenticated association user.
     * @return The updated profile with a null `landingLogo`.
     * @throws UserNotFoundException if no association profile exists for this user.
     * @throws org.commonlink.exception.ConflictException if the bank account (Mollie) is not completed.
     */
    @Transactional
    fun deleteLogo(userId: UUID): AssociationProfileDto {
        onboardingGate.requireBankReady(userId)
        val profile = resolveProfile(userId)
        val associationId = profile.id!!

        associationLogoRepository.deleteById(associationId)
        profile.landingLogo = null
        val saved = associationProfileRepository.save(profile)

        logger.info("Landing logo deleted: associationId={}", associationId)
        return saved.toDto()
    }

    /**
     * Returns the raw logo bytes for the unauthenticated serving endpoint.
     *
     * Deliberately not scoped to the owner: an `<img>` tag cannot carry a Bearer token, so the same
     * URL must work for the association's own preview and for the public landing page. A logo carries
     * no confidential data and the association UUID is not enumerable.
     *
     * The bytes are read inside the transaction and returned as a plain pair, so the caller never
     * touches the lazily-mapped `data` field outside the persistence context.
     *
     * @param associationId UUID of the association profile.
     * @return MIME type and raw bytes of the logo.
     * @throws NotFoundException if the association has no logo.
     */
    @Transactional(readOnly = true)
    fun getLogo(associationId: UUID): Pair<String, ByteArray> {
        val logo = associationLogoRepository.findById(associationId)
            .orElseThrow { NotFoundException("No logo for association $associationId") }
        return logo.contentType to logo.data
    }

    /**
     * Issues a preview token letting this association render its own landing page even when the
     * destination campaign is not LIVE.
     *
     * Same gate as the rest of the tab: previewing is a configuration activity, not a public one.
     *
     * @param userId UUID of the authenticated association user.
     * @return The token and its expiry.
     * @throws UserNotFoundException if no association profile exists for this user.
     * @throws org.commonlink.exception.ConflictException if the bank account (Mollie) is not completed.
     */
    @Transactional(readOnly = true)
    fun issuePreviewToken(userId: UUID): LandingPreviewTokenDto {
        onboardingGate.requireBankReady(userId)
        val profile = resolveProfile(userId)
        val (token, expiresAt) = previewTokenService.issue(profile.id!!)
        return LandingPreviewTokenDto(previewToken = token, expiresAt = expiresAt)
    }

    /** Public serving path stored on the profile — never recomposed by callers. */
    private fun logoPath(associationId: UUID) = "/api/public/associations/$associationId/logo"

    /** Rejects empty files, oversized files and non-image MIME types. */
    private fun validateLogo(file: MultipartFile) {
        if (file.isEmpty) {
            throw UnprocessableEntityException("Logo file is empty")
        }
        if (file.size > MAX_LOGO_SIZE) {
            throw UnprocessableEntityException("Logo exceeds the maximum allowed size of 2 MB")
        }
        val mime = file.contentType ?: ""
        if (mime !in LOGO_ALLOWED_MIME) {
            throw UnprocessableEntityException(
                "Unsupported logo type '$mime'; allowed types: ${LOGO_ALLOWED_MIME.joinToString(", ")}"
            )
        }
    }

    private fun resolveProfile(userId: UUID) =
        associationProfileRepository.findByUserId(userId)
            .orElseThrow { UserNotFoundException("Association profile not found for user $userId") }
}
