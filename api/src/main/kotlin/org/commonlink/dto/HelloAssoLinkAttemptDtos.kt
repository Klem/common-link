package org.commonlink.dto

import org.commonlink.entity.HelloAssoLinkAttemptOutcome
import java.time.Instant
import java.util.UUID

/** Number of HelloAsso key checks that ended with [outcome]. */
data class HelloAssoLinkAttemptOutcomeCount(val outcome: HelloAssoLinkAttemptOutcome, val count: Long)

/**
 * Statistics of the HelloAsso key checks over `[from, to)` (curator screen).
 *
 * @property total every attempt of the period.
 * @property associations distinct associations that made at least one attempt.
 * @property byOutcome attempts per outcome; outcomes with no attempt are listed with 0, in enum order.
 */
data class HelloAssoLinkAttemptStatsResponse(
    val from: Instant,
    val to: Instant,
    val total: Long,
    val associations: Long,
    val byOutcome: List<HelloAssoLinkAttemptOutcomeCount>,
)

/**
 * One HelloAsso key check, as shown to curators. No secret: the client id is masked.
 *
 * @property organizationRna HelloAsso `rnaNumber` (null when blank); [associationRna] CommonLink's RNA at that moment.
 */
data class HelloAssoLinkAttemptRow(
    val id: UUID,
    val attemptedAt: Instant,
    val associationId: UUID,
    val associationName: String,
    val outcome: HelloAssoLinkAttemptOutcome,
    val clientIdMasked: String?,
    val organizationsCount: Int?,
    val organizationSlug: String?,
    val organizationName: String?,
    val organizationType: String?,
    val organizationRna: String?,
    val associationRna: String?,
)

/** A page of [HelloAssoLinkAttemptRow], newest first. */
data class HelloAssoLinkAttemptPageResponse(
    val content: List<HelloAssoLinkAttemptRow>,
    val page: Int,
    val size: Int,
    val totalElements: Long,
    val totalPages: Int,
)
