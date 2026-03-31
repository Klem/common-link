package org.commonlink.repository

import org.commonlink.entity.AssociationProfile
import org.commonlink.entity.AuthProvider
import org.commonlink.entity.DonorProfile
import org.commonlink.entity.EmailVerificationToken
import org.commonlink.entity.MagicLinkToken
import org.commonlink.entity.RefreshToken
import org.commonlink.entity.User
import org.commonlink.entity.UserRole
import java.time.Instant

/**
 * Factories de données de test pour les entités du domaine.
 *
 * Principe :
 *  - Chaque méthode retourne une entité NON persistée (id = null)
 *  - Les paramètres ont des valeurs par défaut sensées pour réduire le bruit dans les tests
 *  - Les tests appellent repository.save(fixture()) pour persister et récupérer l'id généré
 *  - Comme @DataJpaTest est @Transactional, tout est rollback après chaque test
 *
 * Pas de duplication d'UUID car la DB génère les ids via gen_random_uuid().
 */
object TestFixtures {

    // ── Users ────────────────────────────────────────────────────────────────

    fun donorUser(
        email: String = "donor@example.com",
        provider: AuthProvider = AuthProvider.EMAIL,
        displayName: String? = "Alice Dupont",
        emailVerified: Boolean = true,
    ) = User(
        email = email,
        role = UserRole.DONOR,
        provider = provider,
        displayName = displayName,
        emailVerified = emailVerified,
    )

    fun associationUser(
        email: String = "asso@example.com",
        provider: AuthProvider = AuthProvider.EMAIL,
        displayName: String? = "Les Restos du Coeur",
        emailVerified: Boolean = true,
    ) = User(
        email = email,
        role = UserRole.ASSOCIATION,
        provider = provider,
        displayName = displayName,
        emailVerified = emailVerified,
    )

    fun googleUser(
        email: String = "google-user@gmail.com",
        googleSub: String = "google-sub-12345",
        role: UserRole = UserRole.DONOR,
    ) = User(
        email = email,
        role = role,
        provider = AuthProvider.GOOGLE,
        googleSub = googleSub,
        emailVerified = true,
    )

    // ── Profiles ─────────────────────────────────────────────────────────────

    fun donorProfile(
        user: User,
        displayName: String? = "Alice D.",
        anonymous: Boolean = false,
    ) = DonorProfile(
        user = user,
        displayName = displayName,
        anonymous = anonymous,
    )

    fun associationProfile(
        user: User,
        name: String = "Les Restos du Coeur",
        identifier: String = "775671356",  // SIRET fictif 9 chiffres (format RNA/SIRET)
        city: String? = "Paris",
        postalCode: String? = "75001",
        contactName: String? = "Jean Martin",
        description: String? = "Aide alimentaire et insertion sociale.",
        verified: Boolean = false,
    ) = AssociationProfile(
        user = user,
        name = name,
        identifier = identifier,
        city = city,
        postalCode = postalCode,
        contactName = contactName,
        description = description,
        verified = verified,
    )

    // ── Tokens ───────────────────────────────────────────────────────────────

    fun magicLinkToken(
        email: String = "donor@example.com",
        tokenHash: String = "abc123def456" + "0".repeat(52),  // 64 chars
        role: UserRole = UserRole.DONOR,
        expiresAt: Instant = Instant.now().plusSeconds(900),   // 15 min
        usedAt: Instant? = null,
    ) = MagicLinkToken(
        email = email,
        tokenHash = tokenHash,
        role = role,
        expiresAt = expiresAt,
        usedAt = usedAt,
    )

    fun emailVerificationToken(
        user: User,
        tokenHash: String = "emailtoken" + "0".repeat(54),  // 64 chars
        expiresAt: Instant = Instant.now().plusSeconds(86400),  // 24h
        usedAt: Instant? = null,
    ) = EmailVerificationToken(
        user = user,
        tokenHash = tokenHash,
        expiresAt = expiresAt,
        usedAt = usedAt,
    )

    fun refreshToken(
        user: User,
        tokenHash: String = "refresh" + "0".repeat(57),  // 64 chars
        expiresAt: Instant = Instant.now().plusSeconds(604800),  // 7 jours
        revoked: Boolean = false,
    ) = RefreshToken(
        user = user,
        tokenHash = tokenHash,
        expiresAt = expiresAt,
        revoked = revoked,
    )
}
