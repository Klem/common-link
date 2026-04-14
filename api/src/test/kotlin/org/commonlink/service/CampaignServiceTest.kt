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
import org.commonlink.exception.NotFoundException
import org.commonlink.exception.UnprocessableEntityException
import org.commonlink.repository.AssociationProfileRepository
import org.commonlink.repository.TestFixtures
import org.commonlink.repository.TestcontainersConfig
import org.commonlink.repository.UserRepository
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.testcontainers.context.ImportTestcontainers
import org.springframework.test.context.TestPropertySource
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.util.UUID
import jakarta.persistence.EntityManager

/**
 * Integration tests for [CampaignService] using a real PostgreSQL container via Testcontainers.
 *
 * Each test runs in a transaction that is rolled back after completion, so the database is
 * always in a clean state between tests.
 */
@SpringBootTest
@ImportTestcontainers(TestcontainersConfig::class)
@TestPropertySource(properties = [
    "spring.profiles.active=local",
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
    private lateinit var entityManager: EntityManager

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

    // ── listCampaigns ─────────────────────────────────────────────────────────

    @Test
    fun `listCampaigns - returns empty list for new association`() {
        val result = campaignService.listCampaigns(userId)

        assertTrue(result.isEmpty())
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

    // ── updateCampaign ────────────────────────────────────────────────────────

    @Test
    fun `updateCampaign - updates name and status`() {
        val created = campaignService.createCampaign(
            userId,
            CreateCampaignRequest(name = "Old Name")
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
        val created = campaignService.createCampaign(userId, CreateCampaignRequest(name = "Campaign"))
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
}
