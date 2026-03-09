package org.commonlink.repository

import org.assertj.core.api.Assertions.assertThat
import org.commonlink.entity.UserRole
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager
import java.time.Instant

class MagicLinkTokenRepositoryTest(
    @Autowired private val magicLinkTokenRepository: MagicLinkTokenRepository,
    @Autowired private val em: TestEntityManager,
) : AbstractRepositoryTest() {

    // Hashes SHA-256 fictifs de 64 chars hexadécimaux
    private val hashActive   = "a".repeat(64)
    private val hashExpired  = "b".repeat(64)
    private val hashUsed     = "c".repeat(64)
    private val hashDonor2   = "d".repeat(64)

    @BeforeEach
    fun seed() {
        // Token valide (non utilisé, non expiré)
        magicLinkTokenRepository.save(
            TestFixtures.magicLinkToken(
                email = "alice@example.com",
                tokenHash = hashActive,
                role = UserRole.DONOR,
                expiresAt = Instant.now().plusSeconds(900),
            )
        )
        // Token expiré (non utilisé)
        magicLinkTokenRepository.save(
            TestFixtures.magicLinkToken(
                email = "alice@example.com",
                tokenHash = hashExpired,
                role = UserRole.DONOR,
                expiresAt = Instant.now().minusSeconds(3600),  // expiré il y a 1h
            )
        )
        // Token utilisé (usedAt renseigné)
        magicLinkTokenRepository.save(
            TestFixtures.magicLinkToken(
                email = "alice@example.com",
                tokenHash = hashUsed,
                role = UserRole.DONOR,
                expiresAt = Instant.now().plusSeconds(900),
                usedAt = Instant.now().minusSeconds(60),
            )
        )
        // Token valide pour un autre email
        magicLinkTokenRepository.save(
            TestFixtures.magicLinkToken(
                email = "bob@example.com",
                tokenHash = hashDonor2,
                role = UserRole.DONOR,
                expiresAt = Instant.now().plusSeconds(900),
            )
        )

        em.flush()
        em.clear()
    }

    @Test
    fun `findByTokenHash retourne le token quand le hash existe`() {
        val result = magicLinkTokenRepository.findByTokenHash(hashActive)

        assertThat(result).isPresent
        assertThat(result.get().email).isEqualTo("alice@example.com")
        assertThat(result.get().role).isEqualTo(UserRole.DONOR)
    }

    @Test
    fun `findByTokenHash retourne empty pour un hash inconnu`() {
        val result = magicLinkTokenRepository.findByTokenHash("0".repeat(64))

        assertThat(result).isEmpty
    }

    @Test
    fun `findByTokenHashAndUsedAtIsNull retourne le token si non utilisé`() {
        val result = magicLinkTokenRepository.findByTokenHashAndUsedAtIsNull(hashActive)

        assertThat(result).isPresent
        assertThat(result.get().usedAt).isNull()
    }

    @Test
    fun `findByTokenHashAndUsedAtIsNull retourne empty si token déjà utilisé`() {
        val result = magicLinkTokenRepository.findByTokenHashAndUsedAtIsNull(hashUsed)

        // Le token existe mais usedAt est renseigné → doit retourner empty
        assertThat(result).isEmpty
    }

    @Test
    fun `countByEmailAndCreatedAtAfter compte les tokens récents`() {
        // Les 3 tokens d'alice ont été créés à Instant.now() → après "il y a 5 min"
        val cutoff = Instant.now().minusSeconds(300)

        val count = magicLinkTokenRepository.countByEmailAndCreatedAtAfter("alice@example.com", cutoff)

        assertThat(count).isEqualTo(3)
    }

    @Test
    fun `countByEmailAndCreatedAtAfter retourne 0 pour un email sans token récent`() {
        // Cutoff dans le futur → aucun token n'a été créé après ça
        val cutoff = Instant.now().plusSeconds(60)

        val count = magicLinkTokenRepository.countByEmailAndCreatedAtAfter("alice@example.com", cutoff)

        assertThat(count).isEqualTo(0)
    }

    @Test
    fun `countByEmailAndCreatedAtAfter ne compte pas les tokens d'un autre email`() {
        val cutoff = Instant.now().minusSeconds(300)

        val count = magicLinkTokenRepository.countByEmailAndCreatedAtAfter("bob@example.com", cutoff)

        assertThat(count).isEqualTo(1)
    }

    @Test
    fun `marquer un token comme utilisé persiste usedAt`() {
        val token = magicLinkTokenRepository.findByTokenHash(hashActive).get()
        val usedAt = Instant.now()
        token.usedAt = usedAt
        magicLinkTokenRepository.save(token)
        em.flush()
        em.clear()

        // Après flush/clear, findByTokenHashAndUsedAtIsNull ne doit plus le retourner
        assertThat(magicLinkTokenRepository.findByTokenHashAndUsedAtIsNull(hashActive)).isEmpty
    }
}
