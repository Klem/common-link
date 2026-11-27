package org.commonlink.repository

import org.assertj.core.api.Assertions.assertThat
import org.commonlink.entity.AssociationProfile
import org.commonlink.entity.HelloAssoLinkAttempt
import org.commonlink.entity.HelloAssoLinkAttemptOutcome
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager
import org.springframework.boot.testcontainers.context.ImportTestcontainers
import org.springframework.data.domain.PageRequest
import org.springframework.jdbc.core.JdbcTemplate
import java.time.Instant

/** Pins the curator statistics queries of [HelloAssoLinkAttemptRepository] and the outcome CHECK (V100). */
@ImportTestcontainers(TestcontainersConfig::class)
class HelloAssoLinkAttemptRepositoryTest(
    @Autowired private val userRepository: UserRepository,
    @Autowired private val associationProfileRepository: AssociationProfileRepository,
    @Autowired private val repository: HelloAssoLinkAttemptRepository,
    @Autowired private val em: TestEntityManager,
    @Autowired private val jdbc: JdbcTemplate,
) : AbstractRepositoryTest() {

    private val from = Instant.parse("2026-11-01T00:00:00Z")
    private val to = Instant.parse("2026-12-01T00:00:00Z")

    private fun association(name: String): AssociationProfile {
        val user = userRepository.save(TestFixtures.associationUser(email = "hla-${System.nanoTime()}@example.com"))
        return associationProfileRepository.save(TestFixtures.associationProfile(user, name = name))
    }

    private fun attempt(association: AssociationProfile, outcome: HelloAssoLinkAttemptOutcome, at: Instant) =
        repository.save(
            HelloAssoLinkAttempt(
                associationId = association.id!!, attemptedAt = at, outcome = outcome, organizationSlug = "fc-mougins",
                organizationName = "FC Mougins", organizationType = "Association1901", associationRna = "W061006975",
            )
        )

    @Test
    fun `counts per outcome and distinct associations within the period only`() {
        val a = association("Football Club de Mougins")
        val b = association("Autre asso")
        attempt(a, HelloAssoLinkAttemptOutcome.NAME_MISMATCH, Instant.parse("2026-11-25T15:00:00Z"))
        attempt(a, HelloAssoLinkAttemptOutcome.NAME_MISMATCH, Instant.parse("2026-11-25T15:05:00Z"))
        attempt(b, HelloAssoLinkAttemptOutcome.MATCHED, Instant.parse("2026-11-20T10:00:00Z"))
        attempt(b, HelloAssoLinkAttemptOutcome.MATCHED, Instant.parse("2026-10-31T23:59:59Z")) // before the period
        attempt(b, HelloAssoLinkAttemptOutcome.MATCHED, to) // end is exclusive
        em.flush()

        val counts = repository.countByOutcome(from, to).associate { it.outcome to it.count }

        assertThat(counts).containsExactlyInAnyOrderEntriesOf(
            mapOf(HelloAssoLinkAttemptOutcome.NAME_MISMATCH to 2L, HelloAssoLinkAttemptOutcome.MATCHED to 1L)
        )
        assertThat(repository.countAssociations(from, to)).isEqualTo(2)
    }

    @Test
    fun `rows come newest first with the association name, optionally filtered by outcome`() {
        val a = association("Football Club de Mougins")
        attempt(a, HelloAssoLinkAttemptOutcome.NAME_MISMATCH, Instant.parse("2026-11-25T15:00:00Z"))
        attempt(a, HelloAssoLinkAttemptOutcome.MATCHED, Instant.parse("2026-11-26T09:00:00Z"))
        em.flush()

        val all = repository.findRows(from, to, null, PageRequest.of(0, 10))
        assertThat(all.totalElements).isEqualTo(2)
        assertThat(all.content.map { it.outcome })
            .containsExactly(HelloAssoLinkAttemptOutcome.MATCHED, HelloAssoLinkAttemptOutcome.NAME_MISMATCH)
        assertThat(all.content.first().associationName).isEqualTo("Football Club de Mougins")
        assertThat(all.content.first().organizationType).isEqualTo("Association1901")

        val mismatches = repository.findRows(from, to, HelloAssoLinkAttemptOutcome.NAME_MISMATCH, PageRequest.of(0, 10))
        assertThat(mismatches.content.single().organizationName).isEqualTo("FC Mougins")
    }

    @Test
    fun `an outcome outside the enum is refused by the CHECK constraint`() {
        val a = association("Asso")
        em.flush()

        assertThrows<Exception> {
            jdbc.update(
                "INSERT INTO helloasso_link_attempts (association_id, outcome) VALUES (?, 'SOMETHING_ELSE')",
                a.id,
            )
        }
    }
}
