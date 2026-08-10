package org.commonlink.repository

import org.commonlink.entity.BeneficialOwner
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface BeneficialOwnerRepository : JpaRepository<BeneficialOwner, UUID> {

    /** Vrai si au moins un bénéficiaire non écarté existe pour cette association. */
    fun existsByAssociationIdAndDiscardedFalse(associationId: UUID): Boolean

    fun findAllByAssociationIdOrderByCollectedAtAsc(associationId: UUID): List<BeneficialOwner>
}
