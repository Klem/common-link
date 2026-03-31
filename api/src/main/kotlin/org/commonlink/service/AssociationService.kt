package org.commonlink.service

import org.commonlink.dto.AssociationProfileDto
import org.commonlink.dto.UpdateAssociationProfileRequest
import org.commonlink.dto.toDto
import org.commonlink.exception.UserNotFoundException
import org.commonlink.repository.AssociationProfileRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
class AssociationService(
    private val associationProfileRepository: AssociationProfileRepository
) {

    fun getProfile(userId: UUID): AssociationProfileDto =
        associationProfileRepository.findByUserId(userId)
            .orElseThrow { UserNotFoundException("Association profile not found for user $userId") }
            .toDto()

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
