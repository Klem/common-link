package org.commonlink.repository

import org.commonlink.entity.ComplianceDeclarant
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface ComplianceDeclarantRepository : JpaRepository<ComplianceDeclarant, UUID> {

    /** All currently active (non-revoked) declarants. */
    fun findAllByRevokedAtIsNull(): List<ComplianceDeclarant>
}
