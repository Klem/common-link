package org.commonlink.entity

import jakarta.persistence.Column
import jakarta.persistence.Convert
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

/**
 * Un bénéficiaire effectif retenu pour une association (dispositif LCB-FT).
 *
 * Conformément à la règle réglementaire appliquée, les bénéficiaires effectifs d'une association
 * sont les membres de l'organe d'administration, de direction et de surveillance ; à défaut
 * d'organe identifiable, les représentants légaux.
 *
 * Chaque ligne est le résultat d'une décision humaine d'un curateur ([confirmedBy]) :
 * aucun bénéficiaire n'est inséré automatiquement. [collectedAt] horodate cette confirmation.
 *
 * [name] et [dateOfBirth] sont chiffrés en base (AES-256-GCM via [ComplianceCryptoConverter]) —
 * données personnelles conservées pour obligation légale (LCB-FT).
 *
 * Un bénéficiaire écarté ([discarded] = true) n'est jamais supprimé : la ligne subsiste pour
 * l'audit trail. Seuls les bénéficiaires non écartés comptent pour le contrôle d'approbation KYC.
 */
@Entity
@Table(name = "beneficial_owner")
class BeneficialOwner(

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    val id: UUID? = null,

    @Column(name = "association_id", nullable = false, updatable = false)
    val associationId: UUID,

    /** Nom chiffré en base. */
    @Convert(converter = ComplianceCryptoConverter::class)
    @Column(name = "name", nullable = false, columnDefinition = "TEXT")
    val name: String,

    @Column(name = "role", length = 200)
    val role: String? = null,

    /** Date de naissance chiffrée en base, nullable si non renseignée. */
    @Convert(converter = ComplianceCryptoConverter::class)
    @Column(name = "date_of_birth", columnDefinition = "TEXT")
    val dateOfBirth: String? = null,

    @Enumerated(EnumType.STRING)
    @Column(name = "origin", nullable = false, length = 20)
    val origin: BeneficialOwnerOrigin,

    /** Catégorie de la personne : bénéficiaire effectif strict ou représentant légal (art. R.561-3 CMF). */
    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 20)
    val type: BeneficialOwnerType = BeneficialOwnerType.BENEFICIAL_OWNER,

    /** Horodatage de la confirmation par le curateur — identique à la date de collecte réglementaire. */
    @Column(name = "collected_at", nullable = false, updatable = false)
    val collectedAt: Instant,

    /** UUID du curateur ([User.id]) ayant retenu ce bénéficiaire. */
    @Column(name = "confirmed_by", updatable = false)
    val confirmedBy: UUID,

    @Column(name = "discarded", nullable = false)
    var discarded: Boolean = false,

    /** UUID du curateur ayant écarté ce bénéficiaire. */
    @Column(name = "discarded_by")
    var discardedBy: UUID? = null,

    @Column(name = "discarded_at")
    var discardedAt: Instant? = null,
)
