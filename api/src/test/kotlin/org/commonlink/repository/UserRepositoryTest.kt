package org.commonlink.repository

import org.assertj.core.api.Assertions.assertThat
import org.commonlink.entity.AuthProvider
import org.commonlink.entity.UserRole
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager

class UserRepositoryTest(
    @Autowired private val userRepository: UserRepository,
    @Autowired private val em: TestEntityManager,
) : AbstractRepositoryTest() {

    // Les fixtures sont insérées dans la transaction du test → rollback automatique.
    // Pas besoin de @AfterEach / DELETE.
    @BeforeEach
    fun seed() {
        userRepository.saveAll(
            listOf(
                TestFixtures.donorUser(email = "alice@example.com"),
                TestFixtures.donorUser(email = "bob@example.com", displayName = "Bob Martin"),
                TestFixtures.associationUser(email = "asso1@example.com"),
                TestFixtures.googleUser(email = "google1@gmail.com", googleSub = "google-sub-001"),
                TestFixtures.googleUser(email = "google2@gmail.com", googleSub = "google-sub-002", role = UserRole.ASSOCIATION),
            )
        )
        // flush pour que les requêtes JPQL voient les données sans attendre le commit
        em.flush()
        em.clear()
    }

    @Test
    fun `findByEmail retourne l'utilisateur quand l'email existe`() {
        val result = userRepository.findByEmail("alice@example.com")

        assertThat(result).isPresent
        assertThat(result.get().email).isEqualTo("alice@example.com")
        assertThat(result.get().role).isEqualTo(UserRole.DONOR)
        assertThat(result.get().displayName).isEqualTo("Alice Dupont")
    }

    @Test
    fun `findByEmail retourne empty quand l'email n'existe pas`() {
        val result = userRepository.findByEmail("inconnu@example.com")

        assertThat(result).isEmpty
    }

    @Test
    fun `findByEmail est case-sensitive`() {
        // PostgreSQL est case-sensitive par défaut sur les varchar
        val result = userRepository.findByEmail("ALICE@EXAMPLE.COM")

        assertThat(result).isEmpty
    }

    @Test
    fun `findByGoogleSub retourne l'utilisateur quand le sub existe`() {
        val result = userRepository.findByGoogleSub("google-sub-001")

        assertThat(result).isPresent
        assertThat(result.get().email).isEqualTo("google1@gmail.com")
        assertThat(result.get().provider).isEqualTo(AuthProvider.GOOGLE)
    }

    @Test
    fun `findByGoogleSub retourne empty quand le sub n'existe pas`() {
        val result = userRepository.findByGoogleSub("google-sub-inconnu")

        assertThat(result).isEmpty
    }

    @Test
    fun `existsByEmail retourne true quand l'email existe`() {
        assertThat(userRepository.existsByEmail("asso1@example.com")).isTrue
    }

    @Test
    fun `existsByEmail retourne false quand l'email n'existe pas`() {
        assertThat(userRepository.existsByEmail("fantome@example.com")).isFalse
    }

    @Test
    fun `save persiste un nouvel utilisateur et génère un UUID`() {
        val newUser = TestFixtures.donorUser(email = "new@example.com")

        val saved = userRepository.save(newUser)

        assertThat(saved.id).isNotNull
        assertThat(userRepository.findByEmail("new@example.com")).isPresent
    }

    @Test
    fun `findAll retourne tous les utilisateurs seedés`() {
        val all = userRepository.findAll()

        assertThat(all).hasSize(5)
    }

    @Test
    fun `les 5 utilisateurs couvrent les deux rôles et les trois providers`() {
        val all = userRepository.findAll()

        assertThat(all.map { it.role }).containsExactlyInAnyOrder(
            UserRole.DONOR, UserRole.DONOR, UserRole.ASSOCIATION,
            UserRole.DONOR, UserRole.ASSOCIATION
        )
        assertThat(all.map { it.provider }).containsAll(
            listOf(AuthProvider.EMAIL, AuthProvider.GOOGLE)
        )
    }
}
