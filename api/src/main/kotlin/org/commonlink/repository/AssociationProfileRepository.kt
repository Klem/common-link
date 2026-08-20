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

    /**
     * Whether any profile already carries this identifier.
     *
     * `association_profiles.identifier` has no unique constraint (plain index since V4), so
     * duplicates are possible in existing data and an `Optional` finder would raise
     * `IncorrectResultSizeDataAccessException`. A boolean projection cannot.
     */
    fun existsByIdentifier(identifier: String): Boolean

    /** Whether any profile already carries this SIREN in the secondary [AssociationProfile.siren] column. */
    fun existsBySiren(siren: String): Boolean
    fun findByVerificationStatus(status: VerificationStatus, pageable: Pageable): Page<AssociationProfile>
    fun findByWidgetToken(widgetToken: String): Optional<AssociationProfile>
}
