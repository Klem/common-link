package org.commonlink.service

import org.commonlink.dto.AdminVerificationDetailDto
import org.commonlink.dto.AdminVerificationSummaryDto
import org.commonlink.dto.DocumentSlotDto
import org.commonlink.dto.OptionalDocumentDto
import org.commonlink.dto.VerificationStateDto
import org.commonlink.entity.AssociationDocument
import org.commonlink.entity.AssociationDocumentType
import org.commonlink.entity.ScopeVerdict
import org.commonlink.entity.AssociationDocumentType.OPTIONAL
import org.commonlink.entity.AssociationDocumentType.VERIF_RNA_RECEIPT
import org.commonlink.entity.AssociationDocumentType.VERIF_REPRESENTATIVE_ID
import org.commonlink.entity.AssociationDocumentType.VERIF_STATUTS
import org.commonlink.entity.VerificationStatus
import org.commonlink.entity.UserRole
import org.commonlink.exception.ConflictException
import org.commonlink.exception.NotFoundException
import org.commonlink.exception.UnprocessableEntityException
import org.commonlink.exception.UserNotFoundException
import org.commonlink.repository.AssociationDocumentRepository
import org.commonlink.repository.AssociationProfileRepository
import org.commonlink.repository.AssociationRegistryCheckRepository
import org.commonlink.repository.BeneficialOwnerRepository
import org.commonlink.repository.UserRepository
import org.slf4j.LoggerFactory
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.multipart.MultipartFile
import java.time.Instant
import java.util.UUID

private val VERIF_DOC_TYPES = listOf(VERIF_STATUTS, VERIF_RNA_RECEIPT, VERIF_REPRESENTATIVE_ID)

private val VERIF_ALLOWED_MIME = setOf(
    "application/pdf",
    "image/jpeg",
    "image/png",
    "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
)

private val OPTIONAL_ALLOWED_MIME = VERIF_ALLOWED_MIME + setOf(
    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
)

private val VALID_CATEGORIES = setOf("FINANCIAL", "REPORT", "SUPPORTING_DOC", "OTHER")

private const val MAX_FILE_SIZE = 10L * 1024 * 1024 // 10 MB

/**
 * Business logic for association KYC verification and document management.
 *
 * Verification state machine: UNVERIFIED → PENDING (on submit) → VERIFIED | REJECTED.
 * After REJECTED the association can replace documents and resubmit (REJECTED → PENDING).
 *
 * Document upload/delete is blocked while status is PENDING or VERIFIED.
 * Submission requires all 3 required document slots to be filled.
 */
@Service
class VerificationService(
    private val associationProfileRepository: AssociationProfileRepository,
    private val documentRepository: AssociationDocumentRepository,
    private val registryCheckRepository: AssociationRegistryCheckRepository,
    private val emailService: EmailService,
    private val userRepository: UserRepository,
    private val complianceAuditLogService: ComplianceAuditLogService,
    private val beneficialOwnerRepository: BeneficialOwnerRepository,
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    /**
     * Returns the full verification state for the association, including per-slot metadata for
     * the 3 required documents. Content bytes are never loaded.
     */
    fun getVerificationState(userId: UUID): VerificationStateDto {
        val profile = associationProfileRepository.findByUserId(userId)
            .orElseThrow { UserNotFoundException("Association profile not found for user $userId") }

        val slotMap = VERIF_DOC_TYPES.associateWith { docType ->
            documentRepository.findMetadataByAssociationIdAndDocType(profile.id!!, docType)
        }

        val slots = VERIF_DOC_TYPES.map { docType ->
            val meta = slotMap[docType]
            DocumentSlotDto(
                docType = docType,
                uploaded = meta != null,
                id = meta?.id,
                fileName = meta?.fileName,
                sizeBytes = meta?.sizeBytes,
                uploadedAt = meta?.uploadedAt,
            )
        }

        return VerificationStateDto(
            status = profile.verificationStatus,
            rejectionReason = profile.verificationRejectionReason,
            submittedAt = profile.verificationSubmittedAt,
            verifiedAt = profile.verifiedAt,
            requiredDocuments = slots,
        )
    }

    /**
     * Uploads or replaces a required verification document.
     * Blocked when status is PENDING or VERIFIED.
     */
    @Transactional
    fun uploadVerificationDocument(userId: UUID, docType: AssociationDocumentType, file: MultipartFile) {
        require(docType in VERIF_DOC_TYPES) {
            "docType $docType is not a verification document type"
        }
        val profile = associationProfileRepository.findByUserId(userId)
            .orElseThrow { UserNotFoundException("Association profile not found for user $userId") }

        guardMutableState(profile.verificationStatus)
        validateFile(file, VERIF_ALLOWED_MIME)

        // Replace: delete existing slot before inserting new one
        if (documentRepository.existsByAssociationIdAndDocType(profile.id!!, docType)) {
            documentRepository.deleteByAssociationIdAndDocType(profile.id!!, docType)
        }

        documentRepository.save(
            AssociationDocument(
                association = profile,
                docType = docType,
                fileName = file.originalFilename ?: file.name,
                contentType = file.contentType ?: "application/octet-stream",
                sizeBytes = file.size,
                content = file.bytes,
                uploadedAt = Instant.now(),
            )
        )
        logger.info("Verification document {} uploaded for association {}", docType, profile.id)
    }

    /**
     * Deletes a required verification document slot.
     * Blocked when status is PENDING or VERIFIED.
     */
    @Transactional
    fun deleteVerificationDocument(userId: UUID, docType: AssociationDocumentType) {
        require(docType in VERIF_DOC_TYPES) {
            "docType $docType is not a verification document type"
        }
        val profile = associationProfileRepository.findByUserId(userId)
            .orElseThrow { UserNotFoundException("Association profile not found for user $userId") }

        guardMutableState(profile.verificationStatus)

        if (!documentRepository.existsByAssociationIdAndDocType(profile.id!!, docType)) {
            throw NotFoundException("Document $docType not found for this association")
        }

        documentRepository.deleteByAssociationIdAndDocType(profile.id!!, docType)
        logger.info("Verification document {} deleted for association {}", docType, profile.id)
    }

    /**
     * Submits the verification dossier for admin review.
     * Requires all 3 documents to be uploaded.
     * Transitions UNVERIFIED | REJECTED → PENDING.
     */
    @Transactional
    fun submitVerification(userId: UUID) {
        val profile = associationProfileRepository.findByUserId(userId)
            .orElseThrow { UserNotFoundException("Association profile not found for user $userId") }

        when (profile.verificationStatus) {
            VerificationStatus.PENDING -> throw ConflictException("Verification is already pending")
            VerificationStatus.VERIFIED -> throw ConflictException("Association is already verified")
            else -> Unit
        }

        val uploadedCount = VERIF_DOC_TYPES.count { docType ->
            documentRepository.existsByAssociationIdAndDocType(profile.id!!, docType)
        }
        if (uploadedCount < VERIF_DOC_TYPES.size) {
            throw ConflictException("All 3 required documents must be uploaded before submission ($uploadedCount/3 present)")
        }

        profile.verificationStatus = VerificationStatus.PENDING
        profile.verificationRejectionReason = null
        profile.verificationSubmittedAt = Instant.now()
        associationProfileRepository.save(profile)

        val curators = userRepository.findAllByRole(UserRole.CURATOR)
        if (curators.isEmpty()) {
            logger.warn("No CURATOR users found — skipping submission notification for association {}", profile.id)
        }
        curators.forEach { curator ->
            try {
                emailService.sendVerificationSubmittedToAdmin(profile.name, curator.email)
            } catch (e: Exception) {
                logger.error("Failed to notify CURATOR {} of verification submission for association {}", curator.email, profile.id, e)
            }
        }
        logger.info("Verification submitted for association {} — status → PENDING", profile.id)
    }

    // -------------------------------------------------------------------------
    // Optional documents
    // -------------------------------------------------------------------------

    /**
     * Lists all supplementary (OPTIONAL) documents for an association, newest first.
     * Content bytes are never loaded.
     */
    fun listOptionalDocuments(userId: UUID): List<OptionalDocumentDto> {
        val profile = associationProfileRepository.findByUserId(userId)
            .orElseThrow { UserNotFoundException("Association profile not found for user $userId") }

        return documentRepository.findAllMetadataByAssociationIdAndDocType(profile.id!!, OPTIONAL)
            .map { it.toOptionalDto() }
    }

    /**
     * Uploads a supplementary document (any category).
     * Accepts PDF, JPEG, PNG, DOCX, and XLSX.
     */
    @Transactional
    fun uploadOptionalDocument(userId: UUID, file: MultipartFile, category: String): OptionalDocumentDto {
        if (category !in VALID_CATEGORIES) {
            throw UnprocessableEntityException("Category must be one of: ${VALID_CATEGORIES.joinToString()}")
        }
        val profile = associationProfileRepository.findByUserId(userId)
            .orElseThrow { UserNotFoundException("Association profile not found for user $userId") }

        validateFile(file, OPTIONAL_ALLOWED_MIME)

        val saved = documentRepository.save(
            AssociationDocument(
                association = profile,
                docType = OPTIONAL,
                category = category,
                fileName = file.originalFilename ?: file.name,
                contentType = file.contentType ?: "application/octet-stream",
                sizeBytes = file.size,
                content = file.bytes,
                uploadedAt = Instant.now(),
            )
        )
        logger.info("Optional document uploaded for association {} (category={})", profile.id, category)
        return documentRepository.findMetadataByIdAndAssociationId(saved.id!!, profile.id!!)!!.toOptionalDto()
    }

    /**
     * Deletes a supplementary document. Verifies ownership before deletion.
     */
    @Transactional
    fun deleteOptionalDocument(userId: UUID, docId: UUID) {
        val profile = associationProfileRepository.findByUserId(userId)
            .orElseThrow { UserNotFoundException("Association profile not found for user $userId") }

        documentRepository.findMetadataByIdAndAssociationId(docId, profile.id!!)
            ?: throw NotFoundException("Document $docId not found")

        documentRepository.deleteById(docId)
        logger.info("Optional document {} deleted for association {}", docId, profile.id)
    }

    /**
     * Downloads the binary content of a document (any type).
     * Returns a pair of (metadata, content bytes).
     * Verifies ownership — throws NotFoundException if doc doesn't belong to this association.
     */
    fun downloadDocument(userId: UUID, docId: UUID): Pair<org.commonlink.repository.AssociationDocumentMetadata, ByteArray> {
        val profile = associationProfileRepository.findByUserId(userId)
            .orElseThrow { UserNotFoundException("Association profile not found for user $userId") }

        val meta = documentRepository.findMetadataByIdAndAssociationId(docId, profile.id!!)
            ?: throw NotFoundException("Document $docId not found")

        val content = documentRepository.findContentById(docId)
            ?: throw NotFoundException("Document $docId content not found")

        return meta to content
    }

    // -------------------------------------------------------------------------
    // Admin operations
    // -------------------------------------------------------------------------

    /**
     * Lists verification dossiers filtered by status, paginated. Never loads document content.
     */
    fun adminListVerifications(status: VerificationStatus, pageable: Pageable): Page<AdminVerificationSummaryDto> =
        associationProfileRepository.findByVerificationStatus(status, pageable).map { profile ->
            AdminVerificationSummaryDto(
                associationId = profile.id!!,
                name = profile.name,
                identifier = profile.identifier,
                status = profile.verificationStatus,
                submittedAt = profile.verificationSubmittedAt,
                docCount = documentRepository.countByAssociationIdAndDocTypeIn(profile.id!!, VERIF_DOC_TYPES).toInt(),
            )
        }

    /**
     * Returns the full dossier detail for one association: state, all document slots, and optional docs.
     * Never loads document content.
     */
    fun adminGetDetail(associationId: UUID): AdminVerificationDetailDto {
        val profile = associationProfileRepository.findById(associationId)
            .orElseThrow { NotFoundException("Association $associationId not found") }

        val slots = VERIF_DOC_TYPES.map { docType ->
            val meta = documentRepository.findMetadataByAssociationIdAndDocType(profile.id!!, docType)
            DocumentSlotDto(
                docType = docType,
                uploaded = meta != null,
                id = meta?.id,
                fileName = meta?.fileName,
                sizeBytes = meta?.sizeBytes,
                uploadedAt = meta?.uploadedAt,
            )
        }

        val optionalDocs = documentRepository.findAllMetadataByAssociationIdAndDocType(profile.id!!, OPTIONAL)
            .map { it.toOptionalDto() }

        return AdminVerificationDetailDto(
            associationId = profile.id!!,
            name = profile.name,
            identifier = profile.identifier,
            status = profile.verificationStatus,
            rejectionReason = profile.verificationRejectionReason,
            submittedAt = profile.verificationSubmittedAt,
            verifiedAt = profile.verifiedAt,
            docCount = slots.count { it.uploaded },
            requiredDocuments = slots,
            optionalDocuments = optionalDocs,
        )
    }

    /**
     * Downloads a document on behalf of an admin, verifying that it belongs to the given association.
     * Returns metadata and raw content bytes.
     */
    fun adminDownloadDocument(associationId: UUID, docId: UUID): Pair<org.commonlink.repository.AssociationDocumentMetadata, ByteArray> {
        if (!associationProfileRepository.existsById(associationId)) {
            throw NotFoundException("Association $associationId not found")
        }
        val meta = documentRepository.findMetadataByIdAndAssociationId(docId, associationId)
            ?: throw NotFoundException("Document $docId not found for association $associationId")
        val content = documentRepository.findContentById(docId)
            ?: throw NotFoundException("Document $docId content not found")
        return meta to content
    }

    /**
     * Approves a PENDING dossier → VERIFIED, recording the timestamp.
     * Throws [ConflictException] if the dossier is not in PENDING state.
     */
    @Transactional
    fun adminApprove(associationId: UUID) {
        val profile = associationProfileRepository.findById(associationId)
            .orElseThrow { NotFoundException("Association $associationId not found") }
        if (profile.verificationStatus != VerificationStatus.PENDING) {
            throw ConflictException("Cannot approve: status is ${profile.verificationStatus}, expected PENDING")
        }

        // Scope check: block approval when the latest scan shows a non-9220 legal category.
        // UNDETERMINED (null category / no scan) does not block — a registry outage must not
        // disqualify an association. Log is committed in its own transaction before throwing.
        val latestCheck = registryCheckRepository.findTopByAssociationIdOrderByCheckedAtDesc(associationId)
        if (latestCheck?.scopeVerdict == ScopeVerdict.OUT_OF_SCOPE) {
            complianceAuditLogService.appendOutOfScopeRefusal(associationId, latestCheck.legalCategory!!)
            throw ConflictException(
                "Cannot approve: association legal category '${latestCheck.legalCategory}' is outside platform scope" +
                    " — only loi 1901 associations (category 9220) are accepted"
            )
        }

        // UBO check: block approval when no retained beneficial owner exists.
        // An association with no confirmed beneficial owner cannot enter into a business relationship (LCB-FT).
        // REQUIRES_NEW propagation ensures the log entry is committed even when this method throws.
        if (!beneficialOwnerRepository.existsByAssociationIdAndDiscardedFalse(associationId)) {
            complianceAuditLogService.appendNoUboRefusal(associationId)
            throw ConflictException(
                "Cannot approve: no beneficial owner has been confirmed for this association"
            )
        }

        profile.verificationStatus = VerificationStatus.VERIFIED
        profile.verifiedAt = Instant.now()
        // Freeze the registry pre-check that informed this decision (LCB-FT audit trail; null if never scanned)
        profile.decisionRegistryCheckId =
            registryCheckRepository.findTopByAssociationIdOrderByCheckedAtDesc(associationId)?.id
        associationProfileRepository.save(profile)
        logger.info("Verification approved for association {} — status → VERIFIED", associationId)
        // Notify association after persistence — failure must not rollback the decision
        val recipientEmail = profile.contactEmail ?: profile.user.email
        try {
            emailService.sendVerificationApprovedToAssociation(profile.name, recipientEmail)
        } catch (e: Exception) {
            logger.error("Failed to send approval email for association {}: {}", associationId, e.message)
        }
    }

    /**
     * Rejects a PENDING dossier → REJECTED, storing the admin-provided reason.
     * Throws [ConflictException] if the dossier is not in PENDING state.
     */
    @Transactional
    fun adminReject(associationId: UUID, reason: String) {
        val profile = associationProfileRepository.findById(associationId)
            .orElseThrow { NotFoundException("Association $associationId not found") }
        if (profile.verificationStatus != VerificationStatus.PENDING) {
            throw ConflictException("Cannot reject: status is ${profile.verificationStatus}, expected PENDING")
        }
        profile.verificationStatus = VerificationStatus.REJECTED
        profile.verificationRejectionReason = reason
        // Freeze the registry pre-check that informed this decision (LCB-FT audit trail; null if never scanned)
        profile.decisionRegistryCheckId =
            registryCheckRepository.findTopByAssociationIdOrderByCheckedAtDesc(associationId)?.id
        associationProfileRepository.save(profile)
        logger.info("Verification rejected for association {} — reason: {}", associationId, reason)
        // Notify association after persistence — failure must not rollback the decision
        val recipientEmail = profile.contactEmail ?: profile.user.email
        try {
            emailService.sendVerificationRejectedToAssociation(profile.name, recipientEmail, reason)
        } catch (e: Exception) {
            logger.error("Failed to send rejection email for association {}: {}", associationId, e.message)
        }
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    private fun guardMutableState(status: VerificationStatus) {
        when (status) {
            VerificationStatus.PENDING -> throw ConflictException("Cannot modify documents while verification is pending")
            VerificationStatus.VERIFIED -> throw ConflictException("Cannot modify documents after verification is complete")
            else -> Unit
        }
    }

    private fun validateFile(file: MultipartFile, allowedMime: Set<String>) {
        if (file.size > MAX_FILE_SIZE) {
            throw UnprocessableEntityException("File size exceeds the 10 MB limit (${file.size} bytes)")
        }
        val mime = file.contentType ?: "application/octet-stream"
        if (mime !in allowedMime) {
            throw UnprocessableEntityException("File type '$mime' is not allowed. Accepted: ${allowedMime.joinToString()}")
        }
    }
}

private fun org.commonlink.repository.AssociationDocumentMetadata.toOptionalDto() = OptionalDocumentDto(
    id = id!!,
    fileName = fileName,
    category = category,
    contentType = contentType,
    sizeBytes = sizeBytes,
    uploadedAt = uploadedAt,
)
