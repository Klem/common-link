package org.commonlink.service

import org.commonlink.dto.OnboardingProvidedDocumentDto
import org.commonlink.entity.AssociationDocumentType
import org.commonlink.exception.NotFoundException
import org.commonlink.repository.AssociationDocumentRepository
import org.commonlink.repository.AssociationProfileRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

/**
 * Lists the KYB documents an association already provided to CommonLink, so it can re-download them while completing
 * its payment provider's own verification (Mollie onboarding kit, HelloAsso "Retrouver mes fichiers").
 *
 * Provider-neutral: projects type and id only — never a file name nor content. The download itself goes through
 * `GET /api/association/documents/{id}/content` (ownership-checked and journaled).
 */
@Service
class KybDocumentService(
    private val associationProfileRepository: AssociationProfileRepository,
    private val documentRepository: AssociationDocumentRepository,
) {

    companion object {
        /** KYB document slots surfaced to the association, in display order. */
        val PROVIDED_DOCUMENT_TYPES = listOf(
            AssociationDocumentType.VERIF_STATUTS,
            AssociationDocumentType.VERIF_RNA_RECEIPT,
            AssociationDocumentType.VERIF_REPRESENTATIVE_ID,
        )
    }

    /**
     * KYB slots in [PROVIDED_DOCUMENT_TYPES] that hold a file for [associationId], in display order.
     *
     * @param associationId association profile id.
     */
    @Transactional(readOnly = true)
    fun listProvided(associationId: UUID): List<OnboardingProvidedDocumentDto> {
        val documentsByType = documentRepository.findMetadataByAssociationId(associationId)
            .filter { it.docType in PROVIDED_DOCUMENT_TYPES && it.id != null }
            .associateBy { it.docType }
        return PROVIDED_DOCUMENT_TYPES.mapNotNull { type ->
            documentsByType[type]?.let { OnboardingProvidedDocumentDto(id = it.id!!, type = type) }
        }
    }

    /**
     * Same as [listProvided], for the association owned by [userId].
     *
     * @param userId authenticated user's id (`principal.username`).
     * @throws NotFoundException when the user has no association profile.
     */
    @Transactional(readOnly = true)
    fun listProvidedForUser(userId: UUID): List<OnboardingProvidedDocumentDto> {
        val profile = associationProfileRepository.findByUserId(userId)
            .orElseThrow { NotFoundException("Association not found for user: $userId") }
        return listProvided(profile.id!!)
    }
}
