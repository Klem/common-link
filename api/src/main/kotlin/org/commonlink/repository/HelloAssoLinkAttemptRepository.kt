package org.commonlink.repository

import org.commonlink.dto.HelloAssoLinkAttemptOutcomeCount
import org.commonlink.dto.HelloAssoLinkAttemptRow
import org.commonlink.entity.HelloAssoLinkAttempt
import org.commonlink.entity.HelloAssoLinkAttemptOutcome
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.Instant
import java.util.UUID

/** Append-only persistence of [HelloAssoLinkAttempt]: inserts and reads only. */
interface HelloAssoLinkAttemptRepository : JpaRepository<HelloAssoLinkAttempt, UUID> {

    /** Attempts per outcome in `[from, to)` (index `idx_helloasso_link_attempts_attempted_at`). */
    @Query(
        "SELECT new org.commonlink.dto.HelloAssoLinkAttemptOutcomeCount(t.outcome, COUNT(t)) " +
            "FROM HelloAssoLinkAttempt t " +
            "WHERE t.attemptedAt >= :from AND t.attemptedAt < :to " +
            "GROUP BY t.outcome",
    )
    fun countByOutcome(@Param("from") from: Instant, @Param("to") to: Instant): List<HelloAssoLinkAttemptOutcomeCount>

    /** Distinct associations that made an attempt in `[from, to)`. */
    @Query(
        "SELECT COUNT(DISTINCT t.associationId) FROM HelloAssoLinkAttempt t " +
            "WHERE t.attemptedAt >= :from AND t.attemptedAt < :to",
    )
    fun countAssociations(@Param("from") from: Instant, @Param("to") to: Instant): Long

    /** Attempts in `[from, to)`, newest first, with the association's name; [outcome] null = every outcome. */
    @Query(
        value = "SELECT new org.commonlink.dto.HelloAssoLinkAttemptRow(" +
            "t.id, t.attemptedAt, t.associationId, a.name, t.outcome, t.clientIdMasked, t.organizationsCount, " +
            "t.organizationSlug, t.organizationName, t.organizationType, t.organizationRna, t.associationRna) " +
            "FROM HelloAssoLinkAttempt t JOIN AssociationProfile a ON a.id = t.associationId " +
            "WHERE t.attemptedAt >= :from AND t.attemptedAt < :to " +
            "AND (:outcome IS NULL OR t.outcome = :outcome) " +
            "ORDER BY t.attemptedAt DESC",
        countQuery = "SELECT COUNT(t) FROM HelloAssoLinkAttempt t " +
            "WHERE t.attemptedAt >= :from AND t.attemptedAt < :to " +
            "AND (:outcome IS NULL OR t.outcome = :outcome)",
    )
    fun findRows(
        @Param("from") from: Instant,
        @Param("to") to: Instant,
        @Param("outcome") outcome: HelloAssoLinkAttemptOutcome?,
        pageable: Pageable,
    ): Page<HelloAssoLinkAttemptRow>
}
