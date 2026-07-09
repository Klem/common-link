package org.commonlink.repository

import org.assertj.core.api.Assertions.assertThat
import org.commonlink.entity.VerificationStatus
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager
import java.util.UUID

class AssociationProfileRepositoryTest(
    @Autowired private val userRepository: UserRepository,
    @Autowired private val associationProfileRepository: AssociationProfileRepository,
    @Autowired private val em: TestEntityManager,
) : AbstractRepositoryTest() {

    private lateinit var restosUserId: UUID
    private lateinit var secoursCathoUserId: UUID

    @BeforeEach
    fun seed() {
        val restosUser = userRepository.save(TestFixtures.associationUser(email = "restos@example.com"))
        val secoursCathoUser = userRepository.save(TestFixtures.associationUser(email = "secours-catho@example.com"))

        associationProfileRepository.save(
            TestFixtures.associationProfile(
                user = restosUser,
                name = "Les Restos du Coeur",
                identifier = "775671356",
                city = "Paris",
                verificationStatus = VerificationStatus.VERIFIED,
            )
        )
        associationProfileRepository.save(
            TestFixtures.associationProfile(
                user = secoursCathoUser,
                name = "Secours Catholique",
                identifier = "775739054",
                city = "Lyon",
            )
        )

        restosUserId = restosUser.id!!
        secoursCathoUserId = secoursCathoUser.id!!

        em.flush()
        em.clear()
    }

    @Test
    fun `findByUserId retourne le profil quand il existe`() {
        val result = associationProfileRepository.findByUserId(restosUserId)

        assertThat(result).isPresent
        assertThat(result.get().name).isEqualTo("Les Restos du Coeur")
        assertThat(result.get().city).isEqualTo("Paris")
        assertThat(result.get().verificationStatus).isEqualTo(VerificationStatus.VERIFIED)
    }

    @Test
    fun `findByUserId retourne empty pour un userId inconnu`() {
        val result = associationProfileRepository.findByUserId(UUID.randomUUID())

        assertThat(result).isEmpty
    }

    @Test
    fun `findByIdentifier retourne le profil quand l'identifiant existe`() {
        val result = associationProfileRepository.findByIdentifier("775739054")

        assertThat(result).isPresent
        assertThat(result.get().name).isEqualTo("Secours Catholique")
        assertThat(result.get().city).isEqualTo("Lyon")
    }

    @Test
    fun `findByIdentifier retourne empty pour un identifiant inconnu`() {
        val result = associationProfileRepository.findByIdentifier("000000000")

        assertThat(result).isEmpty
    }

    @Test
    fun `save persiste un profil association avec tous ses champs`() {
        val user = userRepository.save(TestFixtures.associationUser(email = "new-asso@example.com"))
        val profile = TestFixtures.associationProfile(
            user = user,
            name = "Médecins Sans Frontières",
            identifier = "312822382",
            city = "Marseille",
            postalCode = "13001",
            contactName = "Sophie Leblanc",
            description = "Aide médicale d'urgence internationale.",
        )

        val saved = associationProfileRepository.save(profile)

        assertThat(saved.id).isNotNull
        val found = associationProfileRepository.findByIdentifier("312822382")
        assertThat(found).isPresent
        assertThat(found.get().contactName).isEqualTo("Sophie Leblanc")
        assertThat(found.get().postalCode).isEqualTo("13001")
    }

    @Test
    fun `un profil non vérifié a verificationStatus UNVERIFIED par défaut`() {
        val result = associationProfileRepository.findByUserId(secoursCathoUserId)

        assertThat(result).isPresent
        assertThat(result.get().verificationStatus).isEqualTo(VerificationStatus.UNVERIFIED)
    }

    @Test
    fun `mise à jour de verificationStatus persiste correctement`() {
        val profile = associationProfileRepository.findByUserId(secoursCathoUserId).get()
        profile.verificationStatus = VerificationStatus.VERIFIED
        associationProfileRepository.save(profile)
        em.flush()
        em.clear()

        val updated = associationProfileRepository.findByUserId(secoursCathoUserId)
        assertThat(updated.get().verificationStatus).isEqualTo(VerificationStatus.VERIFIED)
    }
}
