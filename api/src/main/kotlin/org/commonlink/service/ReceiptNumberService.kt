package org.commonlink.service

import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Service
import java.util.UUID

/**
 * Issues per-association-per-year sequential receipt numbers (e.g. `2026-0042`).
 *
 * Uses a PostgreSQL `INSERT … ON CONFLICT DO UPDATE … RETURNING` to atomically
 * claim the next sequence value without a separate SELECT + UPDATE round-trip.
 * Thread-safe and safe under concurrent calls within the same JVM.
 */
@Service
class ReceiptNumberService(private val jdbc: JdbcTemplate) {

    /**
     * Returns the next receipt number for [associationId] in [year], e.g. `"2026-0042"`.
     *
     * Atomically increments the counter. Creates the row on first call for this
     * (association, year) pair. Safe to call concurrently — Postgres serialises the upsert.
     */
    fun nextNumber(associationId: UUID, year: Int): String {
        val seq = jdbc.queryForObject(
            """
            INSERT INTO receipt_seq (association_id, year, last_seq)
            VALUES (?, ?, 1)
            ON CONFLICT (association_id, year)
            DO UPDATE SET last_seq = receipt_seq.last_seq + 1
            RETURNING last_seq
            """,
            Int::class.java,
            associationId,
            year.toShort(),
        )!!
        return "$year-${seq.toString().padStart(4, '0')}"
    }
}
