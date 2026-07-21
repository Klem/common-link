package org.commonlink.entity

import jakarta.persistence.*
import java.time.Instant
import java.util.UUID

/**
 * Stores the generated Cerfa 2041-RD PDF for a confirmed donation.
 *
 * Created once in [org.commonlink.service.DonationReceiptService.enqueueOnchainJob],
 * before the on-chain job is enqueued. The [pdfBytes] are the exact bytes whose
 * keccak256 hash is written on-chain — they must never be regenerated after the fact.
 *
 * [emailedAt] is null until the receipt has been sent to the donor. It acts as a
 * guard against duplicate delivery when the on-chain job retries.
 */
@Entity
@Table(name = "donation_receipts")
class DonationReceipt(

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    val id: UUID? = null,

    /** The donation this receipt covers. Unique — one receipt per donation. */
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "donation_id", nullable = false, unique = true)
    val donation: Donation,

    /** Sequential receipt number in the format `yyyy-NNNN` (e.g. `2026-0042`). Per-association-per-year. */
    @Column(name = "receipt_number", nullable = false, length = 20)
    val receiptNumber: String,

    /** Raw PDF bytes. Deterministic for a given donation — hash matches what is stored on-chain. */
    @Column(name = "pdf_bytes", nullable = false, columnDefinition = "BYTEA")
    val pdfBytes: ByteArray,

    /** Server timestamp when the PDF was generated. */
    @Column(name = "generated_at", nullable = false)
    val generatedAt: Instant,

    /** Set once the receipt email has been delivered to the donor. Null = not yet sent. */
    @Column(name = "emailed_at")
    var emailedAt: Instant? = null,
)
