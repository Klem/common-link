package org.commonlink.repository

import jakarta.persistence.EntityManagerFactory
import org.assertj.core.api.Assertions.assertThat
import org.commonlink.entity.BudgetSide
import org.commonlink.entity.CampaignBudgetItem
import org.commonlink.entity.CampaignBudgetSection
import org.hibernate.SessionFactory
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager
import org.springframework.boot.testcontainers.context.ImportTestcontainers
import java.math.BigDecimal

@ImportTestcontainers(TestcontainersConfig::class)
class CampaignBudgetSectionRepositoryTest(
    @Autowired private val userRepository: UserRepository,
    @Autowired private val associationProfileRepository: AssociationProfileRepository,
    @Autowired private val campaignRepository: CampaignRepository,
    @Autowired private val sectionRepository: CampaignBudgetSectionRepository,
    @Autowired private val itemRepository: CampaignBudgetItemRepository,
    @Autowired private val em: TestEntityManager,
    @Autowired private val emf: EntityManagerFactory,
) : AbstractRepositoryTest() {

    @Test
    fun `deleteAllByCampaignId issues one bulk DELETE and cascades to items`() {
        // ── setup ─────────────────────────────────────────────────────────
        val user = userRepository.save(TestFixtures.associationUser())
        val assoc = associationProfileRepository.save(TestFixtures.associationProfile(user))
        val campaign = campaignRepository.save(TestFixtures.campaign(assoc))

        val s1 = sectionRepository.save(
            CampaignBudgetSection(campaign = campaign, side = BudgetSide.EXPENSE, code = "CHARGES", name = "Charges")
        )
        val s2 = sectionRepository.save(
            CampaignBudgetSection(campaign = campaign, side = BudgetSide.REVENUE, code = "PRODUITS", name = "Produits")
        )
        itemRepository.saveAll(listOf(
            CampaignBudgetItem(section = s1, label = "Flyers",     amount = BigDecimal("100")),
            CampaignBudgetItem(section = s1, label = "Local",      amount = BigDecimal("500")),
            CampaignBudgetItem(section = s2, label = "Subvention", amount = BigDecimal("300")),
            CampaignBudgetItem(section = s2, label = "Dons",       amount = BigDecimal("200")),
        ))

        em.flush()
        em.clear()

        val sf = emf.unwrap(SessionFactory::class.java)
        sf.statistics.isStatisticsEnabled = true
        sf.statistics.clear()

        // ── act ────────────────────────────────────────────────────────────
        sectionRepository.deleteAllByCampaignId(campaign.id!!)

        // assert statement count before issuing any further queries
        assertThat(sf.statistics.prepareStatementCount)
            .describedAs("bulk DELETE must issue exactly 1 statement (no N×DELETE)")
            .isEqualTo(1)

        // ── assert: sections gone ──────────────────────────────────────────
        assertThat(sectionRepository.count()).isZero()

        // ── assert: items gone by DB CASCADE (not JPA cascade) ────────────
        assertThat(itemRepository.count()).isZero()
    }
}
