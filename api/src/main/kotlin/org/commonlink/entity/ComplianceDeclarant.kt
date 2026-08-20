package org.commonlink.entity

import jakarta.persistence.Column
import jakarta.persistence.Convert
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

/**
 * Identity record of a natural person authorised to file a suspicious-transaction report
 * (déclaration de soupçon) with TRACFIN via the ERMES télédéclaration portal.
 *
 * ### Encrypted fields
 *
 * [teledeclarantNumber] and [fullName] are encrypted at rest via [ComplianceCryptoConverter]
 * (AES-256-GCM, a fresh random IV per write). Neither field carries an index: because the IV
 * is non-deterministic, two encryptions of the same value produce different ciphertexts, which
 * makes an equality index semantically meaningless as well as useless. Any lookup is by [id]
 * or [userId].
 *
 * **This class must not be converted to a `data class`**: Kotlin's generated [toString] would
 * include both encrypted fields, leaking their decrypted values into any exception message,
 * log line, or Sentry breadcrumb — even at DEBUG level.
 *
 * ### Revocation vs. deletion
 *
 * A designation is never deleted. When a person's mandate ends, [revokedAt] is set to the
 * date of revocation. This preserves the audit trail: TRACFIN may ask, after the fact, who
 * was authorised to file at a given point in time. Physical deletion would make that question
 * unanswerable. [org.commonlink.service.ComplianceDeclarantService.listActive] filters by
 * `revokedAt IS NULL` to return only the currently authorised set.
 *
 * ### One designation per user
 *
 * [userId] carries a `UNIQUE` constraint. A revoked person can therefore not be re-designated
 * on the same platform account — this is the spec's deliberate choice, not a technical
 * limitation.
 */
@Entity
@Table(name = "compliance_declarant")
class ComplianceDeclarant(

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    val id: UUID = UUID.randomUUID(),

    /** [org.commonlink.entity.User.id] of the designated person — must carry [UserRole.COMPLIANCE_OFFICER]. */
    @Column(name = "user_id", nullable = false, updatable = false, unique = true)
    val userId: UUID,

    /** ERMES télédéclarant number assigned after registration on the portal. Encrypted at rest. */
    @Convert(converter = ComplianceCryptoConverter::class)
    @Column(name = "teledeclarant_number", nullable = false, columnDefinition = "TEXT")
    val teledeclarantNumber: String,

    /** Legal full name of the declarant as it appears in the designation document. Encrypted at rest. */
    @Convert(converter = ComplianceCryptoConverter::class)
    @Column(name = "full_name", nullable = false, columnDefinition = "TEXT")
    val fullName: String,

    /** Date of the signed designation document. */
    @Column(name = "designated_at", nullable = false, updatable = false)
    val designatedAt: LocalDate,

    /** Date of revocation, if any. Null means this designation is currently active. */
    @Column(name = "revoked_at")
    var revokedAt: LocalDate? = null,

    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: Instant = Instant.now(),
)
