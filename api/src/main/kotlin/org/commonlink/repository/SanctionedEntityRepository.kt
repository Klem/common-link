package org.commonlink.repository

import org.commonlink.entity.SanctionedEntity
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface SanctionedEntityRepository : JpaRepository<SanctionedEntity, UUID> {

    fun findByIdRegistre(idRegistre: Int): SanctionedEntity?

    fun findByNature(nature: String): List<SanctionedEntity>

    /** Used during re-ingestion to identify entries whose measure has been lifted from the register. */
    fun findByIdRegistreNotIn(ids: Collection<Int>): List<SanctionedEntity>
}
