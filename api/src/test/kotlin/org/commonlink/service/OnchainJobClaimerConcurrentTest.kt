package org.commonlink.service

import org.commonlink.config.OnchainConfig
import org.commonlink.entity.OnchainJob
import org.commonlink.entity.OnchainJobAction
import org.commonlink.entity.OnchainJobStatus
import org.commonlink.repository.OnchainJobRepository
import org.commonlink.repository.TestcontainersConfig
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.testcontainers.context.ImportTestcontainers
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.TestPropertySource
import org.springframework.transaction.support.TransactionTemplate
import java.time.Instant
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors

/**
 * Exercises [OnchainJobClaimer.claimBatch] against a real PostgreSQL container: FOR UPDATE SKIP LOCKED
 * under concurrency, current-chain scoping, and job dependencies — plus the budget-job lookup
 * ([OnchainOutboxService.findLatestBudgetJob]: correlation-key equality / escaped LIKE prefix).
 */
@Tag("testcontainers")
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
    @Autowired private lateinit var outbox: OnchainOutboxService
    @Autowired private lateinit var cfg: OnchainConfig
    @Autowired private lateinit var txTemplate: TransactionTemplate

    private val insertedIds = mutableListOf<UUID>()

    private fun insert(
        chainId: Long = cfg.currentChainId(),
        status: OnchainJobStatus = OnchainJobStatus.PENDING,
        dependsOn: UUID? = null,
        key: String? = "CLAIMER-TEST-${UUID.randomUUID()}",
        action: OnchainJobAction = OnchainJobAction.REGISTER_ASSOCIATION,
        payloadJson: String = """{"address":"0x1","sirenHashHex":"0x01"}""",
        createdAt: Instant = Instant.now(),
    ): OnchainJob = txTemplate.execute {
        repo.save(
            OnchainJob(
                action = action,
                payloadJson = payloadJson,
                correlationKey = key,
                createdAt = createdAt,
                status = status,
                chainId = chainId,
                dependsOnJobId = dependsOn,
            )
        )
    }!!.also { insertedIds += it.id }

    /** Claims everything claimable and returns only the ids this test inserted. */
    private fun claimOwn(): Set<UUID> = claimer.claimBatch(500).map { it.id }.filter { it in insertedIds }.toSet()

    @AfterEach
    fun cleanup() {
        txTemplate.execute {
            // Dependents first (FK to their prerequisite).
            val jobs = repo.findAllById(insertedIds)
            repo.deleteAll(jobs.filter { it.dependsOnJobId != null })
            repo.deleteAll(jobs.filter { it.dependsOnJobId == null })
        }
        insertedIds.clear()
    }

    @Test
    fun `two concurrent claimBatch calls never return the same job id`() {
        repeat(6) { insert() }
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

    @Test
    fun `a job whose dependency is not DONE is not claimed and consumes no attempt`() {
        val prerequisite = insert(status = OnchainJobStatus.RUNNING)
        val dependent = insert(dependsOn = prerequisite.id)

        assertFalse(dependent.id in claimOwn())
        val reloaded = repo.findById(dependent.id).get()
        assertEquals(OnchainJobStatus.PENDING, reloaded.status)
        assertEquals(0, reloaded.attempts)
    }

    @Test
    fun `a job whose dependency is DONE is claimed`() {
        val prerequisite = insert(status = OnchainJobStatus.DONE)
        val dependent = insert(dependsOn = prerequisite.id)

        assertTrue(dependent.id in claimOwn())
    }

    @Test
    fun `a job whose dependency FAILED stays PENDING`() {
        val prerequisite = insert(status = OnchainJobStatus.FAILED)
        val dependent = insert(dependsOn = prerequisite.id)

        assertFalse(dependent.id in claimOwn())
        assertEquals(OnchainJobStatus.PENDING, repo.findById(dependent.id).get().status)
        assertTrue(repo.countBlockedByFailedDependency(cfg.currentChainId()) >= 1)
    }

    @Test
    fun `jobs of another chain are never claimed`() {
        val otherChain = insert(chainId = cfg.currentChainId() + 10_200)
        assertFalse(otherChain.id in claimOwn())
        assertEquals(OnchainJobStatus.PENDING, repo.findById(otherChain.id).get().status)
    }

    @Test
    fun `the same correlation key can be recorded once per chain`() {
        val key = "CLAIMER-TEST-SAME-KEY-${UUID.randomUUID()}"
        val onOtherChain = insert(chainId = cfg.currentChainId() + 10_200, key = key)

        val onCurrentChain = outbox.enqueue(OnchainJobAction.REGISTER_ASSOCIATION, mapOf("k" to "v"), key)
        insertedIds += onCurrentChain.id
        val replay = outbox.enqueue(OnchainJobAction.REGISTER_ASSOCIATION, mapOf("k" to "v"), key)

        assertNotEquals(onOtherChain.id, onCurrentChain.id, "a key known on another chain must not block the current one")
        assertEquals(onCurrentChain.id, replay.id, "idempotent on the current chain")
        assertEquals(cfg.currentChainId(), onCurrentChain.chainId)
    }

    @Test
    fun `findLatestBudgetJob returns the latest budget job of the campaign on the current chain`() {
        val campaignId = UUID.randomUUID()
        val t0 = Instant.now().minusSeconds(60)
        fun budgetJob(action: OnchainJobAction, campaign: UUID, at: Instant, chainId: Long = cfg.currentChainId()) = insert(
            chainId = chainId,
            key = if (action == OnchainJobAction.REGISTER_CAMPAIGN) "REGISTER_CAMPAIGN:$campaign"
                  else "UPDATE_CAMPAIGN_BUDGET:$campaign:${UUID.randomUUID()}",
            action = action,
            payloadJson = """{"campaignId":"$campaign","newBudgetHashHex":"0x02"}""",
            createdAt = at,
        )
        fun latest() = outbox.findLatestBudgetJob("REGISTER_CAMPAIGN:$campaignId", "UPDATE_CAMPAIGN_BUDGET:$campaignId:")
        assertEquals(null, latest())

        val registration = budgetJob(OnchainJobAction.REGISTER_CAMPAIGN, campaignId, t0)
        assertEquals(registration.id, latest()?.id)

        val update = budgetJob(OnchainJobAction.UPDATE_CAMPAIGN_BUDGET, campaignId, t0.plusSeconds(10))
        budgetJob(OnchainJobAction.UPDATE_CAMPAIGN_BUDGET, campaignId, t0.plusSeconds(20), cfg.currentChainId() + 10_200)
        budgetJob(OnchainJobAction.UPDATE_CAMPAIGN_BUDGET, UUID.randomUUID(), t0.plusSeconds(30))
        insert(
            key = "DONATION:${UUID.randomUUID()}",
            action = OnchainJobAction.RECORD_DONATION,
            payloadJson = """{"campaignId":"$campaignId"}""",
            createdAt = t0.plusSeconds(40),
        )

        assertEquals(update.id, latest()?.id, "other chain, other campaign and non-budget jobs are ignored")
    }
}
