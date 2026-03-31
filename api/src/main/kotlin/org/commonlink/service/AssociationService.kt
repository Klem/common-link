package org.commonlink.service

import org.commonlink.dto.AssociationProfileDto
import org.commonlink.dto.UpdateAssociationProfileRequest
import org.commonlink.dto.toDto
import org.commonlink.exception.UserNotFoundException
import org.commonlink.repository.AssociationProfileRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

/**
 * Business logic for managing association profiles.
 *
 * An association profile is created during registration and holds the public-facing
 * identity of a non-profit organisation (name, SIREN identifier, location, description).
 * This service is used by the association dashboard endpoints.
 */
@Service
class AssociationService(
    private val associationProfileRepository: AssociationProfileRepository
) {

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
}
