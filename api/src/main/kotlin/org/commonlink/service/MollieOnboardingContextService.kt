package org.commonlink.service

import org.commonlink.dto.MollieOnboardingContextDto
import org.commonlink.dto.MollieSelfCompletionAttestationDto
import org.commonlink.dto.OnboardingBeneficialOwnerDto
import org.commonlink.dto.OnboardingProvidedDocumentDto
import org.commonlink.dto.structureVariantOf
import org.commonlink.entity.AssociationDocumentType
import org.commonlink.entity.AssociationProfile
import org.commonlink.entity.AssociationRegistryCheck
import org.commonlink.exception.NotFoundException
import org.commonlink.repository.AssociationDocumentRepository
import org.commonlink.repository.AssociationProfileRepository
import org.commonlink.repository.AssociationRegistryCheckRepository
import org.commonlink.repository.BeneficialOwnerRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

/**
 * Read model and attestation for the "Bank account" tab's guided Mollie onboarding (sprint 20).
 *
 * Kept apart from [MollieConnectService] on purpose: this service never talks to Mollie, never
 * reads or writes [org.commonlink.entity.MollieConnection], and has no dependency on
 * [OnboardingGateService] — so nothing here can change the collection lock
 * ([org.commonlink.entity.MollieConnection.canCollectDonations]). It only projects CommonLink's
 * own KYB data back to the association that owns it, and journals the self-completion
 * attestation (decision D10: a journal fact that unlocks nothing).
 */
@Service
class MollieOnboardingContextService(
    private val associationProfileRepository: AssociationProfileRepository,
    private val registryCheckRepository: AssociationRegistryCheckRepository,
    private val beneficialOwnerRepository: BeneficialOwnerRepository,
    private val documentRepository: AssociationDocumentRepository,
    private val complianceAuditLogService: ComplianceAuditLogService,
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    companion object {
        /**
         * Version of the attestation wording "Je confirme avoir rempli moi-même mon dossier Mollie"
         * (validated mock-up of 2026-10-12). Bump it whenever the wording shown to the user changes,
         * so each journal entry stays tied to the exact text that was attested.
         */
        const val SELF_COMPLETION_ATTESTATION_TEXT_VERSION = "mollie-self-completion-v1-2026-10-12"

        /** KYB document slots surfaced in the onboarding kit, in display order. */
        val KIT_DOCUMENT_TYPES = listOf(
            AssociationDocumentType.VERIF_STATUTS,
            AssociationDocumentType.VERIF_RNA_RECEIPT,
            AssociationDocumentType.VERIF_REPRESENTATIVE_ID,
        )
    }

    /**
     * Builds the onboarding context of the authenticated user's association.
     *
     * - `structureVariant` comes from the registry scan that informed the KYB decision
     *   ([AssociationProfile.decisionRegistryCheckId]), falling back to the latest scan when no
     *   decision snapshot exists.
     * - `beneficialOwners` lists non-discarded rows only (every row is curator-confirmed by
     *   construction), with name and role — never the date of birth.
     * - `providedDocuments` lists the KYB slots in [KIT_DOCUMENT_TYPES] that hold a file.
     * - `siren` is the profile's SIREN, falling back to the registry scan's.
     *
     * @param userId authenticated user's id (`principal.username`).
     * @throws NotFoundException when the user has no association profile.
     */
    @Transactional(readOnly = true)
    fun getOnboardingContext(userId: UUID): MollieOnboardingContextDto {
        val profile = findProfile(userId)
        val associationId = profile.id!!
        val registryCheck = findKybRegistryCheck(profile)

        val beneficialOwners = beneficialOwnerRepository.findAllByAssociationIdOrderByCollectedAtAsc(associationId)
            .filterNot { it.discarded }
            .map { OnboardingBeneficialOwnerDto(name = it.name, role = it.role, type = it.type) }

        val documentsByType = documentRepository.findMetadataByAssociationId(associationId)
            .filter { it.docType in KIT_DOCUMENT_TYPES && it.id != null }
            .associateBy { it.docType }
        val providedDocuments = KIT_DOCUMENT_TYPES.mapNotNull { type ->
            documentsByType[type]?.let { OnboardingProvidedDocumentDto(id = it.id!!, type = type) }
        }

        return MollieOnboardingContextDto(
            structureVariant = structureVariantOf(registryCheck?.legalCategory),
            verifiedAt = profile.verifiedAt,
            beneficialOwners = beneficialOwners,
            providedDocuments = providedDocuments,
            siren = profile.siren ?: registryCheck?.siren,
            selfCompletionAttestedAt = complianceAuditLogService.findMollieSelfCompletionAttestation(associationId)?.occurredAt,
        )
    }

    /**
     * Records that the association attests having filled its Mollie dossier itself.
     *
     * Idempotent per association: a second call returns the first attestation and writes no new
     * journal event (see [ComplianceAuditLogService.appendMollieSelfCompletionAttestedOnce]).
     * Unlocks nothing — no Mollie call, no connection or gate change. Records neither the client
     * IP address nor the user-agent (GDPR decision of 2026-10-12).
     *
     * @param userId authenticated user's id (`principal.username`), journaled as the actor.
     * @throws NotFoundException when the user has no association profile.
     */
    fun attestSelfCompletion(userId: UUID): MollieSelfCompletionAttestationDto {
        val associationId = findProfile(userId).id!!
        val event = complianceAuditLogService.appendMollieSelfCompletionAttestedOnce(
            associationId = associationId,
            actorUserId = userId,
            textVersion = SELF_COMPLETION_ATTESTATION_TEXT_VERSION,
        )
        logger.info("Mollie self-completion attestation for association {} (event {})", associationId, event.id)
        return MollieSelfCompletionAttestationDto(attestedAt = event.occurredAt)
    }

    /** Resolves the association owned by [userId] — the ownership boundary of every call here. */
    private fun findProfile(userId: UUID): AssociationProfile =
        associationProfileRepository.findByUserId(userId)
            .orElseThrow { NotFoundException("Association not found for user: $userId") }

    /**
     * Registry scan backing the KYB data: the frozen decision snapshot when present, otherwise the
     * latest scan, or null when the association was never scanned.
     */
    private fun findKybRegistryCheck(profile: AssociationProfile): AssociationRegistryCheck? =
        profile.decisionRegistryCheckId?.let { registryCheckRepository.findById(it).orElse(null) }
            ?: registryCheckRepository.findTopByAssociationIdOrderByCheckedAtDesc(profile.id!!)
}
