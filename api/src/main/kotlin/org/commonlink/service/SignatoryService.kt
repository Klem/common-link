package org.commonlink.service

import org.commonlink.dto.SignatoryDto
import org.commonlink.entity.AssociationProfile
import org.commonlink.entity.AssociationSignature
import org.commonlink.entity.ComplianceAuditSubjectType
import org.commonlink.entity.SignatureMode
import org.commonlink.exception.NotFoundException
import org.commonlink.exception.UnprocessableEntityException
import org.commonlink.exception.UserNotFoundException
import org.commonlink.repository.AssociationProfileRepository
import org.commonlink.repository.AssociationSignatureRepository
import org.commonlink.util.FileTypeSniffer
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.multipart.MultipartFile
import java.io.ByteArrayInputStream
import java.security.MessageDigest
import java.time.Clock
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.HexFormat
import java.util.UUID
import javax.imageio.ImageIO

/**
 * Authorised receipt signer of an association and the signature printed on its fiscal receipts.
 *
 * This is the **only** write path for [AssociationProfile.signerName] / [AssociationProfile.signerRole]
 * and for [AssociationSignature]: every save carries the signatory attestation ([ATTESTATION_TEXT]) and
 * is journaled in the compliance audit log ([ComplianceAuditLogService.RECEIPT_SIGNATURE_ATTESTED]) in
 * the same transaction. The profile PATCH no longer accepts the signer fields, so a replayed request
 * cannot change the identity printed on receipts without the attestation.
 *
 * The validation below mirrors the frontend signatory card (rule 8): every limit is enforced here too.
 */
@Service
class SignatoryService(
    private val associationProfileRepository: AssociationProfileRepository,
    private val signatureRepository: AssociationSignatureRepository,
    private val auditLog: ComplianceAuditLogService,
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    /** Clock for `attested_at` / `updated_at`; replaced in tests. */
    internal var clock: Clock = Clock.systemUTC()

    companion object {
        /**
         * Sentence the association user must tick before saving. Stored verbatim in the journal payload so
         * the record proves what was attested; the frontend shows the same sentence from its i18n files.
         */
        const val ATTESTATION_TEXT =
            "J'atteste être le signataire désigné ci-dessus ou disposer de son autorisation expresse " +
                "pour apposer sa signature sur les reçus fiscaux émis au nom de l'association."

        /** Maximum signer name length (column `association_profiles.signer_name`). */
        const val MAX_SIGNER_NAME_LENGTH = 255

        /** Maximum signer role length (column `association_profiles.signer_role`). */
        const val MAX_SIGNER_ROLE_LENGTH = 100

        /** 200 KB — a signature never needs more; mirrored by the frontend. */
        const val MAX_IMAGE_BYTES = 204_800

        /** Maximum image width in pixels; mirrored by the frontend. */
        const val MAX_IMAGE_WIDTH = 1200

        /** Maximum image height in pixels; mirrored by the frontend. */
        const val MAX_IMAGE_HEIGHT = 600

        /**
         * Accepted image types, checked on the bytes (never the declared Content-Type). No SVG (XSS vector,
         * not embeddable by OpenPDF), no GIF, no WebP (not embeddable by OpenPDF).
         */
        val ALLOWED_IMAGE_MIME = setOf("image/png", "image/jpeg")
    }

    /**
     * Returns the authorised signer and the state of the receipt signature.
     *
     * @param userId UUID of the authenticated association user (the JWT principal, not the profile id).
     * @return Signer identity and signature metadata; never the image bytes.
     * @throws UserNotFoundException if no association profile exists for this user.
     */
    @Transactional(readOnly = true)
    fun getSignatory(userId: UUID): SignatoryDto {
        val profile = resolveProfile(userId)
        return toDto(profile, signatureRepository.findById(profile.id!!).orElse(null))
    }

    /**
     * Returns the stored signature image, for the association's own preview.
     *
     * The bytes are read inside the transaction and returned as a plain pair, so the caller never touches
     * the lazily-mapped `image` field outside the persistence context.
     *
     * @param userId UUID of the authenticated association user.
     * @return MIME type and raw bytes of the image.
     * @throws UserNotFoundException if no association profile exists for this user.
     * @throws NotFoundException if no signature is saved.
     */
    @Transactional(readOnly = true)
    fun getSignatureImage(userId: UUID): Pair<String, ByteArray> {
        val profile = resolveProfile(userId)
        val signature = signatureRepository.findById(profile.id!!).orElse(null)
            ?: throw NotFoundException("No signature image for this association")
        return signature.contentType to signature.image
    }

    /**
     * Saves the authorised signer and the receipt signature, under the signatory attestation.
     *
     * In one transaction: updates [AssociationProfile.signerName] / [AssociationProfile.signerRole], upserts
     * the [AssociationSignature] row (`attested_at` = `updated_at` = now, truncated to microseconds) and
     * appends one [ComplianceAuditLogService.RECEIPT_SIGNATURE_ATTESTED] event. Receipts already issued are
     * not touched: their PDF is stored once and never regenerated.
     *
     * Every argument arrives as a raw multipart string so that any invalid input is answered with the same
     * 422, whatever field it concerns.
     *
     * @param userId UUID of the authenticated association user.
     * @param signerName Signer full name; trimmed, non-blank, at most [MAX_SIGNER_NAME_LENGTH] characters.
     * @param signerRole Signer role; trimmed, non-blank, at most [MAX_SIGNER_ROLE_LENGTH] characters.
     * @param mode One of [SignatureMode] names.
     * @param attested Must be exactly `"true"`: the user ticked [ATTESTATION_TEXT].
     * @param file Signature image, required whatever the [mode]. PNG or JPEG by its bytes, at most
     *   [MAX_IMAGE_BYTES] bytes, decodable, at most [MAX_IMAGE_WIDTH] x [MAX_IMAGE_HEIGHT] pixels.
     * @return The saved signer and signature state.
     * @throws UserNotFoundException if no association profile exists for this user.
     * @throws UnprocessableEntityException if any field is missing or invalid, or if the attestation is not given.
     */
    @Transactional
    fun saveSignatory(
        userId: UUID,
        signerName: String?,
        signerRole: String?,
        mode: String?,
        attested: String?,
        file: MultipartFile?,
    ): SignatoryDto {
        val profile = resolveProfile(userId)

        val name = requireText(signerName, MAX_SIGNER_NAME_LENGTH, "Signer name")
        val role = requireText(signerRole, MAX_SIGNER_ROLE_LENGTH, "Signer role")
        val signatureMode = SignatureMode.entries.firstOrNull { it.name == mode?.trim() }
            ?: throw UnprocessableEntityException(
                "Signature mode must be one of ${SignatureMode.entries.joinToString(", ")}"
            )
        if (attested?.trim() != "true") {
            throw UnprocessableEntityException("The signatory attestation must be accepted")
        }

        if (file == null || file.isEmpty) {
            throw UnprocessableEntityException("A signature image file is required")
        }
        val image = file.bytes
        val contentType = validateImage(image)

        val associationId = profile.id!!
        val now = Instant.now(clock).truncatedTo(ChronoUnit.MICROS)

        profile.signerName = name
        profile.signerRole = role
        associationProfileRepository.save(profile)

        val signature = signatureRepository.findById(associationId).orElse(null)
            ?.apply {
                this.mode = signatureMode
                this.image = image
                this.contentType = contentType
                this.attestedAt = now
                this.updatedAt = now
            }
            ?: AssociationSignature(
                associationId = associationId,
                mode = signatureMode,
                image = image,
                contentType = contentType,
                attestedAt = now,
                updatedAt = now,
            )
        val saved = signatureRepository.save(signature)

        val imageSha256 = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(image))
        auditLog.append(
            eventType = ComplianceAuditLogService.RECEIPT_SIGNATURE_ATTESTED,
            subjectType = ComplianceAuditSubjectType.ASSOCIATION,
            subjectId = associationId,
            actorUserId = userId,
            payload = linkedMapOf(
                "signerName" to name,
                "signerRole" to role,
                "mode" to signatureMode.name,
                "imageSha256" to imageSha256,
                "attestation" to ATTESTATION_TEXT,
            ),
        )

        logger.info(
            "Receipt signature saved: associationId={}, userId={}, mode={}, imageBytes={}",
            associationId, userId, signatureMode, image.size,
        )
        return toDto(profile, saved)
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    /** Trims [value] and rejects it when blank or longer than [maxLength]. */
    private fun requireText(value: String?, maxLength: Int, label: String): String {
        val trimmed = value?.trim().orEmpty()
        if (trimmed.isEmpty()) throw UnprocessableEntityException("$label is required")
        if (trimmed.length > maxLength) {
            throw UnprocessableEntityException("$label must be at most $maxLength characters")
        }
        return trimmed
    }

    /**
     * Checks a signature image and returns its sniffed MIME type.
     *
     * Dimensions are read from the image header **before** any pixel is decoded: a small file can declare
     * huge dimensions, and decoding it first would allocate them. The image is then fully decoded, and
     * finally parsed by OpenPDF — the library that embeds it in the receipts — so an image accepted here
     * can never be silently dropped from a receipt later.
     *
     * @throws UnprocessableEntityException if the image is too big, of an unaccepted type, undecodable or
     *   outside the pixel bounds.
     */
    private fun validateImage(bytes: ByteArray): String {
        if (bytes.size > MAX_IMAGE_BYTES) {
            throw UnprocessableEntityException("Signature image exceeds the maximum allowed size of 200 KB")
        }
        val mime = FileTypeSniffer.detectImageMime(bytes)?.takeIf { it in ALLOWED_IMAGE_MIME }
            ?: run {
                logger.warn("Rejected signature upload: bytes are neither PNG nor JPEG")
                throw UnprocessableEntityException("Signature image must be a PNG or JPEG file")
            }

        val decodable = runCatching {
            ImageIO.createImageInputStream(ByteArrayInputStream(bytes)).use { input ->
                val reader = ImageIO.getImageReaders(input).asSequence().firstOrNull() ?: return@use false
                try {
                    reader.input = input
                    val width = reader.getWidth(0)
                    val height = reader.getHeight(0)
                    if (width <= 0 || height <= 0 || width > MAX_IMAGE_WIDTH || height > MAX_IMAGE_HEIGHT) {
                        throw UnprocessableEntityException(
                            "Signature image must be at most ${MAX_IMAGE_WIDTH}x${MAX_IMAGE_HEIGHT} pixels"
                        )
                    }
                    reader.read(0) != null
                } finally {
                    reader.dispose()
                }
            }
        }.getOrElse { if (it is UnprocessableEntityException) throw it else false }
        if (!decodable) {
            logger.warn("Rejected signature upload: {} bytes could not be decoded", mime)
            throw UnprocessableEntityException("Signature image could not be decoded")
        }
        runCatching { com.lowagie.text.Image.getInstance(bytes) }.onFailure {
            logger.warn("Rejected signature upload: {} not embeddable in a PDF ({})", mime, it.message)
            throw UnprocessableEntityException("Signature image could not be decoded")
        }
        return mime
    }

    private fun toDto(profile: AssociationProfile, signature: AssociationSignature?) = SignatoryDto(
        signerName = profile.signerName,
        signerRole = profile.signerRole,
        mode = signature?.mode,
        hasSignature = signature != null,
        attestedAt = signature?.attestedAt,
        updatedAt = signature?.updatedAt,
    )

    private fun resolveProfile(userId: UUID): AssociationProfile =
        associationProfileRepository.findByUserId(userId)
            .orElseThrow { UserNotFoundException("Association profile not found for user $userId") }
}
