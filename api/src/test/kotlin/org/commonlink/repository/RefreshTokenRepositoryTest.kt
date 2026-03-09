package org.commonlink.repository

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID

class RefreshTokenRepositoryTest(
    @Autowired private val userRepository: UserRepository,
    @Autowired private val refreshTokenRepository: RefreshTokenRepository,
    @Autowired private val em: TestEntityManager,
) : AbstractRepositoryTest() {

    private lateinit var aliceId: UUID
    private lateinit var bobId: UUID

    private val aliceToken1Hash = "alice1" + "0".repeat(58)
    private val aliceToken2Hash = "alice2" + "0".repeat(58)
    private val aliceRevokedHash = "aliceR" + "0".repeat(58)
    private val bobToken1Hash = "bob001" + "0".repeat(58)

    @BeforeEach
    fun seed() {
        val alice = userRepository.save(TestFixtures.donorUser(email = "alice@example.com"))
        val bob = userRepository.save(TestFixtures.donorUser(email = "bob@example.com"))

        aliceId = alice.id!!
        bobId = bob.id!!

        // Alice : 2 tokens actifs + 1 révoqué
        refreshTokenRepository.save(TestFixtures.refreshToken(user = alice, tokenHash = aliceToken1Hash))
        refreshTokenRepository.save(TestFixtures.refreshToken(user = alice, tokenHash = aliceToken2Hash))
        refreshTokenRepository.save(
            TestFixtures.refreshToken(user = alice, tokenHash = aliceRevokedHash, revoked = true)
        )
        // Bob : 1 token actif
        refreshTokenRepository.save(TestFixtures.refreshToken(user = bob, tokenHash = bobToken1Hash))

        em.flush()
        em.clear()
    }

    @Test
    fun `findByTokenHash retourne le token quand le hash existe`() {
        val result = refreshTokenRepository.findByTokenHash(aliceToken1Hash)

        assertThat(result).isPresent
        assertThat(result.get().revoked).isFalse
    }

    @Test
    fun `findByTokenHash retourne empty pour un hash inconnu`() {
        val result = refreshTokenRepository.findByTokenHash("0".repeat(64))

        assertThat(result).isEmpty
    }

    @Test
    fun `findAllByUserIdAndRevokedFalse retourne uniquement les tokens actifs`() {
        val result = refreshTokenRepository.findAllByUserIdAndRevokedFalse(aliceId)

        // Alice a 2 actifs + 1 révoqué → doit retourner 2
        assertThat(result).hasSize(2)
        assertThat(result.map { it.tokenHash }).containsExactlyInAnyOrder(aliceToken1Hash, aliceToken2Hash)
        assertThat(result.all { !it.revoked }).isTrue
    }

    @Test
    fun `findAllByUserIdAndRevokedFalse retourne empty pour un user sans token actif`() {
        // Révoquer le seul token de bob
        val bobToken = refreshTokenRepository.findByTokenHash(bobToken1Hash).get()
        bobToken.revoked = true
        refreshTokenRepository.save(bobToken)
        em.flush()
        em.clear()

        val result = refreshTokenRepository.findAllByUserIdAndRevokedFalse(bobId)

        assertThat(result).isEmpty()
    }

    @Test
    fun `findAllByUserIdAndRevokedFalse retourne empty pour un userId inconnu`() {
        val result = refreshTokenRepository.findAllByUserIdAndRevokedFalse(UUID.randomUUID())

        assertThat(result).isEmpty()
    }

    @Test
    @Transactional  // revokeAllByUserId est @Modifying → nécessite une transaction active
    fun `revokeAllByUserId révoque tous les tokens actifs de l'utilisateur`() {
        refreshTokenRepository.revokeAllByUserId(aliceId)
        em.flush()
        em.clear()

        val activeAfter = refreshTokenRepository.findAllByUserIdAndRevokedFalse(aliceId)
        assertThat(activeAfter).isEmpty()

        // Les tokens existent toujours en DB (pas de DELETE)
        val allAliceTokens = refreshTokenRepository.findAll()
            .filter { it.user.id == aliceId }
        assertThat(allAliceTokens).hasSize(3)
        assertThat(allAliceTokens.all { it.revoked }).isTrue
    }

    @Test
    @Transactional
    fun `revokeAllByUserId ne touche pas les tokens d'un autre utilisateur`() {
        refreshTokenRepository.revokeAllByUserId(aliceId)
        em.flush()
        em.clear()

        val bobActive = refreshTokenRepository.findAllByUserIdAndRevokedFalse(bobId)
        assertThat(bobActive).hasSize(1)
        assertThat(bobActive.first().tokenHash).isEqualTo(bobToken1Hash)
    }

    @Test
    fun `un token expiré peut encore être trouvé par hash (expiration gérée applicativement)`() {
        val user = userRepository.save(TestFixtures.donorUser(email = "expired@example.com"))
        val expiredHash = "expir1" + "0".repeat(58)
        val expiredToken = TestFixtures.refreshToken(
            user = user,
            tokenHash = expiredHash,
            expiresAt = Instant.now().minusSeconds(3600),  // expiré il y a 1h
        )
        refreshTokenRepository.save(expiredToken)
        em.flush()
        em.clear()

        // Le repository ne filtre pas sur expiresAt → c'est la responsabilité du service
        val result = refreshTokenRepository.findByTokenHash(expiredHash)
        assertThat(result).isPresent
        assertThat(result.get().expiresAt).isBefore(Instant.now())
    }
}
