package org.commonlink.repository

import org.commonlink.entity.AssociationDocument
import org.commonlink.entity.AssociationDocumentType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.Instant
import java.util.UUID

/**
 * Metadata-only projection for [AssociationDocument] listing queries.
 *
 * Intentionally excludes [AssociationDocument.content] (BYTEA) so that listing calls
 * never load file contents from the database. Use [AssociationDocumentRepository.findContentById]
 * exclusively for download operations.
 */
interface AssociationDocumentMetadata {
    val id: UUID?
    val docType: AssociationDocumentType
    val category: String?
    val fileName: String
    val contentType: String
    val sizeBytes: Long
    val uploadedAt: Instant
}

interface AssociationDocumentRepository : JpaRepository<AssociationDocument, UUID> {

    /**
     * Returns metadata for all documents belonging to an association, without loading content.
     * Backed by [idx_association_document_association_id].
     */
    @Query("""
        SELECT d.id AS id, d.docType AS docType, d.category AS category,
               d.fileName AS fileName, d.contentType AS contentType,
               d.sizeBytes AS sizeBytes, d.uploadedAt AS uploadedAt
        FROM AssociationDocument d
        WHERE d.association.id = :associationId
    """)
    fun findMetadataByAssociationId(@Param("associationId") associationId: UUID): List<AssociationDocumentMetadata>

    /**
     * Returns metadata for a single document slot, without loading content.
     */
    @Query("""
        SELECT d.id AS id, d.docType AS docType, d.category AS category,
               d.fileName AS fileName, d.contentType AS contentType,
               d.sizeBytes AS sizeBytes, d.uploadedAt AS uploadedAt
        FROM AssociationDocument d
        WHERE d.association.id = :associationId AND d.docType = :docType
    """)
    fun findMetadataByAssociationIdAndDocType(
        @Param("associationId") associationId: UUID,
        @Param("docType") docType: AssociationDocumentType,
    ): AssociationDocumentMetadata?

    /** Checks whether a document slot is filled for the given association. */
    fun existsByAssociationIdAndDocType(associationId: UUID, docType: AssociationDocumentType): Boolean

    /** Deletes a specific document slot (used for replace/delete operations). */
    fun deleteByAssociationIdAndDocType(associationId: UUID, docType: AssociationDocumentType)
}
