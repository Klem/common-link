package org.commonlink.service

import org.commonlink.dto.DonorProfileDto
import org.commonlink.dto.UpdateDonorProfileRequest
import org.commonlink.dto.toDto
import org.commonlink.exception.UserNotFoundException
import org.commonlink.repository.DonorProfileRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

/**
 * Business logic for managing donor profiles.
 *
 * A donor profile is created automatically when a donor account is registered and holds
 * the public-facing identity used in donation listings (display name, anonymity preference).
 */
@Service
class DonorService(
    private val donorProfileRepository: DonorProfileRepository
) {

    /**
     * Retrieves the donor profile for the given user.
     *
     * @param userId UUID of the authenticated donor user.
     * @return [DonorProfileDto] with the profile data.
     * @throws UserNotFoundException if no profile exists for this user.
     */
    fun getProfile(userId: UUID): DonorProfileDto =
        donorProfileRepository.findByUserId(userId)
            .orElseThrow { UserNotFoundException("Donor profile not found for user $userId") }
            .toDto()

    /**
     * Partially updates the donor profile with the non-null fields from the request.
     *
     * Only the fields explicitly provided by the caller are updated; null fields in [req]
     * are ignored, preserving the existing values.
     *
     * @param userId UUID of the authenticated donor user.
     * @param req Partial update request; null fields are left unchanged.
     * @return Updated [DonorProfileDto].
     * @throws UserNotFoundException if no profile exists for this user.
     */
    @Transactional
    fun updateProfile(userId: UUID, req: UpdateDonorProfileRequest): DonorProfileDto {
        val profile = donorProfileRepository.findByUserId(userId)
            .orElseThrow { UserNotFoundException("Donor profile not found for user $userId") }
        req.displayName?.let { profile.displayName = it }
        req.anonymous?.let { profile.anonymous = it }
        return donorProfileRepository.save(profile).toDto()
    }
}
