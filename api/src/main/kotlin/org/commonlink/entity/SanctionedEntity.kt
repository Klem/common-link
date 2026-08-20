package org.commonlink.entity

import jakarta.persistence.*
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

enum class SanctionedNature {
    PHYSICAL_PERSON, LEGAL_ENTITY, VESSEL;

    companion object {
        fun fromDgtresor(value: String): SanctionedNature = when (value.trim()) {
            "Personne physique" -> PHYSICAL_PERSON
            "Personne morale"   -> LEGAL_ENTITY
            "Navire"            -> VESSEL
            else -> throw IllegalArgumentException("Unknown DG Trésor nature value (register format may have changed)")
        }
    }
}

/**
 * An entry in the national asset-freeze register published by the Direction générale
 * du Trésor (DG Trésor). The register consolidates French, EU and UN measures
 * applicable in France — decision D2: a single source, a single normalization path,
 * a single point of failure to monitor.
 *
 * **Lifecycle** — Rows are upserted on each ingestion ([idRegistre] is the deduplication
 * and update key) and deleted when the measure is lifted from the published register.
 * This table is mutable: DG Trésor may update details for an active measure (new alias,
 * corrected date of birth). The [ingestedAt] field records the last update time.
 *
 * **Name normalization** — [normalizedNames] is a JSON array computed via
 * [org.commonlink.util.NameNormalizer] at ingestion. The same function is used to
 * normalize queries in [org.commonlink.service.SanctionScreeningService], ensuring
 * no storage/query asymmetry.
 */
@Entity
@Table(
    name = "sanctioned_entity",
    uniqueConstraints = [UniqueConstraint(name = "uq_sanctioned_entity_id_registre", columnNames = ["id_registre"])],
)
class SanctionedEntity(

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    val id: UUID? = null,

    /** Unique registry number assigned by DG Trésor — deduplication and update key. */
    @Column(name = "id_registre", nullable = false, unique = true)
    val idRegistre: Int,

    @Enumerated(EnumType.STRING)
    @Column(name = "nature", nullable = false, length = 32)
    var nature: SanctionedNature,

    /** Full raw name as received from the register (Nom + Prénom for PP, Dénomination for PM/Navire). */
    @Column(name = "nom", nullable = false, length = 512)
    var nom: String,

    /**
     * All normalized name variants for this entry: main name (NOM PRENOM), reversed
     * (PRENOM NOM), and each alias in both orderings.
     *
     * Stored as a JSON array string via [StringListJsonConverter]. Only
     * [org.commonlink.util.NameNormalizer] is used to produce these values — never
     * SQL functions such as `upper()` or `unaccent()`.
     */
    @Convert(converter = StringListJsonConverter::class)
    @Column(name = "normalized_names", nullable = false, columnDefinition = "text")
    var normalizedNames: List<String>,

    /** Partial date of birth for physical persons: "DD/MM/YYYY", "MM/YYYY", or "YYYY". */
    @Column(name = "date_of_birth", length = 32)
    var dateOfBirth: String? = null,

    /** EU or UN resolution reference, if any. */
    @Column(name = "legal_reference", length = 256)
    var legalReference: String? = null,

    /** Publication date of the DG Trésor register from which this entry was ingested. */
    @Column(name = "publication_date", nullable = false)
    var publicationDate: LocalDate,

    @Column(name = "ingested_at", nullable = false)
    var ingestedAt: Instant = Instant.now(),
)
