package org.commonlink.dto

import java.time.Instant
import java.util.UUID

/**
 * Metadata for a supplementary (OPTIONAL) document uploaded by an association.
 *
 * @param id Document id.
 * @param fileName Original file name.
 * @param category Category: FINANCIAL | REPORT | SUPPORTING_DOC | OTHER.
 * @param contentType MIME type.
 * @param sizeBytes File size in bytes.
 * @param uploadedAt Upload timestamp.
 */
data class OptionalDocumentDto(
    val id: UUID,
    val fileName: String,
    val category: String?,
    val contentType: String,
    val sizeBytes: Long,
    val uploadedAt: Instant,
)
