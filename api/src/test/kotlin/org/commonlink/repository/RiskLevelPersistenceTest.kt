package org.commonlink.repository

import org.assertj.core.api.Assertions.assertThat
import org.commonlink.entity.RiskLevel
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager

/**
 * Vérifie le aller-retour de persistance des champs de niveau de risque LCB-FT
 * sur les entités AssociationProfile et Donation.
 *
 * Ces tests valident le mapping JPA (entité → colonne → entité) mais pas la migration
 * Flyway : le schéma de test est généré par Hibernate (create-drop), Flyway étant
 * désactivé en profil test.
 */
class RiskLevelPersistenceTest(
    @Autowired private val userRepository: UserRepository,
    @Autowired private val associationProfileRepository: AssociationProfileRepository,
    @Autowired private val donorProfileRepository: DonorProfileRepository,
    @Autowired private val campaignRepository: CampaignRepository,
    @Autowired private val donationRepository: DonationRepository,
    @Autowired private val em: TestEntityManager,
) : AbstractRepositoryTest() {

    // ── AssociationProfile ────────────────────────────────────────────────────

    @Test
    fun `association profile - riskLevel defaults to STANDARD with null assessment fields`() {
        val user = userRepository.save(TestFixtures.associationUser(email = "risk-default@example.com"))
        val profile = associationProfileRepository.save(TestFixtures.associationProfile(user = user))
        em.flush()
        em.clear()

        val found = associationProfileRepository.findById(profile.id!!).get()
        assertThat(found.riskLevel).isEqualTo(RiskLevel.STANDARD)
        assertThat(found.riskLevelAssessedAt).isNull()
        assertThat(found.riskClassificationVersion).isNull()
    }

    @Test
    fun `association profile - riskLevel HIGH persists and reloads correctly`() {
        val user = userRepository.save(TestFixtures.associationUser(email = "risk-high@example.com"))
        val profile = associationProfileRepository.save(
            TestFixtures.associationProfile(user = user, riskLevel = RiskLevel.HIGH)
        )
        em.flush()
        em.clear()

        val found = associationProfileRepository.findById(profile.id!!).get()
        assertThat(found.riskLevel).isEqualTo(RiskLevel.HIGH)
    }

    // ── Donation ──────────────────────────────────────────────────────────────

    @Test
    fun `donation - riskLevel defaults to STANDARD`() {
        val (donor, campaign) = buildDonorAndCampaign("risk-donor-std", "risk-asso-std")
        val donation = donationRepository.save(TestFixtures.donation(donor = donor, campaign = campaign))
        em.flush()
        em.clear()

        val found = donationRepository.findById(donation.id!!).get()
        assertThat(found.riskLevel).isEqualTo(RiskLevel.STANDARD)
    }

    @Test
    fun `donation - riskLevel HIGH persists as immutable snapshot`() {
        val (donor, campaign) = buildDonorAndCampaign("risk-donor-high", "risk-asso-high")
        val donation = donationRepository.save(
            TestFixtures.donation(donor = donor, campaign = campaign, riskLevel = RiskLevel.HIGH)
        )
        em.flush()
        em.clear()

        val found = donationRepository.findById(donation.id!!).get()
        assertThat(found.riskLevel).isEqualTo(RiskLevel.HIGH)
    }

    private fun buildDonorAndCampaign(donorSuffix: String, assoSuffix: String): Pair<org.commonlink.entity.DonorProfile, org.commonlink.entity.Campaign> {
        val assocUser = userRepository.save(TestFixtures.associationUser(email = "$assoSuffix@example.com"))
        val assoc = associationProfileRepository.save(TestFixtures.associationProfile(assocUser))
        val campaign = campaignRepository.save(TestFixtures.campaign(assoc))
        val donorUser = userRepository.save(TestFixtures.donorUser(email = "$donorSuffix@example.com"))
        val donor = donorProfileRepository.save(TestFixtures.donorProfile(donorUser))
        return Pair(donor, campaign)
    }
}
