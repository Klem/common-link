package org.commonlink.service

import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import org.commonlink.dto.HelloAssoLinkAttemptOutcomeCount
import org.commonlink.entity.HelloAssoLinkAttemptOutcome
import org.commonlink.exception.UnprocessableEntityException
import org.commonlink.repository.HelloAssoLinkAttemptRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.Pageable
import java.time.Duration
import java.time.Instant

/** [HelloAssoLinkAttemptStatsService]: every outcome listed, default period, input bounds. */
class HelloAssoLinkAttemptStatsServiceTest {

    private val repository = mockk<HelloAssoLinkAttemptRepository>()
    private val service = HelloAssoLinkAttemptStatsService(repository)
    private val from = Instant.parse("2026-11-01T00:00:00Z")
    private val to = Instant.parse("2026-12-01T00:00:00Z")

    @Test
    fun `stats lists every outcome in enum order, 0 for the absent ones`() {
        every { repository.countByOutcome(from, to) } returns listOf(
            HelloAssoLinkAttemptOutcomeCount(HelloAssoLinkAttemptOutcome.NAME_MISMATCH, 4),
            HelloAssoLinkAttemptOutcomeCount(HelloAssoLinkAttemptOutcome.MATCHED, 6),
        )
        every { repository.countAssociations(from, to) } returns 3

        val stats = service.stats(from, to)

        assertEquals(HelloAssoLinkAttemptOutcome.entries, stats.byOutcome.map { it.outcome })
        assertEquals(6, stats.byOutcome.first().count)
        assertEquals(0, stats.byOutcome.single { it.outcome == HelloAssoLinkAttemptOutcome.RNA_MISMATCH }.count)
        assertEquals(10, stats.total)
        assertEquals(3, stats.associations)
    }

    @Test
    fun `stats defaults to the last 30 days`() {
        val start = slot<Instant>()
        val end = slot<Instant>()
        every { repository.countByOutcome(capture(start), capture(end)) } returns emptyList()
        every { repository.countAssociations(any(), any()) } returns 0

        service.stats(null, null)

        assertEquals(HelloAssoLinkAttemptStatsService.DEFAULT_PERIOD, Duration.between(start.captured, end.captured))
    }

    @Test
    fun `a period whose start is not before its end is refused`() {
        assertThrows<UnprocessableEntityException> { service.stats(to, from) }
        assertThrows<UnprocessableEntityException> { service.list(to, to, null, 0, 20) }
    }

    @Test
    fun `list refuses a negative page or a size outside 1 to 100`() {
        assertThrows<UnprocessableEntityException> { service.list(from, to, null, -1, 20) }
        assertThrows<UnprocessableEntityException> { service.list(from, to, null, 0, 0) }
        assertThrows<UnprocessableEntityException> { service.list(from, to, null, 0, 101) }
    }

    @Test
    fun `list passes the outcome filter and paging to the repository`() {
        val pageable = slot<Pageable>()
        every { repository.findRows(from, to, HelloAssoLinkAttemptOutcome.MATCHED, capture(pageable)) } answers {
            PageImpl(emptyList(), pageable.captured, 0)
        }

        val page = service.list(from, to, HelloAssoLinkAttemptOutcome.MATCHED, 2, 25)

        assertEquals(2, pageable.captured.pageNumber)
        assertEquals(25, pageable.captured.pageSize)
        assertEquals(2, page.page)
    }
}
