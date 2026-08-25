package org.commonlink.repository

import org.commonlink.entity.AssociationProfile
import org.commonlink.entity.AuthProvider
import org.commonlink.entity.Campaign
import org.commonlink.entity.CampaignMilestone
import org.commonlink.entity.CampaignStatus
import org.commonlink.entity.Donation
import org.commonlink.entity.DonorProfile
import org.commonlink.entity.EmailVerificationToken
import org.commonlink.entity.FiscalMandate
import org.commonlink.entity.IbanVerificationStatus
import org.commonlink.entity.MagicLinkToken
import org.commonlink.entity.MandateEligibility
import org.commonlink.entity.MilestoneStatus
import org.commonlink.entity.Payee
import org.commonlink.entity.RiskLevel
import org.commonlink.entity.VerificationStatus
import org.commonlink.entity.PayeeIban
import org.commonlink.entity.Payout
import org.commonlink.entity.PayoutKind
import org.commonlink.entity.PayoutStatus
import org.commonlink.entity.RefreshToken
import org.commonlink.entity.User
import org.commonlink.entity.UserRole
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

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
        contactEmail: String? = "contact@restos-du-coeur.org",
        description: String? = "Aide alimentaire et insertion sociale.",
        verificationStatus: VerificationStatus = VerificationStatus.UNVERIFIED,
        riskLevel: RiskLevel = RiskLevel.STANDARD,
    ) = AssociationProfile(
        user = user,
        name = name,
        identifier = identifier,
        city = city,
        postalCode = postalCode,
        contactName = contactName,
        contactEmail = contactEmail,
        description = description,
        verificationStatus = verificationStatus,
        riskLevel = riskLevel,
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

    // ── Campaigns ────────────────────────────────────────────────────────────

    /**
     * Returns an unpersisted [Campaign] entity for testing.
     *
     * @param association The association profile that owns the campaign.
     * @param name Display name of the campaign.
     * @param goal Total fundraising goal in euros.
     * @param status Initial lifecycle status (defaults to DRAFT).
     */
    fun campaign(
        association: AssociationProfile,
        name: String = "Hiver Solidaire 2025",
        goal: BigDecimal = BigDecimal("40000"),
        status: CampaignStatus = CampaignStatus.DRAFT,
    ) = Campaign(
        association = association,
        name = name,
        emoji = "🌍",
        description = "Campagne de soutien hivernal pour les familles en difficulté.",
        goal = goal,
        status = status,
    )

    /**
     * Returns an unpersisted [CampaignMilestone] entity for testing.
     *
     * @param campaign The campaign this milestone belongs to.
     * @param title Short title of the milestone.
     * @param targetAmount Amount required to reach this milestone.
     * @param sortOrder Display position (lower = first).
     * @param status Initial milestone status (defaults to LOCKED).
     */
    fun milestone(
        campaign: Campaign,
        title: String = "Urgence Chauffage",
        targetAmount: BigDecimal = BigDecimal("5000"),
        sortOrder: Int = 0,
        status: MilestoneStatus = MilestoneStatus.LOCKED,
    ) = CampaignMilestone(
        campaign = campaign,
        emoji = "❄️",
        title = title,
        description = "Matériel de chauffage d'urgence pour 8 familles.",
        targetAmount = targetAmount,
        sortOrder = sortOrder,
        status = status,
    )

    /**
     * Returns an unpersisted [Donation] entity for testing.
     *
     * The [providerRef] defaults to a random UUID-based string to avoid UNIQUE constraint violations.
     *
     * @param donor The donor profile making the donation.
     * @param campaign The campaign receiving the donation.
     * @param amount Donation amount in euros.
     * @param confirmedAt When the payment was confirmed; null means unconfirmed.
     * @param typeCode Plan comptable prefix for budget variance reporting (default "74").
     * @param createdAt Overridable so tests can simulate a donation old enough to be picked up by
     *   [org.commonlink.service.MolliePaymentReconciler]'s staleness threshold.
     */
    fun donation(
        donor: DonorProfile,
        campaign: Campaign,
        amount: BigDecimal = BigDecimal("50.00"),
        providerRef: String = "test:${UUID.randomUUID()}",
        confirmedAt: Instant? = Instant.now(),
        typeCode: String = "74",
        riskLevel: RiskLevel = RiskLevel.STANDARD,
        createdAt: Instant = Instant.now(),
    ) = Donation(
        donor = donor,
        campaign = campaign,
        amount = amount,
        providerRef = providerRef,
        confirmedAt = confirmedAt,
        typeCode = typeCode,
        riskLevel = riskLevel,
        createdAt = createdAt,
    )

    // ── Mandates ─────────────────────────────────────────────────────────────

    fun fiscalMandate(
        association: AssociationProfile,
        eligibility: MandateEligibility = MandateEligibility.OIG_66,
        reference: String = "MND-2026-0001",
        signedAt: Instant = Instant.now(),
    ) = FiscalMandate(
        association = association,
        eligibility = eligibility,
        reference = reference,
        signedAt = signedAt,
    )

    // ── Payees ───────────────────────────────────────────────────────────────

    fun payee(
        association: AssociationProfile,
        name: String = "École Kaolack SARL",
        identifier1: String = "123456789",
    ) = Payee(
        association = association,
        name = name,
        identifier1 = identifier1,
    )

    fun payeeIban(
        payee: Payee,
        iban: String = "FR7630006000011234567890189",
        status: IbanVerificationStatus = IbanVerificationStatus.VERIFIED,
    ) = PayeeIban(
        payee = payee,
        iban = iban,
        status = status,
    )

    fun payout(
        campaign: Campaign,
        payee: Payee,
        payeeIban: PayeeIban,
        amount: BigDecimal = BigDecimal("500.00"),
        kind: PayoutKind = PayoutKind.EXPENSE,
        typeCode: String = "60-mat",
        label: String = "Achat matériel pédagogique — facture FAC-001",
        status: PayoutStatus = PayoutStatus.PENDING,
    ) = Payout(
        campaign = campaign,
        payee = payee,
        payeeIbanId = payeeIban.id!!,
        payeeIbanValue = payeeIban.iban,
        amount = amount,
        kind = kind,
        typeCode = typeCode,
        label = label,
        status = status,
    )
}
