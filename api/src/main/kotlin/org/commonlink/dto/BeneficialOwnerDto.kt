package org.commonlink.dto

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Size
import org.commonlink.entity.BeneficialOwnerOrigin
import org.commonlink.entity.BeneficialOwnerType
import java.time.Instant
import java.util.UUID

/** Réponse API représentant un bénéficiaire effectif ou représentant légal (nom déchiffré par le serveur). */
data class BeneficialOwnerDto(
    val id: UUID,
    val name: String,
    val role: String?,
    val dateOfBirth: String?,
    val origin: BeneficialOwnerOrigin,
    val type: BeneficialOwnerType,
    val collectedAt: Instant,
    val confirmedBy: UUID,
    val discarded: Boolean,
    val discardedBy: UUID?,
    val discardedAt: Instant?,
)

/** Corps de requête pour l'ajout d'un bénéficiaire effectif ou représentant légal par le curateur. */
data class AddBeneficialOwnerRequest(
    @field:NotBlank
    @field:Size(max = 200)
    val name: String,

    @field:Size(max = 200)
    val role: String? = null,

    @field:Size(max = 100)
    val dateOfBirth: String? = null,

    @field:NotNull
    val origin: BeneficialOwnerOrigin,

    @field:NotNull
    val type: BeneficialOwnerType = BeneficialOwnerType.BENEFICIAL_OWNER,
)
