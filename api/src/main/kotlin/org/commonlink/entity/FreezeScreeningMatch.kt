package org.commonlink.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

/** Fuzzy-matching algorithm that produced a [FreezeScreeningMatch.score]. */
enum class ScreeningAlgorithm { JARO_WINKLER }

/**
 * Evidence of one correspondence between a screened name and an entry of the national
 * asset-freeze register, retained so the compliance officer can motivate a decision.
 *
 * ### Why this exists
 * The compliance journal ([ComplianceAuditLog]) records only aggregates for a
 * `FREEZE_SCREENING_HIT`: match count, top score, threshold, register publication date.
 * That is enough to prove *a control took place*, but not enough to *decide*: the officer
 * cannot tell which register entry was matched, nor on which value. This entity carries the
 * decision-grade evidence that the journal deliberately does not.
 *
 * ### Why it is not stored in the journal
 * `docs/legal/E4-journal-controles-de-gel.md` §4.5 structurally excludes cleartext identities
 * from the journal, on the ground that "l'identité complète est déjà conservée dans les dossiers
 * d'identification auxquels la référence interne renvoie". That ground holds for the *screened
 * subject* only — a register entry belongs to no CommonLink dossier, it is a public official
 * publication. The journal therefore stays byte-identical (its exact field set is asserted by
 * tests) and identity-bearing evidence lives here, purpose-bound and separately retained.
 *
 * ### Retention
 * Five years (art. L.561-12 CMF), aligned with the compliance journal.
 *
 * ### Snapshots, not joins
 * [matchedName], [matchedNature], [matchedLegalReference] and [matchedDateOfBirth] are frozen
 * copies rather than a join to [SanctionedEntity]. `SanctionSyncExecutor` deletes register rows
 * once a measure is lifted; a join would erase the evidence backing a past decision on the day
 * the entry is delisted.
 */
@Entity
@Table(name = "freeze_screening_match")
class FreezeScreeningMatch(

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    val id: UUID? = null,

    /**
     * `sequence_no` of the [ComplianceAuditLog] `FREEZE_SCREENING_HIT` entry that recorded this
     * correspondence. Anchors mutable evidence to the immutable, hash-chained journal.
     *
     * Intentionally not a foreign key: `compliance_audit_log` is protected by `REVOKE` plus an
     * immutability trigger, and a referencing constraint on a deliberately inalterable table adds
     * a lock without adding a guarantee.
     */
    @Column(name = "audit_log_seq_ref", nullable = false, updatable = false)
    val auditLogSeqRef: Long,

    @Enumerated(EnumType.STRING)
    @Column(name = "subject_type", nullable = false, updatable = false, length = 32)
    val subjectType: ComplianceAuditSubjectType,

    /** UUID of the screened party — association, representative, beneficial owner or donor. */
    @Column(name = "subject_id", nullable = false, updatable = false)
    val subjectId: UUID,

    /**
     * Grouping context, so correspondences whose subject is a representative or a beneficial
     * owner can be attached to the alert carried by the association.
     *
     * Null for a donor screening, which has no association context.
     */
    @Column(name = "association_id", updatable = false)
    val associationId: UUID? = null,

    /**
     * The value actually compared, after `NameNormalizer.normalize()` — `"TECHNO +"` is stored
     * as `"TECHNO"`.
     *
     * Frozen at screening time: it is the value that produced [score], and it must survive a
     * later correction of the name held in the dossier. Without it the officer sees a score
     * that no visible pair of strings explains.
     */
    @Column(name = "screened_normalized_name", nullable = false, updatable = false, length = 512)
    val screenedNormalizedName: String,

    /** Registry number assigned by DG Trésor — public reference to the matched register entry. */
    @Column(name = "sanctioned_id_registre", nullable = false, updatable = false)
    val sanctionedIdRegistre: Int,

    @Column(name = "matched_name", nullable = false, updatable = false, length = 512)
    val matchedName: String,

    @Enumerated(EnumType.STRING)
    @Column(name = "matched_nature", nullable = false, updatable = false, length = 32)
    val matchedNature: SanctionedNature,

    /**
     * EU or UN measure reference of the matched entry. The decisive column for a false-positive
     * ruling: it names the sanctions programme the entry falls under.
     */
    @Column(name = "matched_legal_reference", updatable = false, length = 256)
    val matchedLegalReference: String? = null,

    /** Partial date of birth of the matched entry, when it is a physical person. */
    @Column(name = "matched_date_of_birth", updatable = false, length = 32)
    val matchedDateOfBirth: String? = null,

    @Column(name = "score", nullable = false, updatable = false)
    val score: Double,

    @Column(name = "score_threshold", nullable = false, updatable = false)
    val scoreThreshold: Double,

    @Enumerated(EnumType.STRING)
    @Column(name = "algorithm", nullable = false, updatable = false, length = 64)
    val algorithm: ScreeningAlgorithm = ScreeningAlgorithm.JARO_WINKLER,

    /** Publication date of the register version screened against. */
    @Column(name = "registry_publication_date", nullable = false, updatable = false)
    val registryPublicationDate: LocalDate,

    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: Instant = Instant.now(),
)
