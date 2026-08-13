package org.commonlink.repository

import org.commonlink.entity.BeneficialOwner
import org.commonlink.entity.BeneficialOwnerType
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface BeneficialOwnerRepository : JpaRepository<BeneficialOwner, UUID> {

    /** Vrai si au moins une personne du [type] donné, non écartée, existe pour cette association. */
    fun existsByAssociationIdAndTypeAndDiscardedFalse(associationId: UUID, type: BeneficialOwnerType): Boolean

    fun findAllByAssociationIdOrderByCollectedAtAsc(associationId: UUID): List<BeneficialOwner>
}
