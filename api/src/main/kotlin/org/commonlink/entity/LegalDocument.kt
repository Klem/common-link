package org.commonlink.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

/**
 * One immutable version of a CGU/CGV text.
 *
 * Rows are never updated or deleted — a new version is a new row. The "current" version of a
 * [documentType] is the one with the latest [publishedAt]
 * ([org.commonlink.repository.LegalDocumentRepository.findTopByDocumentTypeOrderByPublishedAtDesc]).
 * [LegalAcceptance.documentVersion] stores [version] as a plain string, not a foreign key: the
 * acceptance proof must remain readable even if this table were ever pruned.
 *
 * Since V97 the texts are injected at startup from the landing site
 * ([org.commonlink.bootstrap.LegalDocumentSeeder]): [version] is then `<effective date>-<first 12 hex of
 * [contentSha256]>`, so any change of text — even twice the same day — is a new version.
 */
@Entity
@Table(name = "legal_document")
class LegalDocument(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    val id: UUID? = null,

    @Enumerated(EnumType.STRING)
    @Column(name = "document_type", nullable = false, updatable = false, length = 10)
    val documentType: LegalDocumentType,

    /** Human-assigned version identifier (e.g. "2026-08-26"), unique per [documentType]. */
    @Column(name = "version", nullable = false, updatable = false, length = 32)
    val version: String,

    /** The text itself, to be read according to [contentFormat]. */
    @Column(name = "content", nullable = false, updatable = false, columnDefinition = "TEXT")
    val content: String,

    @Enumerated(EnumType.STRING)
    @Column(name = "content_format", nullable = false, updatable = false, length = 16)
    val contentFormat: LegalContentFormat = LegalContentFormat.TEXT,

    /** SHA-256 (lowercase hex) of [content]. Set on every `JSON_V1` row, null on the V73 placeholders. */
    @Column(name = "content_sha256", updatable = false, length = 64)
    val contentSha256: String? = null,

    @Column(name = "published_at", nullable = false, updatable = false)
    val publishedAt: Instant = Instant.now(),
)
