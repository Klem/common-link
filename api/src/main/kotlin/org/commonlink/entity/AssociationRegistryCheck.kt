package org.commonlink.entity

import jakarta.persistence.*
import java.time.Instant
import java.util.UUID

/**
 * A single automated legal-existence pre-check run against French public registries
 * (Recherche d'entreprises, INSEE Sirene, JOAFE, BODACC) for an association.
 *
 * **Append-only**: one row is inserted per scan and rows are never updated or deleted.
 * This preserves the LCB-FT audit trail — a curator's approve/reject decision freezes a
 * reference to the row that informed it (see [AssociationProfile.decisionRegistryCheckId]),
 * and because the row is immutable that reference is a faithful snapshot.
 *
 * All flags are nullable: `null` means the source was unavailable or the check was not
 * applicable (e.g. no SIREN → INSEE and BODACC are skipped). Informational only — never
 * used to auto-approve or auto-reject a dossier.
 */
@Entity
@Table(name = "association_registry_check")
class AssociationRegistryCheck(

    /** Auto-generated UUID primary key. */
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    val id: UUID? = null,

    /** The association this scan was run for. */
    @Column(name = "association_id", nullable = false, updatable = false)
    val associationId: UUID,

    /** Whether the entity was found as an association in Recherche d'entreprises. */
    @Column(name = "association_exists")
    val associationExists: Boolean? = null,

    /** SIREN (9 digits) resolved from the registry, if any. */
    @Column(name = "siren", length = 9)
    val siren: String? = null,

    /** RNA number (W + 9 digits), if any. */
    @Column(name = "rna", length = 20)
    val rna: String? = null,

    /** INSEE administrative status: 'A' = active, 'C' = ceased. */
    @Column(name = "etat_administratif", length = 1)
    val etatAdministratif: String? = null,

    /** Whether a creation/declaration entry was found in the JOAFE. */
    @Column(name = "joafe_declaration_found")
    val joafeDeclarationFound: Boolean? = null,

    /** Whether a dissolution notice was found in the JOAFE. */
    @Column(name = "dissolution_detected")
    val dissolutionDetected: Boolean? = null,

    /** Whether a collective insolvency procedure was found in BODACC. */
    @Column(name = "bodacc_procedure_found")
    val bodaccProcedureFound: Boolean? = null,

    /** Non-fatal warnings from individual source failures (raw evidence), stored as a JSON array string. */
    @Convert(converter = StringListJsonConverter::class)
    @Column(name = "warnings", nullable = false, columnDefinition = "text")
    val warnings: List<String> = emptyList(),

    /** Representatives of the association collected from consulted registries (name only, no title), stored as a JSON array. */
    @Convert(converter = StringListJsonConverter::class)
    @Column(name = "officers", nullable = false, columnDefinition = "text")
    val officers: List<String> = emptyList(),

    /** Whether the association is active according to the RNA (DJEPVA). Null if the source was unavailable or not applicable. */
    @Column(name = "rna_active")
    val rnaActive: Boolean? = null,

    /** UUID of the curator ([User.id]) who triggered this scan. */
    @Column(name = "checked_by", updatable = false)
    val checkedBy: UUID? = null,

    /** Timestamp when the scan was performed. */
    @Column(name = "checked_at", nullable = false, updatable = false)
    val checkedAt: Instant = Instant.now(),
)
