package org.commonlink.service

import org.commonlink.dto.DonorProfileDto
import org.commonlink.dto.UpdateDonorProfileRequest
import org.commonlink.dto.toDto
import org.commonlink.exception.UserNotFoundException
import org.commonlink.repository.DonorProfileRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
class DonorService(
    private val donorProfileRepository: DonorProfileRepository
) {

    fun getProfile(userId: UUID): DonorProfileDto =
        donorProfileRepository.findByUserId(userId)
            .orElseThrow { UserNotFoundException("Donor profile not found for user $userId") }
            .toDto()

    @Transactional
    fun updateProfile(userId: UUID, req: UpdateDonorProfileRequest): DonorProfileDto {
        val profile = donorProfileRepository.findByUserId(userId)
            .orElseThrow { UserNotFoundException("Donor profile not found for user $userId") }
        req.displayName?.let { profile.displayName = it }
        req.anonymous?.let { profile.anonymous = it }
        return donorProfileRepository.save(profile).toDto()
    }
}
