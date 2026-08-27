package org.commonlink.service

import org.commonlink.dto.LegalAcceptanceDto
import org.commonlink.dto.LegalAcceptanceStateDto
import org.commonlink.dto.LegalDocumentDto
import org.commonlink.entity.LegalAcceptance
import org.commonlink.entity.LegalAcceptanceSubjectType
import org.commonlink.entity.LegalDocument
import org.commonlink.entity.LegalDocumentType
import org.commonlink.exception.NotFoundException
import org.commonlink.exception.UnprocessableEntityException
import org.commonlink.exception.UserNotFoundException
import org.commonlink.repository.AssociationProfileRepository
import org.commonlink.repository.LegalAcceptanceRepository
import org.commonlink.repository.LegalDocumentRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

/**
 * Proof-of-acceptance engine for CGU/CGV (notice ACPR ; art. 1740 A CGI).
 *
 * Two distinct write shapes over the same [LegalAcceptance] table — see that entity's KDoc for why:
 * association rows are deduplicated per (association, document, version); donor rows are written
 * fresh on every donation.
 */
@Service
class LegalAcceptanceService(
    private val legalDocumentRepository: LegalDocumentRepository,
    private val legalAcceptanceRepository: LegalAcceptanceRepository,
    private val associationProfileRepository: AssociationProfileRepository,
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    /** The current published version of [documentType]. Throws if none was ever seeded. */
    @Transactional(readOnly = true)
    fun currentDocument(documentType: LegalDocumentType): LegalDocument =
        legalDocumentRepository.findTopByDocumentTypeOrderByPublishedAtDesc(documentType)
            ?: throw NotFoundException("No published $documentType document")

    fun currentDocumentDto(documentType: LegalDocumentType): LegalDocumentDto =
        currentDocument(documentType).toDto()

    /** Whether [associationId] already has a standing acceptance of the current [documentType] version. */
    @Transactional(readOnly = true)
    fun associationAcceptanceState(associationId: UUID, documentType: LegalDocumentType): LegalAcceptanceStateDto {
        val current = currentDocument(documentType)
        val accepted = legalAcceptanceRepository.existsBySubjectTypeAndSubjectIdAndDocumentTypeAndDocumentVersion(
            LegalAcceptanceSubjectType.ASSOCIATION, associationId, documentType, current.version,
        )
        return LegalAcceptanceStateDto(documentType, current.version, accepted)
    }

    /** Same as [associationAcceptanceState], resolved from a `User` id — used by the controller. */
    @Transactional(readOnly = true)
    fun associationAcceptanceStateForUser(userId: UUID, documentType: LegalDocumentType): LegalAcceptanceStateDto {
        val profile = associationProfileRepository.findByUserId(userId)
            .orElseThrow { UserNotFoundException("Association profile not found for user $userId") }
        return associationAcceptanceState(profile.id!!, documentType)
    }

    /**
     * Publish-time gate. No-op (writes nothing) if [associationId] already accepted the current
     * version — this is what makes the frontend's "auto-checked and greyed out for later
     * campaigns" behaviour safe rather than a stale rubber stamp: a version bump makes `already`
     * false again and the gate re-engages.
     *
     * @throws UnprocessableEntityException if not yet accepted and [accepted] is false.
     */
    @Transactional
    fun requireAssociationAcceptance(
        associationId: UUID,
        documentType: LegalDocumentType,
        accepted: Boolean,
        signerName: String?,
        signerEmail: String?,
        campaignId: UUID,
    ) {
        val current = currentDocument(documentType)
        val already = legalAcceptanceRepository.existsBySubjectTypeAndSubjectIdAndDocumentTypeAndDocumentVersion(
            LegalAcceptanceSubjectType.ASSOCIATION, associationId, documentType, current.version,
        )
        if (already) return
        if (!accepted) {
            throw UnprocessableEntityException(
                "You must accept the current $documentType (version ${current.version}) before publishing"
            )
        }
        legalAcceptanceRepository.save(
            LegalAcceptance(
                subjectType = LegalAcceptanceSubjectType.ASSOCIATION,
                subjectId = associationId,
                documentType = documentType,
                documentVersion = current.version,
                signerName = signerName,
                signerEmail = signerEmail,
                campaignId = campaignId,
            )
        )
        logger.info(
            "Legal acceptance recorded: subjectType=ASSOCIATION subjectId={} documentType={} version={}",
            associationId, documentType, current.version,
        )
    }

    /**
     * Records donor acceptance of every [LegalDocumentType] for one donation — a donation is a
     * transactional act, so acceptance is captured fresh every time rather than reused across
     * future donations. Idempotent per (donationId, documentType): a retried request writes
     * nothing twice.
     */
    @Transactional
    fun recordDonorAcceptance(
        donorProfileId: UUID,
        donationId: UUID,
        campaignId: UUID,
        signerName: String?,
        signerEmail: String?,
    ) {
        for (documentType in LegalDocumentType.entries) {
            if (legalAcceptanceRepository.existsByDonationIdAndDocumentType(donationId, documentType)) continue
            val current = currentDocument(documentType)
            legalAcceptanceRepository.save(
                LegalAcceptance(
                    subjectType = LegalAcceptanceSubjectType.DONOR,
                    subjectId = donorProfileId,
                    documentType = documentType,
                    documentVersion = current.version,
                    signerName = signerName,
                    signerEmail = signerEmail,
                    donationId = donationId,
                    campaignId = campaignId,
                )
            )
        }
        logger.info("Legal acceptance recorded: subjectType=DONOR subjectId={} donationId={}", donorProfileId, donationId)
    }

    /** Full acceptance history for one account — restitution of proof for compliance review. */
    @Transactional(readOnly = true)
    fun listAcceptances(subjectType: LegalAcceptanceSubjectType, subjectId: UUID): List<LegalAcceptanceDto> =
        legalAcceptanceRepository.findAllBySubjectTypeAndSubjectIdOrderByAcceptedAtDesc(subjectType, subjectId)
            .map { it.toDto() }

    private fun LegalDocument.toDto() = LegalDocumentDto(
        documentType = documentType,
        version = version,
        content = content,
        publishedAt = publishedAt,
    )

    private fun LegalAcceptance.toDto() = LegalAcceptanceDto(
        id = id,
        subjectType = subjectType,
        subjectId = subjectId,
        documentType = documentType,
        documentVersion = documentVersion,
        acceptedAt = acceptedAt,
        signerName = signerName,
        signerEmail = signerEmail,
        donationId = donationId,
        campaignId = campaignId,
    )
}
