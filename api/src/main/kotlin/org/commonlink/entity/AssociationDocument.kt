package org.commonlink.entity

import jakarta.persistence.*
import java.time.Instant
import java.util.UUID

/**
 * A document uploaded by an association, used either for KYC verification or fiscal mandate.
 *
 * The [content] field holds the raw binary (BYTEA). It is mapped with [FetchType.LAZY] so that
 * listing queries never load the file content — only a dedicated download query fetches it.
 * Use [org.commonlink.repository.AssociationDocumentRepository.findContentById] for downloads.
 *
 * Uniqueness: at most one document per `(association, docType)` for all types except [AssociationDocumentType.OPTIONAL],
 * enforced by a partial unique index in the database (see V35 migration). A replacement upload
 * deletes the existing row before inserting the new one at the service layer.
 */
@Entity
@Table(name = "association_document")
class AssociationDocument(

    /** Auto-generated UUID primary key. */
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    val id: UUID? = null,

    /** The association this document belongs to. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "association_id", nullable = false)
    val association: AssociationProfile,

    /** Logical document category (KYC slot, mandate slot, or optional). */
    @Enumerated(EnumType.STRING)
    @Column(name = "doc_type", nullable = false, length = 30)
    val docType: AssociationDocumentType,

    /** Sub-category for [AssociationDocumentType.OPTIONAL] documents (financier, rapport, etc.). */
    @Column(name = "category", length = 20)
    val category: String? = null,

    /** Original file name as uploaded by the user. */
    @Column(name = "file_name", nullable = false, length = 255)
    val fileName: String,

    /** MIME type of the uploaded file (e.g. application/pdf). */
    @Column(name = "content_type", nullable = false, length = 100)
    val contentType: String,

    /** File size in bytes. */
    @Column(name = "size_bytes", nullable = false)
    val sizeBytes: Long,

    /**
     * Raw file content stored as BYTEA.
     * Loaded lazily — never fetched by listing queries.
     */
    @Basic(fetch = FetchType.LAZY)
    @Column(name = "content", nullable = false)
    val content: ByteArray,

    /** Timestamp when the document was uploaded. */
    @Column(name = "uploaded_at", nullable = false)
    val uploadedAt: Instant,
)
