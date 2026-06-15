package org.commonlink.service

import org.commonlink.dto.CreateCampaignRequest
import org.commonlink.dto.CreateMilestoneRequest
import org.commonlink.dto.ReorderMilestonesRequest
import org.commonlink.dto.SaveBudgetRequest
import org.commonlink.dto.SaveBudgetItemRequest
import org.commonlink.dto.SaveBudgetSectionRequest
import org.commonlink.dto.UpdateCampaignRequest
import org.commonlink.dto.UpdateMilestoneRequest
import org.commonlink.entity.BudgetSide
import org.commonlink.entity.CampaignStatus
import org.commonlink.entity.MilestoneStatus
import org.commonlink.entity.MoneriumConnection
import org.commonlink.entity.MoneriumConnectionState
import org.commonlink.entity.OnchainJobAction
import org.commonlink.exception.NotFoundException
import org.commonlink.exception.UnprocessableEntityException
import org.commonlink.repository.AssociationProfileRepository
import org.commonlink.repository.MoneriumConnectionRepository
import org.commonlink.repository.OnchainJobRepository
import org.commonlink.repository.TestFixtures
import org.commonlink.repository.TestcontainersConfig
import org.commonlink.repository.UserRepository
import org.hibernate.SessionFactory
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.testcontainers.context.ImportTestcontainers
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.TestPropertySource
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.util.UUID
import jakarta.persistence.EntityManager
import jakarta.persistence.EntityManagerFactory

/**
 * Integration tests for [CampaignService] using a real PostgreSQL container via Testcontainers.
 *
 * Each test runs in a transaction that is rolled back after completion, so the database is
 * always in a clean state between tests.
 */
@SpringBootTest
@ImportTestcontainers(TestcontainersConfig::class)
@ActiveProfiles("test")
@TestPropertySource(properties = [
    "app.jwt.secret=test-secret-key-must-be-at-least-32-chars!!",
    "app.frontend-url=http://localhost:3000",
    "app.vop.demo-mode=true"
])
@Transactional
class CampaignServiceTest {

    @Autowired
    private lateinit var campaignService: CampaignService

    @Autowired
    private lateinit var userRepository: UserRepository

    @Autowired
    private lateinit var associationProfileRepository: AssociationProfileRepository

    @Autowired
    private lateinit var moneriumConnectionRepository: MoneriumConnectionRepository

    @Autowired
    private lateinit var onchainJobRepository: OnchainJobRepository

    @Autowired
    private lateinit var entityManager: EntityManager

    @Autowired
    private lateinit var entityManagerFactory: EntityManagerFactory

    private lateinit var userId: UUID
    private lateinit var otherUserId: UUID

    @BeforeEach
    fun setUp() {
        val user = userRepository.save(TestFixtures.associationUser())
        associationProfileRepository.save(TestFixtures.associationProfile(user))
        userId = user.id!!

        val otherUser = userRepository.save(TestFixtures.associationUser(email = "other@example.com"))
        associationProfileRepository.save(TestFixtures.associationProfile(otherUser, identifier = "123456789"))
        otherUserId = otherUser.id!!
    }

    /** Links an ACTIVE Monerium wallet to the association, satisfying the publish-time gate. */
    private fun linkMonerium(ownerId: UUID) {
        val assoc = associationProfileRepository.findByUserId(ownerId).get()
        moneriumConnectionRepository.save(MoneriumConnection(
            association  = assoc,
            walletAddress = "0xdeadbeefdeadbeefdeadbeefdeadbeefdeadbeef",
            accessToken  = "tok",
            refreshToken = "ref",
            expiresAt    = java.time.Instant.now().plusSeconds(3600),
            state        = MoneriumConnectionState.ACTIVE,
        ))
    }

    // ── listCampaigns ─────────────────────────────────────────────────────────

    @Test
    fun `listCampaigns - returns empty list for new association`() {
        val result = campaignService.listCampaigns(userId)

        assertTrue(result.isEmpty())
    }

    @Test
    fun `listCampaigns - returns campaigns in descending creation-date order`() {
        val older = campaignService.createCampaign(userId, CreateCampaignRequest(name = "Older Campaign"))
        val newer = campaignService.createCampaign(userId, CreateCampaignRequest(name = "Newer Campaign"))

        // Force distinct created_at so order is deterministic regardless of execution speed
        val tOld = java.sql.Timestamp.from(java.time.Instant.now().minusSeconds(60))
        val tNew = java.sql.Timestamp.from(java.time.Instant.now())
        entityManager.createNativeQuery("UPDATE campaigns SET created_at = :ts WHERE id = :id")
            .setParameter("ts", tOld).setParameter("id", older.id).executeUpdate()
        entityManager.createNativeQuery("UPDATE campaigns SET created_at = :ts WHERE id = :id")
            .setParameter("ts", tNew).setParameter("id", newer.id).executeUpdate()
        entityManager.flush()
        entityManager.clear()

        val result = campaignService.listCampaigns(userId)

        assertEquals(2, result.size)
        assertEquals("Newer Campaign", result[0].name)
        assertEquals("Older Campaign", result[1].name)
    }

    // ── createCampaign ────────────────────────────────────────────────────────

    @Test
    fun `createCampaign - creates and returns campaign with empty budget and milestones`() {
        val req = CreateCampaignRequest(
            name = "Hiver Solidaire 2025",
            emoji = "🌍",
            description = "Campagne test",
            goal = BigDecimal("40000")
        )

        val result = campaignService.createCampaign(userId, req)

        assertNotNull(result.id)
        assertEquals("Hiver Solidaire 2025", result.name)
        assertEquals("🌍", result.emoji)
        assertEquals(BigDecimal("40000"), result.goal)
        assertEquals(CampaignStatus.DRAFT, result.status)
        assertTrue(result.budgetSections.isEmpty())
        assertTrue(result.milestones.isEmpty())
    }

    // ── getCampaign ───────────────────────────────────────────────────────────

    @Test
    fun `getCampaign - returns campaign with budget sections and milestones`() {
        val created = campaignService.createCampaign(
            userId,
            CreateCampaignRequest(name = "Test Campaign", goal = BigDecimal("10000"))
        )
        val budgetReq = SaveBudgetRequest(
            sections = listOf(
                SaveBudgetSectionRequest(
                    side = BudgetSide.EXPENSE,
                    code = "60",
                    name = "Achats",
                    sortOrder = 0,
                    items = listOf(SaveBudgetItemRequest(label = "Fournitures", amount = BigDecimal("500")))
                )
            )
        )
        campaignService.saveBudget(userId, created.id, budgetReq)
        campaignService.addMilestone(
            userId,
            created.id,
            CreateMilestoneRequest(title = "Étape 1", targetAmount = BigDecimal("5000"))
        )

        // Flush and clear session so getCampaign loads fresh data from DB
        entityManager.flush()
        entityManager.clear()

        val result = campaignService.getCampaign(userId, created.id)

        assertEquals(created.id, result.id)
        assertEquals(1, result.budgetSections.size)
        assertEquals(1, result.milestones.size)
        assertEquals(1, result.budgetSections[0].items.size)
    }

    @Test
    fun `getCampaign - loads detail in bounded queries with no N+1 on section items`() {
        val campaign = campaignService.createCampaign(
            userId, CreateCampaignRequest(name = "N+1 Test", goal = BigDecimal("30000"))
        )
        campaignService.saveBudget(userId, campaign.id, SaveBudgetRequest(
            sections = (1..3).map { s ->
                SaveBudgetSectionRequest(
                    side = BudgetSide.EXPENSE, code = "SEC$s", name = "Section $s", sortOrder = s - 1,
                    items = listOf(
                        SaveBudgetItemRequest(label = "Item ${s}A", amount = BigDecimal("100"), sortOrder = 0),
                        SaveBudgetItemRequest(label = "Item ${s}B", amount = BigDecimal("200"), sortOrder = 1),
                    )
                )
            }
        ))
        repeat(2) { i ->
            campaignService.addMilestone(userId, campaign.id, CreateMilestoneRequest(title = "M$i", sortOrder = i))
        }

        entityManager.flush()
        entityManager.clear()

        val sf = entityManagerFactory.unwrap(SessionFactory::class.java)
        sf.statistics.isStatisticsEnabled = true
        sf.statistics.clear()

        val result = campaignService.getCampaign(userId, campaign.id)

        val stmtCount = sf.statistics.prepareStatementCount
        assertTrue(stmtCount <= 5, "Expected ≤5 SQL statements (no N+1), got $stmtCount")
        assertEquals(3, result.budgetSections.size)
        assertEquals(2, result.milestones.size)
        result.budgetSections.forEach { assertEquals(2, it.items.size) }
        // sections and milestones are in sortOrder order
        assertEquals(listOf(0, 1, 2), result.budgetSections.map { it.sortOrder })
        assertEquals(listOf(0, 1), result.milestones.map { it.sortOrder })
    }

    // ── updateCampaign ────────────────────────────────────────────────────────

    @Test
    fun `updateCampaign - updates name and status`() {
        linkMonerium(userId)
        val created = campaignService.createCampaign(
            userId,
            CreateCampaignRequest(name = "Old Name", goal = BigDecimal("10000"))
        )

        val result = campaignService.updateCampaign(
            userId,
            created.id,
            UpdateCampaignRequest(name = "New Name", status = CampaignStatus.LIVE)
        )

        assertEquals("New Name", result.name)
        assertEquals(CampaignStatus.LIVE, result.status)
    }

    @Test
    fun `updateCampaign - invalid status transition LIVE to DRAFT throws 422`() {
        // LIVE → DRAFT is invalid (only LIVE → ENDED is allowed)
        linkMonerium(userId)
        val created = campaignService.createCampaign(userId, CreateCampaignRequest(name = "Campaign", goal = BigDecimal("10000")))
        campaignService.updateCampaign(userId, created.id, UpdateCampaignRequest(status = CampaignStatus.LIVE))

        assertThrows<UnprocessableEntityException> {
            campaignService.updateCampaign(userId, created.id, UpdateCampaignRequest(status = CampaignStatus.DRAFT))
        }
    }

    // ── deleteCampaign ────────────────────────────────────────────────────────

    @Test
    fun `deleteCampaign - removes the campaign`() {
        val created = campaignService.createCampaign(userId, CreateCampaignRequest(name = "To Delete"))

        campaignService.deleteCampaign(userId, created.id)

        val remaining = campaignService.listCampaigns(userId)
        assertTrue(remaining.isEmpty())
    }

    // ── saveBudget ────────────────────────────────────────────────────────────

    @Test
    fun `saveBudget - replaces entire budget structure`() {
        val created = campaignService.createCampaign(userId, CreateCampaignRequest(name = "Budget Campaign"))
        val req = SaveBudgetRequest(
            sections = listOf(
                SaveBudgetSectionRequest(
                    side = BudgetSide.EXPENSE,
                    code = "60",
                    name = "Achats",
                    sortOrder = 0,
                    items = listOf(
                        SaveBudgetItemRequest(label = "Prestations", amount = BigDecimal("4200"), sortOrder = 0),
                        SaveBudgetItemRequest(label = "Fournitures", amount = BigDecimal("620"), sortOrder = 1)
                    )
                ),
                SaveBudgetSectionRequest(
                    side = BudgetSide.REVENUE,
                    code = "74",
                    name = "Subventions",
                    sortOrder = 0,
                    items = listOf(SaveBudgetItemRequest(label = "État", amount = BigDecimal("15000")))
                )
            )
        )

        val result = campaignService.saveBudget(userId, created.id, req)

        assertEquals(2, result.budgetSections.size)
        val charges = result.budgetSections.first { it.side == BudgetSide.EXPENSE }
        assertEquals(2, charges.items.size)
    }

    @Test
    fun `saveBudget - called twice replaces old budget with new one`() {
        val created = campaignService.createCampaign(userId, CreateCampaignRequest(name = "Budget Campaign"))
        val firstReq = SaveBudgetRequest(
            sections = listOf(
                SaveBudgetSectionRequest(
                    side = BudgetSide.EXPENSE, code = "60", name = "Achats", sortOrder = 0,
                    items = listOf(SaveBudgetItemRequest(label = "Item A", amount = BigDecimal("1000")))
                )
            )
        )
        campaignService.saveBudget(userId, created.id, firstReq)

        // Flush and clear session to avoid Hibernate 1st-level cache conflicts on the second save
        entityManager.flush()
        entityManager.clear()

        val secondReq = SaveBudgetRequest(
            sections = listOf(
                SaveBudgetSectionRequest(
                    side = BudgetSide.REVENUE, code = "74", name = "Subventions", sortOrder = 0,
                    items = listOf(SaveBudgetItemRequest(label = "Item B", amount = BigDecimal("5000")))
                )
            )
        )
        val result = campaignService.saveBudget(userId, created.id, secondReq)

        assertEquals(1, result.budgetSections.size)
        assertEquals(BudgetSide.REVENUE, result.budgetSections[0].side)
        assertEquals("Item B", result.budgetSections[0].items[0].label)
    }

    // ── addMilestone ──────────────────────────────────────────────────────────

    @Test
    fun `addMilestone - creates and returns milestone`() {
        val campaign = campaignService.createCampaign(userId, CreateCampaignRequest(name = "Campaign"))
        val req = CreateMilestoneRequest(
            title = "Urgence Chauffage",
            emoji = "❄️",
            description = "Matériel d'urgence",
            targetAmount = BigDecimal("5000"),
            sortOrder = 0
        )

        val result = campaignService.addMilestone(userId, campaign.id, req)

        assertNotNull(result.id)
        assertEquals("Urgence Chauffage", result.title)
        assertEquals("❄️", result.emoji)
        assertEquals(BigDecimal("5000"), result.targetAmount)
        assertEquals(MilestoneStatus.LOCKED, result.status)
    }

    // ── updateMilestone ───────────────────────────────────────────────────────

    @Test
    fun `updateMilestone - updates title and status`() {
        val campaign = campaignService.createCampaign(userId, CreateCampaignRequest(name = "Campaign"))
        val milestone = campaignService.addMilestone(
            userId, campaign.id, CreateMilestoneRequest(title = "Old Title")
        )

        val result = campaignService.updateMilestone(
            userId, campaign.id, milestone.id,
            UpdateMilestoneRequest(title = "New Title", status = MilestoneStatus.CURRENT)
        )

        assertEquals("New Title", result.title)
        assertEquals(MilestoneStatus.CURRENT, result.status)
    }

    // ── deleteMilestone ───────────────────────────────────────────────────────

    @Test
    fun `deleteMilestone - removes the milestone`() {
        val campaign = campaignService.createCampaign(userId, CreateCampaignRequest(name = "Campaign"))
        val milestone = campaignService.addMilestone(
            userId, campaign.id, CreateMilestoneRequest(title = "To Delete")
        )

        campaignService.deleteMilestone(userId, campaign.id, milestone.id)

        val updated = campaignService.getCampaign(userId, campaign.id)
        assertTrue(updated.milestones.isEmpty())
    }

    // ── reorderMilestones ─────────────────────────────────────────────────────

    @Test
    fun `reorderMilestones - updates sort order`() {
        val campaign = campaignService.createCampaign(userId, CreateCampaignRequest(name = "Campaign"))
        val ms1 = campaignService.addMilestone(
            userId, campaign.id, CreateMilestoneRequest(title = "First", sortOrder = 0)
        )
        val ms2 = campaignService.addMilestone(
            userId, campaign.id, CreateMilestoneRequest(title = "Second", sortOrder = 1)
        )

        val result = campaignService.reorderMilestones(
            userId, campaign.id, ReorderMilestonesRequest(milestoneIds = listOf(ms2.id, ms1.id))
        )

        assertEquals(ms2.id, result[0].id)
        assertEquals(0, result[0].sortOrder)
        assertEquals(ms1.id, result[1].id)
        assertEquals(1, result[1].sortOrder)
    }

    // ── 404 on non-existent campaign ──────────────────────────────────────────

    @Test
    fun `getCampaign - non-existent campaign throws 404`() {
        assertThrows<NotFoundException> {
            campaignService.getCampaign(userId, UUID.randomUUID())
        }
    }

    @Test
    fun `updateCampaign - non-existent campaign throws 404`() {
        assertThrows<NotFoundException> {
            campaignService.updateCampaign(userId, UUID.randomUUID(), UpdateCampaignRequest(name = "X"))
        }
    }

    @Test
    fun `deleteCampaign - non-existent campaign throws 404`() {
        assertThrows<NotFoundException> {
            campaignService.deleteCampaign(userId, UUID.randomUUID())
        }
    }

    @Test
    fun `saveBudget - non-existent campaign throws 404`() {
        assertThrows<NotFoundException> {
            campaignService.saveBudget(userId, UUID.randomUUID(), SaveBudgetRequest(sections = emptyList()))
        }
    }

    @Test
    fun `addMilestone - non-existent campaign throws 404`() {
        assertThrows<NotFoundException> {
            campaignService.addMilestone(userId, UUID.randomUUID(), CreateMilestoneRequest(title = "X"))
        }
    }

    // ── 404 on campaign belonging to another association ──────────────────────

    @Test
    fun `getCampaign - campaign of other association throws 404`() {
        val otherCampaign = campaignService.createCampaign(
            otherUserId, CreateCampaignRequest(name = "Other Campaign")
        )

        assertThrows<NotFoundException> {
            campaignService.getCampaign(userId, otherCampaign.id)
        }
    }

    @Test
    fun `deleteCampaign - campaign of other association throws 404`() {
        val otherCampaign = campaignService.createCampaign(
            otherUserId, CreateCampaignRequest(name = "Other Campaign")
        )

        assertThrows<NotFoundException> {
            campaignService.deleteCampaign(userId, otherCampaign.id)
        }
    }

    // ── on-chain publish ──────────────────────────────────────────────────────

    @Test
    fun `publish - no Monerium wallet returns 422 and enqueues no job`() {
        val campaign = campaignService.createCampaign(
            userId, CreateCampaignRequest(name = "Publish test", goal = BigDecimal("10000"))
        )

        assertThrows<UnprocessableEntityException> {
            campaignService.updateCampaign(userId, campaign.id, UpdateCampaignRequest(status = CampaignStatus.LIVE))
        }
        assertEquals(0, onchainJobRepository.findAll().size)
    }

    @Test
    fun `publish - valid campaign enqueues CREATE then PUBLISH, republish is no-op`() {
        linkMonerium(userId)
        val campaign = campaignService.createCampaign(
            userId, CreateCampaignRequest(name = "Publish test", goal = BigDecimal("5000"))
        )

        campaignService.updateCampaign(userId, campaign.id, UpdateCampaignRequest(status = CampaignStatus.LIVE))

        val jobs = onchainJobRepository.findAll()
        val actions = jobs.map { it.action }
        assertEquals(1, actions.count { it == OnchainJobAction.CREATE_CAMPAIGN })
        assertEquals(1, actions.count { it == OnchainJobAction.PUBLISH_CAMPAIGN })
        // CREATE must have been inserted before PUBLISH
        val createJob  = jobs.first { it.action == OnchainJobAction.CREATE_CAMPAIGN }
        val publishJob = jobs.first { it.action == OnchainJobAction.PUBLISH_CAMPAIGN }
        assertFalse(createJob.createdAt.isAfter(publishJob.createdAt))

        // Republish is a no-op — correlation key guard prevents a second CREATE/PUBLISH
        assertThrows<UnprocessableEntityException> {
            campaignService.updateCampaign(userId, campaign.id, UpdateCampaignRequest(status = CampaignStatus.LIVE))
        }
        assertEquals(2, onchainJobRepository.findAll().size)
    }

    @Test
    fun `saveBudget - LIVE campaign with changed budget enqueues UPDATE_CAMPAIGN_BUDGET, identical budget enqueues nothing`() {
        linkMonerium(userId)
        val campaign = campaignService.createCampaign(
            userId, CreateCampaignRequest(name = "Budget test", goal = BigDecimal("5000"))
        )
        val budgetReq = SaveBudgetRequest(listOf(
            SaveBudgetSectionRequest(BudgetSide.EXPENSE, "60", "Achats", 0,
                listOf(SaveBudgetItemRequest("Matériel", BigDecimal("500"), 0)))
        ))
        campaignService.saveBudget(userId, campaign.id, budgetReq)
        campaignService.updateCampaign(userId, campaign.id, UpdateCampaignRequest(status = CampaignStatus.LIVE))
        val jobsAfterPublish = onchainJobRepository.findAll().size

        // Edit with different amount — should enqueue UPDATE_CAMPAIGN_BUDGET
        val changedBudget = SaveBudgetRequest(listOf(
            SaveBudgetSectionRequest(BudgetSide.EXPENSE, "60", "Achats", 0,
                listOf(SaveBudgetItemRequest("Matériel", BigDecimal("999"), 0)))
        ))
        campaignService.saveBudget(userId, campaign.id, changedBudget)
        val jobsAfterChange = onchainJobRepository.findAll()
        assertEquals(1, jobsAfterChange.count { it.action == OnchainJobAction.UPDATE_CAMPAIGN_BUDGET })

        // Edit with same content — hash unchanged, no new job
        campaignService.saveBudget(userId, campaign.id, changedBudget)
        val jobsAfterSameEdit = onchainJobRepository.findAll()
        assertEquals(1, jobsAfterSameEdit.count { it.action == OnchainJobAction.UPDATE_CAMPAIGN_BUDGET })
    }

    @Test
    fun `saveBudget - DRAFT campaign updates budgetHash off-chain but enqueues no job`() {
        val campaign = campaignService.createCampaign(
            userId, CreateCampaignRequest(name = "Draft budget test", goal = BigDecimal("5000"))
        )
        val budgetReq = SaveBudgetRequest(listOf(
            SaveBudgetSectionRequest(BudgetSide.EXPENSE, "60", "Achats", 0,
                listOf(SaveBudgetItemRequest("Fournitures", BigDecimal("200"), 0)))
        ))

        campaignService.saveBudget(userId, campaign.id, budgetReq)

        // No on-chain job for DRAFT campaigns
        assertEquals(0, onchainJobRepository.findAll().size)
        // But budgetHash is set in DB
        entityManager.flush()
        entityManager.clear()
        val updated = campaignService.getCampaign(userId, campaign.id)
        assertNotNull(updated.budgetHash)
    }
}
