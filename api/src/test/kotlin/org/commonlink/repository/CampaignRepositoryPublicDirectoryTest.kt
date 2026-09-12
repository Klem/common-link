package org.commonlink.repository

import jakarta.persistence.EntityManagerFactory
import org.assertj.core.api.Assertions.assertThat
import org.commonlink.entity.AssociationProfile
import org.commonlink.entity.AssociationStatus
import org.commonlink.entity.Campaign
import org.commonlink.entity.CampaignStatus
import org.hibernate.SessionFactory
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager
import org.springframework.boot.testcontainers.context.ImportTestcontainers
import org.springframework.data.domain.PageRequest

/**
 * Couvre le prédicat d'éligibilité publique de [CampaignRepository.findPublicLive].
 *
 * L'enjeu n'est pas « la requête renvoie des lignes » mais « elle ne renvoie *que* ce que
 * `PublicWidgetService.resolveLanding` accepterait de servir » : une campagne listée dont la page
 * de don répond 409 est une promesse cassée pour le donateur.
 */
@ImportTestcontainers(TestcontainersConfig::class)
class CampaignRepositoryPublicDirectoryTest(
    @Autowired private val userRepository: UserRepository,
    @Autowired private val associationProfileRepository: AssociationProfileRepository,
    @Autowired private val campaignRepository: CampaignRepository,
    @Autowired private val milestoneRepository: CampaignMilestoneRepository,
    @Autowired private val em: TestEntityManager,
    @Autowired private val emf: EntityManagerFactory,
) : AbstractRepositoryTest() {

    private var emailSeq = 0

    /**
     * Crée une association éligible par défaut : ACTIVE, jeton widget émis, campagne LIVE
     * désignée comme destination du widget.
     */
    private fun eligible(
        name: String,
        campaignStatus: CampaignStatus = CampaignStatus.LIVE,
        associationStatus: AssociationStatus = AssociationStatus.ACTIVE,
        widgetToken: String? = "clk_${name.lowercase()}",
        wireDestination: Boolean = true,
    ): Pair<AssociationProfile, Campaign> {
        val user = userRepository.save(TestFixtures.associationUser(email = "asso${emailSeq++}@example.com"))
        val assoc = associationProfileRepository.save(
            TestFixtures.associationProfile(user, name = name, identifier = "7756713${emailSeq.toString().padStart(2, '0')}")
        )
        val campaign = campaignRepository.save(TestFixtures.campaign(assoc, name = "Campagne $name", status = campaignStatus))
        assoc.status = associationStatus
        assoc.widgetToken = widgetToken
        if (wireDestination) assoc.widgetDestinationCampaign = campaign
        associationProfileRepository.save(assoc)
        return assoc to campaign
    }

    private fun findAll() = campaignRepository.findPublicLive(PageRequest.of(0, 50))

    @Test
    fun `findPublicLive renvoie une campagne LIVE d'association active`() {
        val (assoc, campaign) = eligible("Restos")
        milestoneRepository.save(TestFixtures.milestone(campaign, title = "Jalon 1", sortOrder = 0))
        milestoneRepository.save(TestFixtures.milestone(campaign, title = "Jalon 2", sortOrder = 1))
        em.flush()
        em.clear()

        val rows = findAll()

        assertThat(rows).hasSize(1)
        with(rows.single()) {
            assertThat(campaignId).isEqualTo(campaign.id)
            assertThat(campaignName).isEqualTo("Campagne Restos")
            assertThat(associationName).isEqualTo(assoc.name)
            assertThat(widgetToken).isEqualTo("clk_restos")
            assertThat(milestoneCount).isEqualTo(2)
        }
    }

    @Test
    fun `findPublicLive exclut les campagnes non LIVE`() {
        eligible("Brouillon", campaignStatus = CampaignStatus.DRAFT)
        eligible("Pause", campaignStatus = CampaignStatus.PAUSED)
        eligible("Terminee", campaignStatus = CampaignStatus.ENDED)
        em.flush()
        em.clear()

        assertThat(findAll()).isEmpty()
    }

    @Test
    fun `findPublicLive exclut les campagnes LIVE d'une association SUSPENDED`() {
        eligible("Suspendue", associationStatus = AssociationStatus.SUSPENDED)
        em.flush()
        em.clear()

        assertThat(findAll())
            .describedAs("une association suspendue conserve ses campagnes LIVE mais sa page de don répond 409")
            .isEmpty()
    }

    @Test
    fun `findPublicLive exclut une association sans jeton widget ou sans campagne de destination`() {
        eligible("SansJeton", widgetToken = null)
        eligible("SansDestination", wireDestination = false)
        em.flush()
        em.clear()

        assertThat(findAll()).isEmpty()
    }

    @Test
    fun `findPublicLive exclut une campagne LIVE qui n'est pas la destination du widget`() {
        val (assoc, _) = eligible("DeuxCampagnes")
        val autre = campaignRepository.save(
            TestFixtures.campaign(assoc, name = "Campagne secondaire", status = CampaignStatus.LIVE)
        )
        em.flush()
        em.clear()

        val rows = findAll()

        assertThat(rows).hasSize(1)
        assertThat(rows.single().campaignId).isNotEqualTo(autre.id)
    }

    @Test
    fun `findPublicLive borne le nombre de lignes et tient en une seule requete`() {
        repeat(3) { eligible("Asso$it") }
        em.flush()
        em.clear()

        assertThat(campaignRepository.findPublicLive(PageRequest.of(0, 2)))
            .describedAs("la pagination doit être poussée en base, pas appliquée en mémoire")
            .hasSize(2)

        val sf = emf.unwrap(SessionFactory::class.java)
        sf.statistics.isStatisticsEnabled = true
        sf.statistics.clear()

        val rows = findAll()

        assertThat(rows).hasSize(3)
        assertThat(sf.statistics.prepareStatementCount)
            .describedAs("projection scalaire : ni association ni milestones ne doivent déclencher de requête supplémentaire")
            .isEqualTo(1)
    }
}
