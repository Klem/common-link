package org.commonlink.entity

import jakarta.persistence.*
import java.time.Instant
import java.util.UUID

/**
 * Evidence of a manual release of HelloAsso donations by a curator
 * ([org.commonlink.service.HelloAssoFundsReleaseService]): the reason, the association's bank statement line (as
 * text) and a file of that single line. Retention not defined yet (CEO question Q8): no purge in place. The compliance
 * journal references it by [id] only. [content] is loaded lazily.
 */
@Entity
@Table(name = "helloasso_funds_release_evidence")
class HelloAssoFundsReleaseEvidence(

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    val id: UUID = UUID.randomUUID(),

    @Column(name = "association_id", nullable = false, updatable = false)
    val associationId: UUID,

    /** Why the curator released the funds. */
    @Column(name = "reason", nullable = false, columnDefinition = "TEXT")
    val reason: String,

    /** The bank statement line showing the payout, copied as text. */
    @Column(name = "statement_line", nullable = false, columnDefinition = "TEXT")
    val statementLine: String,

    @Column(name = "file_name", nullable = false, length = 255)
    val fileName: String,

    /** Sniff-checked MIME type of [content] (PDF, JPEG or PNG). */
    @Column(name = "content_type", nullable = false, length = 100)
    val contentType: String,

    /** The file of the single statement line (BYTEA), never the whole statement. */
    @Basic(fetch = FetchType.LAZY)
    @Column(name = "content", nullable = false)
    val content: ByteArray,

    /** The curator. */
    @Column(name = "created_by", nullable = false, updatable = false)
    val createdBy: UUID,

    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: Instant = Instant.now(),
)
