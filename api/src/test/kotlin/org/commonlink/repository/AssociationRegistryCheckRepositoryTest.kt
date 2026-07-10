package org.commonlink.repository

import org.assertj.core.api.Assertions.assertThat
import org.commonlink.entity.AssociationRegistryCheck
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager
import java.time.Instant

/**
 * Persistence tests for the append-only [AssociationRegistryCheckRepository].
 * Verifies the JSONB `warnings` round-trip and the "latest scan wins" query.
 *
 * Requires Docker (Testcontainers Postgres) — runs in CI, not locally.
 */
class AssociationRegistryCheckRepositoryTest(
    @Autowired private val registryCheckRepository: AssociationRegistryCheckRepository,
    @Autowired private val userRepository: UserRepository,
    @Autowired private val associationProfileRepository: AssociationProfileRepository,
    @Autowired private val em: TestEntityManager,
) : AbstractRepositoryTest() {

    private fun persistedAssociationId(): java.util.UUID {
        val user = userRepository.save(TestFixtures.associationUser(email = "reg-check@example.com"))
        val profile = associationProfileRepository.save(TestFixtures.associationProfile(user = user))
        em.flush()
        return profile.id!!
    }

    @Test
    fun `findTop returns the most recent scan and round-trips the JSONB warnings`() {
        val associationId = persistedAssociationId()

        registryCheckRepository.save(
            AssociationRegistryCheck(
                associationId = associationId,
                associationExists = true,
                siren = "775671356",
                etatAdministratif = "A",
                warnings = listOf("insee-sirene: timeout"),
                checkedAt = Instant.now().minusSeconds(3600),
            )
        )
        val newer = registryCheckRepository.save(
            AssociationRegistryCheck(
                associationId = associationId,
                associationExists = true,
                siren = "775671356",
                etatAdministratif = "C",
                dissolutionDetected = true,
                warnings = listOf("joafe: 503", "bodacc: timeout"),
                checkedAt = Instant.now(),
            )
        )
        em.flush()
        em.clear()

        val latest = registryCheckRepository.findTopByAssociationIdOrderByCheckedAtDesc(associationId)

        assertThat(latest).isNotNull
        assertThat(latest!!.id).isEqualTo(newer.id)
        assertThat(latest.etatAdministratif).isEqualTo("C")
        assertThat(latest.dissolutionDetected).isTrue()
        assertThat(latest.warnings).containsExactly("joafe: 503", "bodacc: timeout")
    }

    @Test
    fun `findTop returns null when the association was never scanned`() {
        val associationId = persistedAssociationId()

        assertThat(registryCheckRepository.findTopByAssociationIdOrderByCheckedAtDesc(associationId)).isNull()
    }
}
