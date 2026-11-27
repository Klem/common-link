package org.commonlink.service

import org.commonlink.dto.HelloAssoLinkAttemptOutcomeCount
import org.commonlink.dto.HelloAssoLinkAttemptPageResponse
import org.commonlink.dto.HelloAssoLinkAttemptStatsResponse
import org.commonlink.entity.HelloAssoLinkAttemptOutcome
import org.commonlink.exception.UnprocessableEntityException
import org.commonlink.repository.HelloAssoLinkAttemptRepository
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Duration
import java.time.Instant

/**
 * Curator read side of the HelloAsso key-check journal ([org.commonlink.entity.HelloAssoLinkAttempt]): counts per
 * outcome over a period, and the attempts themselves. Read only.
 */
@Service
class HelloAssoLinkAttemptStatsService(
    private val repository: HelloAssoLinkAttemptRepository,
) {
    companion object {
        /** Period used when the caller gives no `from`. */
        val DEFAULT_PERIOD: Duration = Duration.ofDays(30)

        /** Largest page of attempts. */
        const val MAX_PAGE_SIZE = 100
    }

    /**
     * Attempts per outcome over `[from, to)`; every outcome is listed, 0 included, in enum order.
     *
     * @param from period start; defaults to [to] minus [DEFAULT_PERIOD].
     * @param to period end (exclusive); defaults to now.
     * @throws UnprocessableEntityException when `from` is not before `to`.
     */
    @Transactional(readOnly = true)
    fun stats(from: Instant?, to: Instant?): HelloAssoLinkAttemptStatsResponse {
        val (start, end) = period(from, to)
        val counts = repository.countByOutcome(start, end).associate { it.outcome to it.count }
        val byOutcome = HelloAssoLinkAttemptOutcome.entries.map { HelloAssoLinkAttemptOutcomeCount(it, counts[it] ?: 0L) }
        return HelloAssoLinkAttemptStatsResponse(
            from = start,
            to = end,
            total = byOutcome.sumOf { it.count },
            associations = repository.countAssociations(start, end),
            byOutcome = byOutcome,
        )
    }

    /**
     * Attempts over `[from, to)`, newest first, optionally of one [outcome].
     *
     * @param page zero-based page.
     * @param size page size, capped at [MAX_PAGE_SIZE].
     * @throws UnprocessableEntityException when `from` is not before `to`, or [page]/[size] are out of range.
     */
    @Transactional(readOnly = true)
    fun list(
        from: Instant?,
        to: Instant?,
        outcome: HelloAssoLinkAttemptOutcome?,
        page: Int,
        size: Int,
    ): HelloAssoLinkAttemptPageResponse {
        if (page < 0 || size !in 1..MAX_PAGE_SIZE) {
            throw UnprocessableEntityException("page must be >= 0 and size between 1 and $MAX_PAGE_SIZE")
        }
        val (start, end) = period(from, to)
        val result = repository.findRows(start, end, outcome, PageRequest.of(page, size))
        return HelloAssoLinkAttemptPageResponse(
            content = result.content,
            page = result.number,
            size = result.size,
            totalElements = result.totalElements,
            totalPages = result.totalPages,
        )
    }

    private fun period(from: Instant?, to: Instant?): Pair<Instant, Instant> {
        val end = to ?: Instant.now()
        val start = from ?: end.minus(DEFAULT_PERIOD)
        if (!start.isBefore(end)) throw UnprocessableEntityException("from must be before to")
        return start to end
    }
}
