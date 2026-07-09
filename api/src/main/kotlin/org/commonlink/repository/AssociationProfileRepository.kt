package org.commonlink.repository

import org.commonlink.entity.AssociationProfile
import org.commonlink.entity.VerificationStatus
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import java.util.Optional
import java.util.UUID

interface AssociationProfileRepository : JpaRepository<AssociationProfile, UUID> {
    fun findByUserId(userId: UUID): Optional<AssociationProfile>
    fun findByIdentifier(identifier: String): Optional<AssociationProfile>
    fun findByVerificationStatus(status: VerificationStatus, pageable: Pageable): Page<AssociationProfile>
}
