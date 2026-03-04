package org.commonlink.repository

import org.commonlink.entity.DonorProfile
import org.springframework.data.jpa.repository.JpaRepository
import java.util.Optional
import java.util.UUID

interface DonorProfileRepository : JpaRepository<DonorProfile, UUID> {
    fun findByUserId(userId: UUID): Optional<DonorProfile>
}
