package org.commonlink.entity

import jakarta.persistence.*
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

/**
 * Represents a confirmed donation from a [DonorProfile] to a [Campaign].
 *
 * [providerRef] identifies the payment provider transaction (e.g. "stripe:pi_..." or "monerium:...").
 * [confirmedAt] is set when the payment is confirmed and the on-chain recording job is enqueued.
 */
@Entity
@Table(name = "donations")
class Donation(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    val id: UUID? = null,

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "donor_id", nullable = false)
    val donor: DonorProfile,

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "campaign_id", nullable = false)
    val campaign: Campaign,

    @Column(name = "amount", nullable = false, precision = 12, scale = 2)
    val amount: BigDecimal,

    /** Payment provider transaction reference. Format: "stripe:pi_..." or "monerium:<uuid>". */
    @Column(name = "provider_ref", nullable = false, length = 255)
    val providerRef: String,

    /** Set when payment is confirmed and on-chain recording job is enqueued. */
    @Column(name = "confirmed_at")
    var confirmedAt: Instant? = null,

    /** French plan comptable prefix used for budget variance reporting. Default "74" (subventions). */
    @Column(name = "type_code", nullable = false, length = 50)
    var typeCode: String = "74",

    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: Instant = Instant.now(),

    /** Site tiers auto-déclaré via le snippet widget (best-effort, nettoyé à la saisie — ne pas faire confiance). */
    @Column(name = "source_site", length = 255)
    val sourceSite: String? = null,

    /** Snapshot d'identité à la date du don, requis pour le reçu fiscal Cerfa 2041-RD. */
    @Column(name = "donor_full_name", length = 255)
    val donorFullName: String? = null,

    /** Snapshot d'identité — ligne 1 de l'adresse postale. */
    @Column(name = "donor_address_line1", length = 255)
    val donorAddressLine1: String? = null,

    /** Snapshot d'identité — complément d'adresse (optionnel). */
    @Column(name = "donor_address_line2", length = 255)
    val donorAddressLine2: String? = null,

    /** Snapshot d'identité — code postal. */
    @Column(name = "donor_postal_code", length = 16)
    val donorPostalCode: String? = null,

    /** Snapshot d'identité — ville. */
    @Column(name = "donor_city", length = 128)
    val donorCity: String? = null,

    /** Snapshot d'identité — code pays ISO-3166 sur 2 lettres (ex. "FR"). */
    @Column(name = "donor_country", length = 2)
    val donorCountry: String? = null,

    /** Snapshot d'identité — date de naissance, requis pour le reçu fiscal Cerfa 2041-RD. */
    @Column(name = "donor_birth_date")
    val donorBirthDate: LocalDate? = null,

    /** Snapshot d'identité — ville de naissance. */
    @Column(name = "donor_birth_city", length = 128)
    val donorBirthCity: String? = null,

    /** AML/CFT risk level recorded at donation time. Defaults to STANDARD. */
    @Enumerated(EnumType.STRING)
    @Column(name = "risk_level", nullable = false, length = 20)
    val riskLevel: RiskLevel = RiskLevel.STANDARD,
)
