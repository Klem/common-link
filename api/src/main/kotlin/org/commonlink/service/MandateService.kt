package org.commonlink.service

import org.commonlink.dto.MandateDocumentSlotDto
import org.commonlink.dto.MandateStateDto
import org.commonlink.dto.SignMandateRequest
import org.commonlink.entity.AssociationDocument
import org.commonlink.entity.AssociationDocumentType
import org.commonlink.entity.AssociationDocumentType.MANDATE_RESCRIT
import org.commonlink.entity.AssociationDocumentType.MANDATE_STATUTS
import org.commonlink.entity.AssociationProfile
import org.commonlink.entity.FiscalMandate
import org.commonlink.entity.VerificationStatus
import org.commonlink.exception.ConflictException
import org.commonlink.exception.NotFoundException
import org.commonlink.exception.UnprocessableEntityException
import org.commonlink.exception.UserNotFoundException
import org.commonlink.repository.AssociationDocumentRepository
import org.commonlink.repository.AssociationProfileRepository
import org.commonlink.repository.FiscalMandateRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.multipart.MultipartFile
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.UUID

private val MANDATE_DOC_TYPES = listOf(MANDATE_STATUTS, MANDATE_RESCRIT)

private val MANDATE_ALLOWED_MIME = setOf(
    "application/pdf",
    "image/jpeg",
    "image/png",
    "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
)

private const val MAX_MANDATE_FILE_SIZE = 10L * 1024 * 1024

/**
 * Business logic for the fiscal mandate lifecycle.
 *
 * State machine: mandate is absent (not signed) → SIGNED (active) → REVOKED (revokedAt set).
 * Re-signing after revocation creates a new row; history is preserved.
 * Signing is gated on [VerificationStatus.VERIFIED]. Document uploads/deletes are blocked
 * while an active (non-revoked) mandate exists.
 */
@Service
class MandateService(
    private val associationProfileRepository: AssociationProfileRepository,
    private val documentRepository: AssociationDocumentRepository,
    private val fiscalMandateRepository: FiscalMandateRepository,
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    /** Returns the full mandate state: active mandate metadata + 2 document slots + blocked flag. */
    @Transactional(readOnly = true)
    fun getMandateState(userId: UUID): MandateStateDto {
        val profile = resolveProfile(userId)
        val activeMandate = fiscalMandateRepository.findByAssociationIdAndRevokedAtIsNull(profile.id!!)
        val slots = buildSlots(profile.id!!)
        return toDto(profile, activeMandate, slots)
    }

    /**
     * Uploads or replaces a mandate document (MANDATE_STATUTS or MANDATE_RESCRIT).
     * Blocked when an active mandate exists.
     */
    @Transactional
    fun uploadMandateDocument(userId: UUID, docType: AssociationDocumentType, file: MultipartFile) {
        val profile = resolveProfile(userId)
        requireMandateDocType(docType)
        validateFile(file)
        requireNoActiveMandate(profile.id!!, "Cannot replace mandate documents while an active mandate is signed")

        documentRepository.deleteByAssociationIdAndDocType(profile.id!!, docType)
        documentRepository.save(
            AssociationDocument(
                association = profile,
                docType = docType,
                category = null,
                fileName = file.originalFilename ?: "document",
                contentType = file.contentType ?: "application/octet-stream",
                sizeBytes = file.size,
                content = file.bytes,
                uploadedAt = Instant.now(),
            )
        )
        logger.info("Mandate document uploaded: type={} association={}", docType, profile.id)
    }

    /**
     * Deletes a mandate document slot. Blocked when an active mandate exists.
     */
    @Transactional
    fun deleteMandateDocument(userId: UUID, docType: AssociationDocumentType) {
        val profile = resolveProfile(userId)
        requireMandateDocType(docType)
        requireNoActiveMandate(profile.id!!, "Cannot delete mandate documents while an active mandate is signed")

        if (!documentRepository.existsByAssociationIdAndDocType(profile.id!!, docType)) {
            throw NotFoundException("Document $docType not found")
        }
        documentRepository.deleteByAssociationIdAndDocType(profile.id!!, docType)
        logger.info("Mandate document deleted: type={} association={}", docType, profile.id)
    }

    /**
     * Signs a new fiscal mandate.
     *
     * Guards (in order): accepted == true, verificationStatus == VERIFIED, no active mandate exists,
     * both mandate documents are present.
     */
    @Transactional
    fun signMandate(userId: UUID, request: SignMandateRequest): MandateStateDto {
        val profile = resolveProfile(userId)

        if (!request.accepted) {
            throw UnprocessableEntityException("You must accept the mandate terms")
        }
        if (profile.verificationStatus != VerificationStatus.VERIFIED) {
            throw ConflictException("Association must be VERIFIED before signing a mandate")
        }
        if (fiscalMandateRepository.findByAssociationIdAndRevokedAtIsNull(profile.id!!) != null) {
            throw ConflictException("An active mandate already exists; revoke it before re-signing")
        }
        val allDocsPresent = MANDATE_DOC_TYPES.all {
            documentRepository.existsByAssociationIdAndDocType(profile.id!!, it)
        }
        if (!allDocsPresent) {
            throw ConflictException("Both mandate documents (statuts + rescrit) must be uploaded before signing")
        }

        val seq = fiscalMandateRepository.nextSequenceValue()
        val year = ZonedDateTime.now(ZoneId.of("Europe/Paris")).year
        val reference = "MND-$year-${seq.toString().padStart(4, '0')}"

        val mandate = FiscalMandate(
            association = profile,
            eligibility = request.eligibility!!,
            reference = reference,
            signedAt = Instant.now(),
        )
        fiscalMandateRepository.save(mandate)
        logger.info("Fiscal mandate signed: reference={} association={}", reference, profile.id)

        return getMandateState(userId)
    }

    /** Revokes the active mandate by setting [FiscalMandate.revokedAt]. */
    @Transactional
    fun revokeMandate(userId: UUID) {
        val profile = resolveProfile(userId)
        val mandate = fiscalMandateRepository.findByAssociationIdAndRevokedAtIsNull(profile.id!!)
            ?: throw NotFoundException("No active mandate found for this association")

        mandate.revokedAt = Instant.now()
        fiscalMandateRepository.save(mandate)
        logger.info("Fiscal mandate revoked: reference={} association={}", mandate.reference, profile.id)
    }

    /**
     * Returns the active mandate and profile for PDF generation.
     * @throws NotFoundException if no active mandate exists.
     */
    @Transactional(readOnly = true)
    fun getMandatePdf(userId: UUID): Pair<FiscalMandate, AssociationProfile> {
        val profile = resolveProfile(userId)
        val mandate = fiscalMandateRepository.findByAssociationIdAndRevokedAtIsNull(profile.id!!)
            ?: throw NotFoundException("No active mandate found")
        return Pair(mandate, profile)
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private fun resolveProfile(userId: UUID): AssociationProfile =
        associationProfileRepository.findByUserId(userId)
            .orElseThrow { UserNotFoundException("Association profile not found for user $userId") }

    private fun requireMandateDocType(docType: AssociationDocumentType) {
        if (docType !in MANDATE_DOC_TYPES) {
            throw UnprocessableEntityException(
                "Document type $docType is not a mandate document. Expected: ${MANDATE_DOC_TYPES.joinToString()}"
            )
        }
    }

    private fun requireNoActiveMandate(associationId: UUID, message: String) {
        if (fiscalMandateRepository.findByAssociationIdAndRevokedAtIsNull(associationId) != null) {
            throw ConflictException(message)
        }
    }

    private fun validateFile(file: MultipartFile) {
        if (file.size > MAX_MANDATE_FILE_SIZE) {
            throw UnprocessableEntityException("File exceeds the maximum allowed size of 10 MB")
        }
        val mime = file.contentType ?: ""
        if (mime !in MANDATE_ALLOWED_MIME) {
            throw UnprocessableEntityException(
                "File type '$mime' is not accepted. Allowed: PDF, JPEG, PNG, DOCX"
            )
        }
    }

    private fun buildSlots(associationId: UUID): List<MandateDocumentSlotDto> =
        MANDATE_DOC_TYPES.map { docType ->
            val meta = documentRepository.findMetadataByAssociationIdAndDocType(associationId, docType)
            MandateDocumentSlotDto(
                docType = docType,
                uploaded = meta != null,
                id = meta?.id,
                fileName = meta?.fileName,
                sizeBytes = meta?.sizeBytes,
                uploadedAt = meta?.uploadedAt,
            )
        }

    private fun toDto(
        profile: AssociationProfile,
        mandate: FiscalMandate?,
        slots: List<MandateDocumentSlotDto>,
    ): MandateStateDto = MandateStateDto(
        signed = mandate != null,
        reference = mandate?.reference,
        signedAt = mandate?.signedAt,
        eligibility = mandate?.eligibility,
        revokedAt = mandate?.revokedAt,
        mandateDocs = slots,
        blocked = profile.verificationStatus != VerificationStatus.VERIFIED,
    )
}
