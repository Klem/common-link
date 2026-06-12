package org.commonlink.service

import org.commonlink.entity.OnchainJob
import org.commonlink.entity.OnchainJobAction
import org.commonlink.repository.OnchainJobRepository
import org.commonlink.repository.TestcontainersConfig
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.testcontainers.context.ImportTestcontainers
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.TestPropertySource
import org.springframework.transaction.support.TransactionTemplate
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors

/**
 * Proves that two concurrent [OnchainJobClaimer.claimBatch] calls never return the same job id.
 * Uses a real PostgreSQL container so FOR UPDATE SKIP LOCKED semantics are exercised end-to-end.
 */
@SpringBootTest
@ImportTestcontainers(TestcontainersConfig::class)
@ActiveProfiles("test")
@TestPropertySource(properties = [
    "app.jwt.secret=test-secret-key-must-be-at-least-32-chars!!",
    "app.frontend-url=http://localhost:3000",
    "app.vop.demo-mode=true",
])
class OnchainJobClaimerConcurrentTest {

    @Autowired private lateinit var claimer: OnchainJobClaimer
    @Autowired private lateinit var repo: OnchainJobRepository
    @Autowired private lateinit var txTemplate: TransactionTemplate

    private val insertedIds = mutableListOf<UUID>()

    @BeforeEach
    fun insertPendingJobs() {
        txTemplate.execute {
            repeat(6) { i ->
                val job = repo.save(
                    OnchainJob(
                        action = OnchainJobAction.VERIFY_ASSOCIATION,
                        payloadJson = """{"address":"0x${"0".repeat(39)}$i"}""",
                        correlationKey = "CONCURRENT-TEST-${UUID.randomUUID()}",
                    )
                )
                insertedIds += job.id
            }
        }
    }

    @AfterEach
    fun cleanup() {
        txTemplate.execute { repo.deleteAllById(insertedIds) }
        insertedIds.clear()
    }

    @Test
    fun `two concurrent claimBatch calls never return the same job id`() {
        val ready = CountDownLatch(2)
        val executor = Executors.newFixedThreadPool(2)

        fun launchWorker() = executor.submit<List<UUID>> {
            ready.countDown()
            ready.await()  // both threads start simultaneously
            claimer.claimBatch(6).map { it.id }
        }

        val f1 = launchWorker()
        val f2 = launchWorker()

        val ids1 = f1.get()
        val ids2 = f2.get()
        executor.shutdown()

        val all = ids1 + ids2
        assertEquals(
            all.size, all.toSet().size,
            "Jobs dispatched by both workers (overlap): ${ids1.toSet().intersect(ids2.toSet())}",
        )
    }
}
