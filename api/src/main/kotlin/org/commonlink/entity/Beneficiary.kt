package org.commonlink.entity

import jakarta.persistence.*
import java.time.Instant
import java.util.UUID

/**
 * Represents an organisation or individual that an association designates as a payment recipient.
 *
 * A beneficiary is always owned by one [AssociationProfile] and identified by its French SIREN
 * ([identifier1], 9 digits). The optional [identifier2] stores the SIRET (14 digits) when the
 * specific establishment is known.
 *
 * Each beneficiary can hold multiple verified IBANs through the [ibans] collection. Only IBANs
 * with [IbanVerificationStatus.VERIFIED] status should be used for fund transfers.
 */
@Entity
@Table(name = "beneficiaries")
class Beneficiary(
    /** Auto-generated UUID primary key; null until the entity is persisted. */
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    val id: UUID? = null,

    /** The association that manages this beneficiary.*/
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "association_id", nullable = false)
    val association: AssociationProfile,

    /** Official registered name of the beneficiary organisation. */
    @Column(name = "name", nullable = false, length = 255)
    var name: String,

    /** French SIREN identifier (9 digits) — unique per association. */
    @Column(name = "identifier_1", nullable = false, length = 9)
    val identifier1: String,

    /** French SIRET identifier (14 digits) for a specific establishment. Null when only SIREN is known. */
    @Column(name = "identifier_2", length = 14)
    var identifier2: String? = null,

    /** NAF/APE activity code describing the beneficiary's sector of activity. */
    @Column(name = "activity_code", length = 10)
    var activityCode: String? = null,

    /** Legal or administrative category of the beneficiary (e.g. association loi 1901). */
    @Column(name = "category", length = 100)
    var category: String? = null,

    /** City where the beneficiary is headquartered. */
    @Column(name = "city", length = 255)
    var city: String? = null,

    /** Postal code of the beneficiary's registered address. */
    @Column(name = "postal_code", length = 10)
    var postalCode: String? = null,

    /** Whether this beneficiary is currently active and eligible to receive donations. */
    @Column(name = "active", nullable = false)
    var active: Boolean = true,

    /** Timestamp of record creation; immutable after insert. */
    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: Instant = Instant.now(),

    /** Timestamp of last modification; updated on every save. */
    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant = Instant.now()
) {
    /**
     * Collection of IBAN entries associated with this beneficiary.
     *
     * Managed via cascade: adding or removing from this list persists automatically.
     * Orphaned [BeneficiaryIban] records are deleted when removed from the collection.
     */
    @OneToMany(mappedBy = "beneficiary", cascade = [CascadeType.ALL], orphanRemoval = true)
    val ibans: MutableList<BeneficiaryIban> = mutableListOf()
}
