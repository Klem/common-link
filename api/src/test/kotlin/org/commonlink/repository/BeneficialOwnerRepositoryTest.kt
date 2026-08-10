package org.commonlink.repository

import org.assertj.core.api.Assertions.assertThat
import org.commonlink.entity.BeneficialOwner
import org.commonlink.entity.BeneficialOwnerOrigin
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.TestPropertySource
import java.time.Instant
import java.util.UUID

/**
 * Vérifie que [name] et [dateOfBirth] sont bien chiffrés en base lorsque la clé est configurée,
 * et que [BeneficialOwnerRepository.existsByAssociationIdAndDiscardedFalse] est correct.
 *
 * Requiert Docker (Testcontainers Postgres).
 *
 * @TestPropertySource active le chiffrement avec une clé 256 bits de test (32 octets nuls encodés
 * en Base64) — identique au comportement prod, mais jamais utilisée avec des données réelles.
 */
@TestPropertySource(
    properties = ["commonlink.compliance.encryption-key=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA="]
)
class BeneficialOwnerRepositoryTest(
    @Autowired private val repository: BeneficialOwnerRepository,
    @Autowired private val userRepository: UserRepository,
    @Autowired private val associationProfileRepository: AssociationProfileRepository,
    @Autowired private val em: TestEntityManager,
    @Autowired private val jdbcTemplate: JdbcTemplate,
) : AbstractRepositoryTest() {

    private fun persistedAssociationId(): UUID {
        val user = userRepository.save(TestFixtures.associationUser(email = "ubo-test@example.com"))
        val profile = associationProfileRepository.save(TestFixtures.associationProfile(user = user))
        em.flush()
        return profile.id!!
    }

    @Test
    fun `name is stored encrypted — raw SQL column starts with v1 and does not contain plaintext`() {
        val associationId = persistedAssociationId()
        val owner = repository.save(
            BeneficialOwner(
                associationId = associationId,
                name = "Marie Curie",
                origin = BeneficialOwnerOrigin.REGISTRY,
                collectedAt = Instant.now(),
                confirmedBy = UUID.randomUUID(),
            )
        )
        em.flush()
        em.clear()

        val rawName = jdbcTemplate.queryForObject(
            "SELECT name FROM beneficial_owner WHERE id = ?",
            String::class.java,
            owner.id,
        )
        assertThat(rawName).startsWith("v1:")
        assertThat(rawName).doesNotContain("Marie Curie")
    }

    @Test
    fun `date of birth is stored encrypted when provided`() {
        val associationId = persistedAssociationId()
        val owner = repository.save(
            BeneficialOwner(
                associationId = associationId,
                name = "Marie Curie",
                dateOfBirth = "1867-11-07",
                origin = BeneficialOwnerOrigin.STATUTS,
                collectedAt = Instant.now(),
                confirmedBy = UUID.randomUUID(),
            )
        )
        em.flush()
        em.clear()

        val rawDob = jdbcTemplate.queryForObject(
            "SELECT date_of_birth FROM beneficial_owner WHERE id = ?",
            String::class.java,
            owner.id,
        )
        assertThat(rawDob).startsWith("v1:")
        assertThat(rawDob).doesNotContain("1867-11-07")
    }

    @Test
    fun `existsByAssociationIdAndDiscardedFalse returns true when at least one active owner exists`() {
        val associationId = persistedAssociationId()
        repository.save(
            BeneficialOwner(
                associationId = associationId,
                name = "Active Owner",
                origin = BeneficialOwnerOrigin.REGISTRY,
                collectedAt = Instant.now(),
                confirmedBy = UUID.randomUUID(),
            )
        )
        em.flush()

        assertThat(repository.existsByAssociationIdAndDiscardedFalse(associationId)).isTrue()
    }

    @Test
    fun `existsByAssociationIdAndDiscardedFalse returns false when all owners are discarded`() {
        val associationId = persistedAssociationId()
        repository.save(
            BeneficialOwner(
                associationId = associationId,
                name = "Discarded Owner",
                origin = BeneficialOwnerOrigin.REGISTRY,
                collectedAt = Instant.now(),
                confirmedBy = UUID.randomUUID(),
                discarded = true,
            )
        )
        em.flush()

        assertThat(repository.existsByAssociationIdAndDiscardedFalse(associationId)).isFalse()
    }
}
