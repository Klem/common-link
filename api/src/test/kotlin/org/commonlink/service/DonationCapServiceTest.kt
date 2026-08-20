package org.commonlink.service

import io.mockk.every
import io.mockk.mockk
import org.commonlink.config.DonationCapProperties
import org.commonlink.entity.Campaign
import org.commonlink.exception.CollectionCapExceededException
import org.commonlink.repository.DonationRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.assertThrows
import java.math.BigDecimal
import java.time.Duration
import java.time.Instant
import java.util.UUID

/**
 * Unit tests for the collection cap.
 *
 * The invariant under test is negative: a donation that would push a campaign past its cap must be
 * refused *before* the payment is created, because the alternative — refunding afterwards — is what
 * the cap exists to prevent.
 */
class DonationCapServiceTest {

    private val donationRepository = mockk<DonationRepository>()

    private fun service(marginPercent: String = "10", ttl: Duration = Duration.ofMinutes(30)) =
        DonationCapService(
            donationRepository,
            DonationCapProperties(
                marginPercent = BigDecimal(marginPercent),
                reservationTtl = ttl,
                maxPendingSessions = MAX_SESSIONS,
            ),
        )

    /** Campaign with the given goal and confirmed total; id is needed for the pending-sum lookup. */
    private fun campaign(goal: String, raised: String): Campaign =
        mockk<Campaign>(relaxed = true).also {
            every { it.id } returns UUID.randomUUID()
            every { it.goal } returns BigDecimal(goal)
            every { it.raised } returns BigDecimal(raised)
        }

    /**
     * Stubs the open-session lookups: [amount] is the capacity they hold, [sessions] how many rows
     * that is. The count backs the pending-session ceiling (audit 2026-08-20, M6) and defaults to a
     * value well below it, so tests about the amount stay about the amount.
     */
    private fun pending(amount: String?, sessions: Long = 0) {
        every { donationRepository.sumPendingAmountByCampaignIdSince(any(), any()) } returns
            amount?.let { BigDecimal(it) }
        every { donationRepository.countPendingByCampaignIdSince(any(), any()) } returns sessions
    }

    @Test
    fun `cap is the goal widened by the configured margin`() {
        pending(null)
        val c = campaign(goal = "1000", raised = "0")

        assertEquals(0, BigDecimal("1100.00").compareTo(service("10").cap(c)))
        assertEquals(0, BigDecimal("1000.00").compareTo(service("0").cap(c)))
        assertEquals(0, BigDecimal("1025.00").compareTo(service("2.5").cap(c)))
    }

    @Test
    fun `remaining capacity subtracts confirmed donations and open sessions`() {
        pending("50")
        val c = campaign(goal = "1000", raised = "900")

        // cap 1100 − raised 900 − reserved 50
        assertEquals(0, BigDecimal("150.00").compareTo(service().remainingCapacity(c)))
    }

    /** A null sum (no pending rows at all) must read as zero, not blow up. */
    @Test
    fun `no pending donation means nothing is reserved`() {
        pending(null)
        val c = campaign(goal = "1000", raised = "1000")

        assertEquals(0, BigDecimal("100.00").compareTo(service().remainingCapacity(c)))
    }

    /** A campaign already past its cap has no capacity — never a negative one. */
    @Test
    fun `remaining capacity never goes negative`() {
        pending("0")
        val c = campaign(goal = "1000", raised = "5000")

        assertEquals(0, BigDecimal.ZERO.compareTo(service().remainingCapacity(c)))
    }

    @Test
    fun `a donation below the remaining capacity is accepted`() {
        pending("0")
        val c = campaign(goal = "1000", raised = "1000")

        assertDoesNotThrow { service().requireWithinCap(c, BigDecimal("99")) }
    }

    /** The boundary is inclusive: an amount landing exactly on the cap goes through. */
    @Test
    fun `a donation landing exactly on the cap is accepted`() {
        pending("0")
        val c = campaign(goal = "1000", raised = "1000")

        assertDoesNotThrow { service().requireWithinCap(c, BigDecimal("100")) }
    }

    @Test
    fun `a donation one cent above the cap is refused with the remaining capacity`() {
        pending("0")
        val c = campaign(goal = "1000", raised = "1000")

        val ex = assertThrows<CollectionCapExceededException> {
            service().requireWithinCap(c, BigDecimal("100.01"))
        }
        assertEquals(0, BigDecimal("100.00").compareTo(ex.remainingCapacity))
    }

    /**
     * The reservation is the point of the whole mechanism: without it two donors checking out at the
     * same time would each be told the full remaining capacity is theirs, and together overshoot.
     */
    @Test
    fun `an open payment session holds capacity against a second donor`() {
        pending("100")
        val c = campaign(goal = "1000", raised = "1000")

        assertThrows<CollectionCapExceededException> {
            service().requireWithinCap(c, BigDecimal("100"))
        }
    }

    /** With a zero margin the goal is a hard ceiling. */
    @Test
    fun `a zero margin refuses any overshoot`() {
        pending("0")
        val c = campaign(goal = "1000", raised = "1000")

        assertThrows<CollectionCapExceededException> {
            service("0").requireWithinCap(c, BigDecimal("0.01"))
        }
    }

    /**
     * The reservation window is bounded by the configured TTL, not open-ended: an abandoned checkout
     * must eventually release its hold. Asserted on the cutoff passed to the repository, since that
     * is where the bound is applied.
     */
    @Test
    fun `pending donations are only counted within the reservation window`() {
        val cutoffs = mutableListOf<Instant>()
        every { donationRepository.sumPendingAmountByCampaignIdSince(any(), capture(cutoffs)) } returns BigDecimal.ZERO
        val c = campaign(goal = "1000", raised = "0")

        service(ttl = Duration.ofMinutes(30)).remainingCapacity(c)

        val elapsed = Duration.between(cutoffs.single(), Instant.now())
        assert(elapsed >= Duration.ofMinutes(30)) { "cutoff must be at least the TTL in the past, was $elapsed" }
        assert(elapsed < Duration.ofMinutes(31)) { "cutoff must not exceed the TTL, was $elapsed" }
    }

    /**
     * Pending-session ceiling (audit 2026-08-20, M6). The widget endpoint is unauthenticated, so
     * capacity alone is not a sufficient guard: an outsider opening session after session would hold
     * a campaign's whole remaining capacity hostage for the reservation TTL. The ceiling refuses on
     * the count, independently of how much room is left.
     */
    @Test
    fun `a campaign already at the pending-session ceiling refuses a donation with room to spare`() {
        pending("0", sessions = MAX_SESSIONS.toLong())
        val c = campaign(goal = "1000", raised = "0")

        val ex = assertThrows<CollectionCapExceededException> {
            service().requireWithinCap(c, BigDecimal("10"))
        }
        assertEquals(
            0,
            BigDecimal.ZERO.compareTo(ex.remainingCapacity),
            "No capacity is offered while capped on sessions",
        )
    }

    @Test
    fun `a campaign just below the ceiling still accepts a donation`() {
        pending("0", sessions = MAX_SESSIONS.toLong() - 1)
        val c = campaign(goal = "1000", raised = "0")

        service().requireWithinCap(c, BigDecimal("10"))
    }

    private companion object {
        const val MAX_SESSIONS = 50
    }
}
