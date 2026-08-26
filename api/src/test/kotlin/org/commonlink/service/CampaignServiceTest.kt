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
import org.commonlink.entity.MollieConnection
import org.commonlink.entity.MollieConnectionState
import org.commonlink.entity.MollieOnboardingStatus
import org.commonlink.entity.OnchainJobAction
import org.commonlink.entity.VerificationStatus
import org.commonlink.exception.NotFoundException
import org.commonlink.exception.UnprocessableEntityException
import org.commonlink.repository.AssociationProfileRepository
import org.commonlink.repository.MollieConnectionRepository
import org.commonlink.repository.OnchainJobRepository
import org.commonlink.repository.TestFiles
import org.commonlink.repository.TestFixtures
import org.commonlink.repository.TestcontainersConfig
import org.commonlink.repository.UserRepository
import org.hibernate.SessionFactory
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.testcontainers.context.ImportTestcontainers
import org.springframework.test.context.ActiveProfiles
import org.springframework.mock.web.MockMultipartFile
import org.springframework.test.context.TestPropertySource
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.multipart.MultipartFile
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
@Tag("testcontainers")
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
    private lateinit var mollieConnectionRepository: MollieConnectionRepository

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

    /**
     * Links a Mollie connection to the association. Defaults satisfy the publish-time bank gate
     * (ACTIVE + COMPLETED + canReceivePayments); each parameter can be relaxed to exercise one
     * failing condition at a time.
     *
     * Also marks the KYB dossier VERIFIED, because that is the production invariant: connecting
     * Mollie requires a signed mandate, which requires VERIFIED. Tests that need a bank-ready but
     * unverified association (revoked dossier) pass `verifyKyb = false`.
     */
    private fun linkMollie(
        ownerId: UUID,
        state: MollieConnectionState = MollieConnectionState.ACTIVE,
        onboardingStatus: MollieOnboardingStatus = MollieOnboardingStatus.COMPLETED,
        canReceivePayments: Boolean = true,
        verifyKyb: Boolean = true,
    ) {
        val assoc = associationProfileRepository.findByUserId(ownerId).get()
        if (verifyKyb) {
            assoc.verificationStatus = VerificationStatus.VERIFIED
            associationProfileRepository.save(assoc)
        }
        mollieConnectionRepository.save(MollieConnection(
            association        = assoc,
            accessToken        = "tok",
            refreshToken       = "ref",
            expiresAt          = java.time.Instant.now().plusSeconds(3600),
            state              = state,
            onboardingStatus   = onboardingStatus,
            canReceivePayments = canReceivePayments,
        ))
    }

    /**
     * Satisfies the three *content* gates of `preparePublish`: a balanced budget prévisionnel
     * (expenses = revenues, both non-zero), an expected outcome of at least 20 characters, and a
     * set calendrier (startDate/endDate).
     *
     * Tests that exercise an account gate call this first, so the campaign is publishable in every
     * respect but the one under test — otherwise they would assert on the content message instead.
     */
    private fun makePublishable(
        ownerId: UUID,
        campaignId: UUID,
        amount: BigDecimal = BigDecimal("1000"),
    ) {
        campaignService.saveBudget(ownerId, campaignId, SaveBudgetRequest(listOf(
            SaveBudgetSectionRequest(BudgetSide.EXPENSE, "60", "Achats", 0,
                listOf(SaveBudgetItemRequest("Matériel", amount, 0))),
            SaveBudgetSectionRequest(BudgetSide.REVENUE, "74", "Dons", 1,
                listOf(SaveBudgetItemRequest("Dons collectés", amount, 0))),
        )))
        campaignService.updateCampaign(
            ownerId, campaignId,
            UpdateCampaignRequest(
                impactGoals = "200 repas servis chaque semaine pendant six mois",
                startDate = java.time.LocalDate.of(2026, 1, 1),
                endDate = java.time.LocalDate.of(2026, 12, 31),
            ),
        )
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
        linkMollie(userId)
        val created = campaignService.createCampaign(
            userId,
            CreateCampaignRequest(name = "Old Name", goal = BigDecimal("10000"))
        )
        makePublishable(userId, created.id)

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
        linkMollie(userId)
        val created = campaignService.createCampaign(userId, CreateCampaignRequest(name = "Campaign", goal = BigDecimal("10000")))
        makePublishable(userId, created.id)
        campaignService.updateCampaign(userId, created.id, UpdateCampaignRequest(status = CampaignStatus.LIVE))

        assertThrows<UnprocessableEntityException> {
            campaignService.updateCampaign(userId, created.id, UpdateCampaignRequest(status = CampaignStatus.DRAFT))
        }
    }

    @Test
    fun `updateCampaign - endDate less than 7 days after startDate throws 422`() {
        val created = campaignService.createCampaign(userId, CreateCampaignRequest(name = "Date Test"))

        assertThrows<UnprocessableEntityException> {
            campaignService.updateCampaign(
                userId, created.id,
                UpdateCampaignRequest(
                    startDate = java.time.LocalDate.of(2025, 1, 1),
                    endDate = java.time.LocalDate.of(2025, 1, 6) // only 5 days later
                )
            )
        }
    }

    @Test
    fun `updateCampaign - endDate exactly 7 days after startDate succeeds`() {
        val created = campaignService.createCampaign(userId, CreateCampaignRequest(name = "Date Test OK"))

        val updated = campaignService.updateCampaign(
            userId, created.id,
            UpdateCampaignRequest(
                startDate = java.time.LocalDate.of(2025, 1, 1),
                endDate = java.time.LocalDate.of(2025, 1, 8) // exactly 7 days
            )
        )
        assertEquals(java.time.LocalDate.of(2025, 1, 8), updated.endDate)
    }

    @Test
    fun `updateCampaign - sets category, reason and impactGoals`() {
        val created = campaignService.createCampaign(userId, CreateCampaignRequest(name = "Info Test"))

        val updated = campaignService.updateCampaign(
            userId, created.id,
            UpdateCampaignRequest(
                category = "Education",
                reason = "Rénover les écoles",
                impactGoals = "450 élèves bénéficiaires"
            )
        )
        assertEquals("Education", updated.category)
        assertEquals("Rénover les écoles", updated.reason)
        assertEquals("450 élèves bénéficiaires", updated.impactGoals)
    }

    // ── cover image ───────────────────────────────────────────────────────────

    /**
     * Builds a multipart image part whose bytes really are of [contentType] — upload validation
     * checks the leading bytes against the declared type (audit 2026-08-20, M9).
     */
    private fun imagePart(
        contentType: String = "image/png",
        bytes: ByteArray = TestFiles.png(),
    ): MultipartFile = MockMultipartFile("file", "cover.png", contentType, bytes)

    @Test
    fun `uploadCoverImage - stores the bytes and sets the public serving path`() {
        val created = campaignService.createCampaign(userId, CreateCampaignRequest(name = "With Cover"))

        val updated = campaignService.uploadCoverImage(userId, created.id, imagePart())

        assertEquals("/api/public/campaigns/${created.id}/cover", updated.coverImage)
        val (contentType, data) = campaignService.getCoverImage(created.id)
        assertEquals("image/png", contentType)
        assertArrayEquals(TestFiles.png(), data)
    }

    @Test
    fun `uploadCoverImage - replaces a previously stored image`() {
        val created = campaignService.createCampaign(userId, CreateCampaignRequest(name = "With Cover"))
        campaignService.uploadCoverImage(userId, created.id, imagePart())

        campaignService.uploadCoverImage(
            userId, created.id,
            imagePart(contentType = "image/webp", bytes = TestFiles.webp()),
        )

        val (contentType, data) = campaignService.getCoverImage(created.id)
        assertEquals("image/webp", contentType)
        assertArrayEquals(TestFiles.webp(), data)
    }

    @Test
    fun `uploadCoverImage - rejects an unsupported MIME type`() {
        val created = campaignService.createCampaign(userId, CreateCampaignRequest(name = "With Cover"))

        assertThrows<UnprocessableEntityException> {
            campaignService.uploadCoverImage(userId, created.id, imagePart(contentType = "application/pdf"))
        }
    }

    @Test
    fun `uploadCoverImage - rejects a file above 5 MB`() {
        val created = campaignService.createCampaign(userId, CreateCampaignRequest(name = "With Cover"))

        assertThrows<UnprocessableEntityException> {
            campaignService.uploadCoverImage(
                userId, created.id,
                imagePart(bytes = ByteArray(5 * 1024 * 1024 + 1)),
            )
        }
    }

    @Test
    fun `uploadCoverImage - rejects an empty file`() {
        val created = campaignService.createCampaign(userId, CreateCampaignRequest(name = "With Cover"))

        assertThrows<UnprocessableEntityException> {
            campaignService.uploadCoverImage(userId, created.id, imagePart(bytes = ByteArray(0)))
        }
    }

    @Test
    fun `uploadCoverImage - rejects a campaign owned by another association`() {
        val created = campaignService.createCampaign(userId, CreateCampaignRequest(name = "With Cover"))

        assertThrows<NotFoundException> {
            campaignService.uploadCoverImage(otherUserId, created.id, imagePart())
        }
    }

    @Test
    fun `deleteCoverImage - clears the path and the stored bytes`() {
        val created = campaignService.createCampaign(userId, CreateCampaignRequest(name = "With Cover"))
        campaignService.uploadCoverImage(userId, created.id, imagePart())

        val updated = campaignService.deleteCoverImage(userId, created.id)

        assertNull(updated.coverImage)
        assertThrows<NotFoundException> { campaignService.getCoverImage(created.id) }
    }

    @Test
    fun `deleteCoverImage - is a no-op when no image was stored`() {
        val created = campaignService.createCampaign(userId, CreateCampaignRequest(name = "No Cover"))

        val updated = campaignService.deleteCoverImage(userId, created.id)

        assertNull(updated.coverImage)
    }

    @Test
    fun `getCoverImage - throws when the campaign has no cover image`() {
        val created = campaignService.createCampaign(userId, CreateCampaignRequest(name = "No Cover"))

        assertThrows<NotFoundException> { campaignService.getCoverImage(created.id) }
    }

    // ── deleteCampaign ────────────────────────────────────────────────────────

    @Test
    fun `deleteCampaign - removes the campaign`() {
        val created = campaignService.createCampaign(userId, CreateCampaignRequest(name = "To Delete"))

        campaignService.deleteCampaign(userId, created.id)

        val remaining = campaignService.listCampaigns(userId)
        assertTrue(remaining.isEmpty())
    }

    @Test
    fun `deleteCampaign - throws when campaign is not DRAFT`() {
        linkMollie(userId)
        val created = campaignService.createCampaign(userId, CreateCampaignRequest(name = "Live Campaign", goal = BigDecimal("10000")))
        makePublishable(userId, created.id)
        campaignService.updateCampaign(userId, created.id, UpdateCampaignRequest(status = CampaignStatus.LIVE))

        assertThrows<UnprocessableEntityException> {
            campaignService.deleteCampaign(userId, created.id)
        }

        val remaining = campaignService.listCampaigns(userId)
        assertEquals(1, remaining.size)
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

    @Test
    fun `updateMilestone - throws when milestone is CURRENT`() {
        val campaign = campaignService.createCampaign(userId, CreateCampaignRequest(name = "Campaign"))
        val milestone = campaignService.addMilestone(
            userId, campaign.id, CreateMilestoneRequest(title = "Old Title")
        )
        campaignService.updateMilestone(
            userId, campaign.id, milestone.id, UpdateMilestoneRequest(status = MilestoneStatus.CURRENT)
        )

        assertThrows<UnprocessableEntityException> {
            campaignService.updateMilestone(
                userId, campaign.id, milestone.id, UpdateMilestoneRequest(title = "Blocked")
            )
        }
    }

    @Test
    fun `updateMilestone - throws when milestone is REACHED`() {
        val campaign = campaignService.createCampaign(userId, CreateCampaignRequest(name = "Campaign"))
        val milestone = campaignService.addMilestone(
            userId, campaign.id, CreateMilestoneRequest(title = "Old Title")
        )
        campaignService.updateMilestone(
            userId, campaign.id, milestone.id, UpdateMilestoneRequest(status = MilestoneStatus.REACHED)
        )

        assertThrows<UnprocessableEntityException> {
            campaignService.updateMilestone(
                userId, campaign.id, milestone.id, UpdateMilestoneRequest(title = "Blocked")
            )
        }
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

    @Test
    fun `deleteMilestone - throws when milestone is CURRENT`() {
        val campaign = campaignService.createCampaign(userId, CreateCampaignRequest(name = "Campaign"))
        val milestone = campaignService.addMilestone(
            userId, campaign.id, CreateMilestoneRequest(title = "Current One")
        )
        campaignService.updateMilestone(
            userId, campaign.id, milestone.id, UpdateMilestoneRequest(status = MilestoneStatus.CURRENT)
        )

        assertThrows<UnprocessableEntityException> {
            campaignService.deleteMilestone(userId, campaign.id, milestone.id)
        }
    }

    @Test
    fun `deleteMilestone - throws when milestone is REACHED`() {
        val campaign = campaignService.createCampaign(userId, CreateCampaignRequest(name = "Campaign"))
        val milestone = campaignService.addMilestone(
            userId, campaign.id, CreateMilestoneRequest(title = "Reached One")
        )
        campaignService.updateMilestone(
            userId, campaign.id, milestone.id, UpdateMilestoneRequest(status = MilestoneStatus.REACHED)
        )

        assertThrows<UnprocessableEntityException> {
            campaignService.deleteMilestone(userId, campaign.id, milestone.id)
        }
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

    /**
     * Publishing a campaign never enqueues an on-chain job — there is no wallet-provisioning
     * mechanism for associations. The transition itself still succeeds off-chain.
     */
    @Test
    fun `publish - happy path publishes off-chain and enqueues no job`() {
        linkMollie(userId)
        val campaign = campaignService.createCampaign(
            userId, CreateCampaignRequest(name = "Publish test", goal = BigDecimal("10000"))
        )
        makePublishable(userId, campaign.id)

        val result = campaignService.updateCampaign(
            userId, campaign.id, UpdateCampaignRequest(status = CampaignStatus.LIVE)
        )

        assertEquals(CampaignStatus.LIVE, result.status)
        assertEquals(0, onchainJobRepository.findAll().size)
    }

    // ── publish-time KYB gate (LCB-FT — mirrors PrePublishModal's kybReady) ────────────────────

    /**
     * A bank-ready association whose KYB dossier is not VERIFIED must not publish. The onboarding
     * chain implies VERIFIED transitively, but only at the time each step was taken — a dossier
     * revoked afterwards left the campaign publishable, which LCB-FT forbids.
     */
    @Test
    fun `publish - unverified KYB returns 422 even when the bank is ready`() {
        linkMollie(userId, verifyKyb = false)
        val campaign = campaignService.createCampaign(
            userId, CreateCampaignRequest(name = "Unverified KYB", goal = BigDecimal("10000"))
        )
        makePublishable(userId, campaign.id)

        val ex = assertThrows<UnprocessableEntityException> {
            campaignService.updateCampaign(userId, campaign.id, UpdateCampaignRequest(status = CampaignStatus.LIVE))
        }
        assertEquals("Association KYB must be verified before going live", ex.message)
        assertEquals(0, onchainJobRepository.findAll().size)
    }

    @Test
    fun `publish - REJECTED KYB returns 422 even when the bank is ready`() {
        assertPublishRefusedForKyb(VerificationStatus.REJECTED, "Rejected KYB")
    }

    @Test
    fun `publish - PENDING KYB returns 422 even when the bank is ready`() {
        assertPublishRefusedForKyb(VerificationStatus.PENDING, "Pending KYB")
    }

    /**
     * Publishes a bank-ready campaign whose association sits in [status] and asserts the KYB gate
     * refuses it. Keeps the three non-VERIFIED states covered symmetrically with the frontend,
     * so the proof matrix in `docs/legal/E3-verrou-verification-avant-collecte.md` holds at both layers.
     */
    private fun assertPublishRefusedForKyb(status: VerificationStatus, campaignName: String) {
        linkMollie(userId, verifyKyb = false)
        val assoc = associationProfileRepository.findByUserId(userId).get()
        assoc.verificationStatus = status
        associationProfileRepository.save(assoc)
        val campaign = campaignService.createCampaign(
            userId, CreateCampaignRequest(name = campaignName, goal = BigDecimal("10000"))
        )
        makePublishable(userId, campaign.id)

        val ex = assertThrows<UnprocessableEntityException> {
            campaignService.updateCampaign(userId, campaign.id, UpdateCampaignRequest(status = CampaignStatus.LIVE))
        }
        assertEquals("Association KYB must be verified before going live", ex.message)
        assertEquals(0, onchainJobRepository.findAll().size)
    }

    // ── publish-time bank gate (mirrors BankSetupStatus.COMPLETED in the frontend) ─────────────

    @Test
    fun `publish - no Mollie connection returns 422 and enqueues no job`() {
        val assoc = associationProfileRepository.findByUserId(userId).get()
        assoc.verificationStatus = VerificationStatus.VERIFIED
        associationProfileRepository.save(assoc)
        val campaign = campaignService.createCampaign(
            userId, CreateCampaignRequest(name = "No Mollie", goal = BigDecimal("10000"))
        )
        makePublishable(userId, campaign.id)

        val ex = assertThrows<UnprocessableEntityException> {
            campaignService.updateCampaign(userId, campaign.id, UpdateCampaignRequest(status = CampaignStatus.LIVE))
        }
        assertEquals("Association must connect a Mollie account before going live", ex.message)
        assertEquals(0, onchainJobRepository.findAll().size)
    }

    @Test
    fun `publish - BROKEN Mollie connection returns 422 even when KYC was completed`() {
        linkMollie(userId, state = MollieConnectionState.BROKEN)
        val campaign = campaignService.createCampaign(
            userId, CreateCampaignRequest(name = "Broken Mollie", goal = BigDecimal("10000"))
        )
        makePublishable(userId, campaign.id)

        val ex = assertThrows<UnprocessableEntityException> {
            campaignService.updateCampaign(userId, campaign.id, UpdateCampaignRequest(status = CampaignStatus.LIVE))
        }
        assertEquals("Mollie connection is broken — re-authorization required before going live", ex.message)
        assertEquals(0, onchainJobRepository.findAll().size)
    }

    /**
     * Discriminating case: the publish button in `PrePublishModal` only unlocks on
     * `BankSetupStatus.COMPLETED`, i.e. on `onboardingStatus`. An association still under Mollie
     * review must therefore be refused here too, whatever `canReceivePayments` says.
     */
    @Test
    fun `publish - Mollie onboarding still IN_REVIEW returns 422`() {
        linkMollie(userId, onboardingStatus = MollieOnboardingStatus.IN_REVIEW)
        val campaign = campaignService.createCampaign(
            userId, CreateCampaignRequest(name = "In review", goal = BigDecimal("10000"))
        )
        makePublishable(userId, campaign.id)

        val ex = assertThrows<UnprocessableEntityException> {
            campaignService.updateCampaign(userId, campaign.id, UpdateCampaignRequest(status = CampaignStatus.LIVE))
        }
        assertEquals("Association must complete Mollie KYC before going live", ex.message)
        assertEquals(0, onchainJobRepository.findAll().size)
    }

    @Test
    fun `publish - Mollie completed but not authorized to receive payments returns 422`() {
        linkMollie(userId, canReceivePayments = false)
        val campaign = campaignService.createCampaign(
            userId, CreateCampaignRequest(name = "No payments", goal = BigDecimal("10000"))
        )
        makePublishable(userId, campaign.id)

        val ex = assertThrows<UnprocessableEntityException> {
            campaignService.updateCampaign(userId, campaign.id, UpdateCampaignRequest(status = CampaignStatus.LIVE))
        }
        assertEquals("Association must complete Mollie KYC before going live", ex.message)
        assertEquals(0, onchainJobRepository.findAll().size)
    }

    /**
     * Publishing never enqueues an on-chain job (Monerium removed, V67 — see the "happy path" test
     * above), so this only exercises the plain LIVE→LIVE transition guard.
     */
    @Test
    fun `publish - republishing an already-LIVE campaign is rejected`() {
        linkMollie(userId)
        val campaign = campaignService.createCampaign(
            userId, CreateCampaignRequest(name = "Publish test", goal = BigDecimal("5000"))
        )
        makePublishable(userId, campaign.id)

        campaignService.updateCampaign(userId, campaign.id, UpdateCampaignRequest(status = CampaignStatus.LIVE))

        assertThrows<UnprocessableEntityException> {
            campaignService.updateCampaign(userId, campaign.id, UpdateCampaignRequest(status = CampaignStatus.LIVE))
        }
        assertEquals(0, onchainJobRepository.findAll().size)
    }

    @Test
    fun `saveBudget - LIVE campaign with changed budget enqueues UPDATE_CAMPAIGN_BUDGET, identical budget enqueues nothing`() {
        linkMollie(userId)
        val campaign = campaignService.createCampaign(
            userId, CreateCampaignRequest(name = "Budget test", goal = BigDecimal("5000"))
        )
        // Balanced: publishing now requires expenses = revenues (CampaignService.requireBalancedBudget).
        val budgetReq = SaveBudgetRequest(listOf(
            SaveBudgetSectionRequest(BudgetSide.EXPENSE, "60", "Achats", 0,
                listOf(SaveBudgetItemRequest("Matériel", BigDecimal("500"), 0))),
            SaveBudgetSectionRequest(BudgetSide.REVENUE, "74", "Dons", 1,
                listOf(SaveBudgetItemRequest("Dons collectés", BigDecimal("500"), 0))),
        ))
        campaignService.saveBudget(userId, campaign.id, budgetReq)
        campaignService.updateCampaign(
            userId, campaign.id,
            UpdateCampaignRequest(
                impactGoals = "200 repas servis chaque semaine pendant six mois",
                startDate = java.time.LocalDate.of(2026, 1, 1),
                endDate = java.time.LocalDate.of(2026, 12, 31),
            ),
        )
        campaignService.updateCampaign(userId, campaign.id, UpdateCampaignRequest(status = CampaignStatus.LIVE))
        val jobsAfterPublish = onchainJobRepository.findAll().size

        // Edit with different amount — should enqueue UPDATE_CAMPAIGN_BUDGET
        val changedBudget = SaveBudgetRequest(listOf(
            SaveBudgetSectionRequest(BudgetSide.EXPENSE, "60", "Achats", 0,
                listOf(SaveBudgetItemRequest("Matériel", BigDecimal("999"), 0))),
            SaveBudgetSectionRequest(BudgetSide.REVENUE, "74", "Dons", 1,
                listOf(SaveBudgetItemRequest("Dons collectés", BigDecimal("999"), 0))),
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

    // ── publish-time content gates (mirror PrePublishModal's blockers — rule 8) ────────────────

    /**
     * A donor is asked for money against a costed plan, so an empty or lopsided budget prévisionnel
     * blocks publication. Both were merely "recommended" before, which let a campaign go live with
     * no budget at all.
     */
    @Test
    fun `publish - empty budget returns 422`() {
        linkMollie(userId)
        val campaign = campaignService.createCampaign(
            userId, CreateCampaignRequest(name = "No budget", goal = BigDecimal("10000"))
        )
        campaignService.updateCampaign(
            userId, campaign.id,
            UpdateCampaignRequest(
                impactGoals = "200 repas servis chaque semaine pendant six mois",
                startDate = java.time.LocalDate.of(2026, 1, 1),
                endDate = java.time.LocalDate.of(2026, 12, 31),
            ),
        )

        val ex = assertThrows<UnprocessableEntityException> {
            campaignService.updateCampaign(userId, campaign.id, UpdateCampaignRequest(status = CampaignStatus.LIVE))
        }
        assertTrue(ex.message!!.contains("Budget prévisionnel must be balanced"))
        assertEquals(0, onchainJobRepository.findAll().size)
    }

    @Test
    fun `publish - unbalanced budget returns 422`() {
        linkMollie(userId)
        val campaign = campaignService.createCampaign(
            userId, CreateCampaignRequest(name = "Unbalanced budget", goal = BigDecimal("10000"))
        )
        campaignService.saveBudget(userId, campaign.id, SaveBudgetRequest(listOf(
            SaveBudgetSectionRequest(BudgetSide.EXPENSE, "60", "Achats", 0,
                listOf(SaveBudgetItemRequest("Matériel", BigDecimal("1000"), 0))),
            SaveBudgetSectionRequest(BudgetSide.REVENUE, "74", "Dons", 1,
                listOf(SaveBudgetItemRequest("Dons collectés", BigDecimal("800"), 0))),
        )))
        campaignService.updateCampaign(
            userId, campaign.id,
            UpdateCampaignRequest(
                impactGoals = "200 repas servis chaque semaine pendant six mois",
                startDate = java.time.LocalDate.of(2026, 1, 1),
                endDate = java.time.LocalDate.of(2026, 12, 31),
            ),
        )

        val ex = assertThrows<UnprocessableEntityException> {
            campaignService.updateCampaign(userId, campaign.id, UpdateCampaignRequest(status = CampaignStatus.LIVE))
        }
        assertTrue(ex.message!!.contains("Budget prévisionnel must be balanced"))
    }

    /**
     * Tolerance is one euro, exclusive — the same `Math.abs(revenues - expenses) < 1` the frontend
     * uses. A stricter backend would refuse a campaign the publish button declared ready.
     */
    @Test
    fun `publish - budget off by less than one euro is accepted`() {
        linkMollie(userId)
        val campaign = campaignService.createCampaign(
            userId, CreateCampaignRequest(name = "Rounding budget", goal = BigDecimal("10000"))
        )
        campaignService.saveBudget(userId, campaign.id, SaveBudgetRequest(listOf(
            SaveBudgetSectionRequest(BudgetSide.EXPENSE, "60", "Achats", 0,
                listOf(SaveBudgetItemRequest("Matériel", BigDecimal("1000.00"), 0))),
            SaveBudgetSectionRequest(BudgetSide.REVENUE, "74", "Dons", 1,
                listOf(SaveBudgetItemRequest("Dons collectés", BigDecimal("1000.40"), 0))),
        )))
        campaignService.updateCampaign(
            userId, campaign.id,
            UpdateCampaignRequest(
                impactGoals = "200 repas servis chaque semaine pendant six mois",
                startDate = java.time.LocalDate.of(2026, 1, 1),
                endDate = java.time.LocalDate.of(2026, 12, 31),
            ),
        )

        val result = campaignService.updateCampaign(
            userId, campaign.id, UpdateCampaignRequest(status = CampaignStatus.LIVE)
        )

        assertEquals(CampaignStatus.LIVE, result.status)
    }

    @Test
    fun `publish - missing or too short expected outcome returns 422`() {
        linkMollie(userId)
        val campaign = campaignService.createCampaign(
            userId, CreateCampaignRequest(name = "No outcome", goal = BigDecimal("10000"))
        )
        campaignService.saveBudget(userId, campaign.id, SaveBudgetRequest(listOf(
            SaveBudgetSectionRequest(BudgetSide.EXPENSE, "60", "Achats", 0,
                listOf(SaveBudgetItemRequest("Matériel", BigDecimal("1000"), 0))),
            SaveBudgetSectionRequest(BudgetSide.REVENUE, "74", "Dons", 1,
                listOf(SaveBudgetItemRequest("Dons collectés", BigDecimal("1000"), 0))),
        )))
        campaignService.updateCampaign(
            userId, campaign.id,
            UpdateCampaignRequest(
                startDate = java.time.LocalDate.of(2026, 1, 1),
                endDate = java.time.LocalDate.of(2026, 12, 31),
            ),
        )

        val whenNull = assertThrows<UnprocessableEntityException> {
            campaignService.updateCampaign(userId, campaign.id, UpdateCampaignRequest(status = CampaignStatus.LIVE))
        }
        assertTrue(whenNull.message!!.contains("impactGoals"))

        campaignService.updateCampaign(userId, campaign.id, UpdateCampaignRequest(impactGoals = "Trop court"))
        val whenShort = assertThrows<UnprocessableEntityException> {
            campaignService.updateCampaign(userId, campaign.id, UpdateCampaignRequest(status = CampaignStatus.LIVE))
        }
        assertTrue(whenShort.message!!.contains("impactGoals"))
    }

    @Test
    fun `publish - missing calendrier returns 422`() {
        linkMollie(userId)
        val campaign = campaignService.createCampaign(
            userId, CreateCampaignRequest(name = "No dates", goal = BigDecimal("10000"))
        )
        campaignService.saveBudget(userId, campaign.id, SaveBudgetRequest(listOf(
            SaveBudgetSectionRequest(BudgetSide.EXPENSE, "60", "Achats", 0,
                listOf(SaveBudgetItemRequest("Matériel", BigDecimal("1000"), 0))),
            SaveBudgetSectionRequest(BudgetSide.REVENUE, "74", "Dons", 1,
                listOf(SaveBudgetItemRequest("Dons collectés", BigDecimal("1000"), 0))),
        )))
        campaignService.updateCampaign(
            userId, campaign.id,
            UpdateCampaignRequest(impactGoals = "200 repas servis chaque semaine pendant six mois"),
        )

        val error = assertThrows<UnprocessableEntityException> {
            campaignService.updateCampaign(userId, campaign.id, UpdateCampaignRequest(status = CampaignStatus.LIVE))
        }
        assertTrue(error.message!!.contains("dates"))
    }
}
