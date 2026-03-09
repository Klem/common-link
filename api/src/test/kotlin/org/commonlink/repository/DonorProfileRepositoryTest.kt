package org.commonlink.repository

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager

import java.util.UUID

class DonorProfileRepositoryTest(
    @Autowired private val userRepository: UserRepository,
    @Autowired private val donorProfileRepository: DonorProfileRepository,
    @Autowired private val em: TestEntityManager,
) : AbstractRepositoryTest() {

    // IDs récupérés après save pour être utilisés dans les tests
    private lateinit var aliceId: UUID
    private lateinit var bobId: UUID

    @BeforeEach
    fun seed() {
        val alice = userRepository.save(TestFixtures.donorUser(email = "alice@example.com"))
        val bob = userRepository.save(TestFixtures.donorUser(email = "bob@example.com", displayName = "Bob"))

        donorProfileRepository.save(TestFixtures.donorProfile(user = alice, displayName = "Alice D."))
        // bob a un profil anonyme
        donorProfileRepository.save(TestFixtures.donorProfile(user = bob, displayName = null, anonymous = true))

        aliceId = alice.id!!
        bobId = bob.id!!

        em.flush()
        em.clear()
    }

    @Test
    fun `findByUserId retourne le profil quand il existe`() {
        val result = donorProfileRepository.findByUserId(aliceId)

        assertThat(result).isPresent
        assertThat(result.get().displayName).isEqualTo("Alice D.")
        assertThat(result.get().anonymous).isFalse
    }

    @Test
    fun `findByUserId retourne le profil anonyme correctement`() {
        val result = donorProfileRepository.findByUserId(bobId)

        assertThat(result).isPresent
        assertThat(result.get().displayName).isNull()
        assertThat(result.get().anonymous).isTrue
    }

    @Test
    fun `findByUserId retourne empty pour un userId inconnu`() {
        val result = donorProfileRepository.findByUserId(UUID.randomUUID())

        assertThat(result).isEmpty
    }

    @Test
    fun `save persiste un profil et génère un UUID`() {
        val user = userRepository.save(TestFixtures.donorUser(email = "new-donor@example.com"))
        val profile = TestFixtures.donorProfile(user = user, displayName = "New Donor")

        val saved = donorProfileRepository.save(profile)

        assertThat(saved.id).isNotNull
        assertThat(donorProfileRepository.findByUserId(user.id!!)).isPresent
    }

    @Test
    fun `le profil est lié au bon utilisateur (relation user chargée lazily)`() {
        val result = donorProfileRepository.findByUserId(aliceId)

        assertThat(result).isPresent
        // On accède à user.id sans déclencher de N+1 (on vérifie juste la FK)
        val profile = result.get()
        em.refresh(profile)  // force le chargement de la relation lazy dans la transaction
        assertThat(profile.user.id).isEqualTo(aliceId)
    }
}
