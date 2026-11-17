package org.commonlink.repository

import org.commonlink.entity.LegalDocument
import org.commonlink.entity.LegalDocumentType
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface LegalDocumentRepository : JpaRepository<LegalDocument, UUID> {

    /** The current version of [documentType] — the one most recently published. */
    fun findTopByDocumentTypeOrderByPublishedAtDesc(documentType: LegalDocumentType): LegalDocument?

    /** One exact version — backed by the `legal_document_type_version_uq` unique constraint (V73). */
    fun findByDocumentTypeAndVersion(documentType: LegalDocumentType, version: String): LegalDocument?
}
