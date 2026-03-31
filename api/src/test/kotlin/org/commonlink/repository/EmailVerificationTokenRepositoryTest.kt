package org.commonlink.repository

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager
import java.time.Instant

class EmailVerificationTokenRepositoryTest(
    @Autowired private val emailVerificationTokenRepository: EmailVerificationTokenRepository,
    @Autowired private val userRepository: UserRepository,
    @Autowired private val em: TestEntityManager,
) : AbstractRepositoryTest() {

    // Hashes SHA-256 fictifs de 64 chars hexadécimaux
    private val hashActive1  = "a".repeat(64)
    private val hashActive2  = "b".repeat(64)
    private val hashExpired  = "c".repeat(64)
    private val hashUsed     = "d".repeat(64)
    private val hashOtherUser = "e".repeat(64)

    @BeforeEach
    fun seed() {
        val alice = userRepository.save(TestFixtures.donorUser(email = "alice@example.com"))
        val bob   = userRepository.save(TestFixtures.donorUser(email = "bob@example.com"))

        // Token actif 1 (alice)
        emailVerificationTokenRepository.save(
            TestFixtures.emailVerificationToken(
                user = alice,
                tokenHash = hashActive1,
                expiresAt = Instant.now().plusSeconds(86400),
            )
        )
        // Token actif 2 (alice)
        emailVerificationTokenRepository.save(
            TestFixtures.emailVerificationToken(
                user = alice,
                tokenHash = hashActive2,
                expiresAt = Instant.now().plusSeconds(86400),
            )
        )
        // Token expiré (alice) — non utilisé mais expiré
        emailVerificationTokenRepository.save(
            TestFixtures.emailVerificationToken(
                user = alice,
                tokenHash = hashExpired,
                expiresAt = Instant.now().minusSeconds(3600),
            )
        )
        // Token utilisé (alice)
        emailVerificationTokenRepository.save(
            TestFixtures.emailVerificationToken(
                user = alice,
                tokenHash = hashUsed,
                expiresAt = Instant.now().plusSeconds(86400),
                usedAt = Instant.now().minusSeconds(60),
            )
        )
        // Token actif pour un autre utilisateur (bob)
        emailVerificationTokenRepository.save(
            TestFixtures.emailVerificationToken(
                user = bob,
                tokenHash = hashOtherUser,
                expiresAt = Instant.now().plusSeconds(86400),
            )
        )

        em.flush()
        em.clear()
    }

    // ── findByTokenHashAndUsedAtIsNull ────────────────────────────────────────

    @Test
    fun `findByTokenHashAndUsedAtIsNull retourne le token si non utilisé`() {
        val result = emailVerificationTokenRepository.findByTokenHashAndUsedAtIsNull(hashActive1)

        assertThat(result).isPresent
        assertThat(result.get().usedAt).isNull()
        assertThat(result.get().tokenHash).isEqualTo(hashActive1)
    }

    @Test
    fun `findByTokenHashAndUsedAtIsNull retourne empty pour un hash inconnu`() {
        val result = emailVerificationTokenRepository.findByTokenHashAndUsedAtIsNull("0".repeat(64))

        assertThat(result).isEmpty
    }

    @Test
    fun `findByTokenHashAndUsedAtIsNull retourne empty si token déjà utilisé`() {
        val result = emailVerificationTokenRepository.findByTokenHashAndUsedAtIsNull(hashUsed)

        assertThat(result).isEmpty
    }

    // ── countByUserIdAndCreatedAtAfter ────────────────────────────────────────

    @Test
    fun `countByUserIdAndCreatedAtAfter compte les tokens récents pour un utilisateur`() {
        val alice = userRepository.findByEmail("alice@example.com").get()
        val cutoff = Instant.now().minusSeconds(300)

        // alice a 4 tokens (actif1, actif2, expiré, utilisé) tous créés à Instant.now()
        val count = emailVerificationTokenRepository.countByUserIdAndCreatedAtAfter(alice.id!!, cutoff)

        assertThat(count).isEqualTo(4)
    }

    @Test
    fun `countByUserIdAndCreatedAtAfter retourne 0 avec un cutoff dans le futur`() {
        val alice = userRepository.findByEmail("alice@example.com").get()
        val cutoff = Instant.now().plusSeconds(60)

        val count = emailVerificationTokenRepository.countByUserIdAndCreatedAtAfter(alice.id!!, cutoff)

        assertThat(count).isEqualTo(0)
    }

    @Test
    fun `countByUserIdAndCreatedAtAfter ne compte pas les tokens d'un autre utilisateur`() {
        val bob = userRepository.findByEmail("bob@example.com").get()
        val cutoff = Instant.now().minusSeconds(300)

        val count = emailVerificationTokenRepository.countByUserIdAndCreatedAtAfter(bob.id!!, cutoff)

        assertThat(count).isEqualTo(1)
    }

    // ── Mutation : marquer comme utilisé ─────────────────────────────────────

    @Test
    fun `marquer un token comme utilisé persiste usedAt et le masque du filtre null`() {
        val token = emailVerificationTokenRepository.findByTokenHashAndUsedAtIsNull(hashActive1).get()
        token.usedAt = Instant.now()
        emailVerificationTokenRepository.save(token)
        em.flush()
        em.clear()

        assertThat(emailVerificationTokenRepository.findByTokenHashAndUsedAtIsNull(hashActive1)).isEmpty
    }
}
