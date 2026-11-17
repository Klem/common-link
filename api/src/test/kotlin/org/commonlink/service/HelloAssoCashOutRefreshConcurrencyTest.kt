package org.commonlink.service

import com.ninjasquad.springmockk.MockkBean
import io.mockk.every
import io.mockk.verify
import org.commonlink.repository.AssociationProfileRepository
import org.commonlink.repository.RealPostgresTest
import org.commonlink.repository.TestFixtures
import org.commonlink.repository.UserRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import java.util.UUID
import java.util.concurrent.CyclicBarrier
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/** The refresh guard holds under concurrency on real PostgreSQL: N simultaneous requests run a single pass. */
class HelloAssoCashOutRefreshConcurrencyTest : RealPostgresTest() {

    @Autowired private lateinit var refreshService: HelloAssoCashOutRefreshService
    @Autowired private lateinit var userRepository: UserRepository
    @Autowired private lateinit var associationProfileRepository: AssociationProfileRepository
    @MockkBean private lateinit var executor: HelloAssoCashOutSyncExecutor

    @Test
    fun `simultaneous refresh requests of one association run a single pass`() {
        val tag = UUID.randomUUID().toString().take(8)
        val associationId = associationProfileRepository.save(
            TestFixtures.associationProfile(
                userRepository.save(TestFixtures.associationUser(email = "refresh-$tag@example.com")),
                identifier = "9${tag.hashCode().mod(100_000_000).toString().padStart(8, '0')}",
            ),
        ).id!!
        every { executor.syncAssociation(associationId) } answers { Thread.sleep(200); CashOutSyncResult(null, null) }
        every { executor.resultOf(associationId) } returns CashOutSyncResult(null, null)

        val requests = 8
        val barrier = CyclicBarrier(requests)
        val pool = Executors.newFixedThreadPool(requests)
        try {
            val outcomes = List(requests) {
                pool.submit<CashOutRefreshOutcome> {
                    barrier.await(10, TimeUnit.SECONDS)
                    refreshService.requestRefresh(associationId)
                }
            }.map { it.get(30, TimeUnit.SECONDS) }

            verify(exactly = 1) { executor.syncAssociation(associationId) }
            assertEquals(1, outcomes.count { it.refreshed })
            assertEquals(1, outcomes.map { it.nextRefreshAvailableAt }.distinct().size)
        } finally {
            pool.shutdownNow()
        }
    }
}
