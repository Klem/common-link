package org.commonlink.repository

import org.commonlink.entity.SanctionedEntity
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import java.time.LocalDate
import java.util.UUID

interface SanctionedEntityRepository : JpaRepository<SanctionedEntity, UUID> {

    fun findByIdRegistre(idRegistre: Int): SanctionedEntity?

    fun findByNature(nature: String): List<SanctionedEntity>

    /** Used during re-ingestion to identify entries whose measure has been lifted from the register. */
    fun findByIdRegistreNotIn(ids: Collection<Int>): List<SanctionedEntity>

    /**
     * Returns the publication date of the most recently ingested register snapshot, or null when
     * the register table is empty (ingestion has not yet run). Used by callers of
     * [org.commonlink.service.ComplianceAuditLogService.appendFreezeScreeningClear] and
     * [org.commonlink.service.ComplianceAuditLogService.appendFreezeScreeningHit] to record which
     * version of the register was consulted.
     */
    @Query("SELECT MAX(e.publicationDate) FROM SanctionedEntity e")
    fun findMaxPublicationDate(): LocalDate?
}
