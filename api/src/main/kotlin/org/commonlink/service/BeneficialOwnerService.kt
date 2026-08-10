package org.commonlink.service

import org.commonlink.dto.AddBeneficialOwnerRequest
import org.commonlink.dto.BeneficialOwnerDto
import org.commonlink.entity.BeneficialOwner
import org.commonlink.entity.ComplianceAuditSubjectType
import org.commonlink.exception.ConflictException
import org.commonlink.exception.NotFoundException
import org.commonlink.repository.AssociationProfileRepository
import org.commonlink.repository.BeneficialOwnerRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID

/**
 * Gestion des bénéficiaires effectifs dans le cadre de l'entrée en relation (LCB-FT, prompt 9).
 *
 * Chaque opération est une décision humaine d'un curateur : aucun bénéficiaire n'est inséré ou
 * écarté automatiquement. Chaque ajout et chaque écart est journalisé via
 * [ComplianceAuditLogService] pour constituer la preuve réglementaire.
 */
@Service
class BeneficialOwnerService(
    private val repository: BeneficialOwnerRepository,
    private val associationProfileRepository: AssociationProfileRepository,
    private val complianceAuditLogService: ComplianceAuditLogService,
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    /**
     * Liste tous les bénéficiaires effectifs d'une association, y compris les écartés,
     * dans l'ordre chronologique d'ajout.
     */
    fun listOwners(associationId: UUID): List<BeneficialOwnerDto> {
        if (!associationProfileRepository.existsById(associationId)) {
            throw NotFoundException("Association $associationId not found")
        }
        return repository.findAllByAssociationIdOrderByCollectedAtAsc(associationId).map { it.toDto() }
    }

    /**
     * Ajoute un bénéficiaire effectif confirmé par le curateur [curatorUserId].
     * L'ajout est journalisé (événement `UBO_CONFIRMED`).
     */
    @Transactional
    fun addOwner(associationId: UUID, request: AddBeneficialOwnerRequest, curatorUserId: UUID): BeneficialOwnerDto {
        if (!associationProfileRepository.existsById(associationId)) {
            throw NotFoundException("Association $associationId not found")
        }
        val now = Instant.now()
        val owner = repository.save(
            BeneficialOwner(
                associationId = associationId,
                name = request.name,
                role = request.role,
                dateOfBirth = request.dateOfBirth,
                origin = request.origin,
                collectedAt = now,
                confirmedBy = curatorUserId,
            )
        )
        complianceAuditLogService.append(
            eventType = "UBO_CONFIRMED",
            subjectType = ComplianceAuditSubjectType.ASSOCIATION,
            subjectId = associationId,
            actorUserId = curatorUserId,
            payload = mapOf(
                "beneficialOwnerId" to owner.id.toString(),
                "origin" to owner.origin.name,
                "role" to owner.role,
            ),
        )
        logger.info("Beneficial owner {} added for association {} by curator {}", owner.id, associationId, curatorUserId)
        return owner.toDto()
    }

    /**
     * Écarte un bénéficiaire effectif précédemment retenu.
     * L'opération est journalisée (événement `UBO_DISCARDED`).
     * Lève [ConflictException] si le bénéficiaire est déjà écarté.
     */
    @Transactional
    fun discardOwner(associationId: UUID, ownerId: UUID, curatorUserId: UUID) {
        val owner = repository.findById(ownerId)
            .orElseThrow { NotFoundException("Beneficial owner $ownerId not found") }
        if (owner.associationId != associationId) {
            throw NotFoundException("Beneficial owner $ownerId not found for association $associationId")
        }
        if (owner.discarded) {
            throw ConflictException("Beneficial owner $ownerId is already discarded")
        }
        owner.discarded = true
        owner.discardedBy = curatorUserId
        owner.discardedAt = Instant.now()
        repository.save(owner)

        complianceAuditLogService.append(
            eventType = "UBO_DISCARDED",
            subjectType = ComplianceAuditSubjectType.ASSOCIATION,
            subjectId = associationId,
            actorUserId = curatorUserId,
            payload = mapOf(
                "beneficialOwnerId" to ownerId.toString(),
                "origin" to owner.origin.name,
            ),
        )
        logger.info("Beneficial owner {} discarded for association {} by curator {}", ownerId, associationId, curatorUserId)
    }
}

private fun BeneficialOwner.toDto() = BeneficialOwnerDto(
    id = id!!,
    name = name,
    role = role,
    dateOfBirth = dateOfBirth,
    origin = origin,
    collectedAt = collectedAt,
    confirmedBy = confirmedBy,
    discarded = discarded,
    discardedBy = discardedBy,
    discardedAt = discardedAt,
)
