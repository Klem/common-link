package org.commonlink.dto

import io.swagger.v3.oas.annotations.media.Schema
import org.commonlink.entity.SignatureMode
import java.time.Instant

/**
 * Authorised receipt signer of an association and the state of its receipt signature.
 *
 * The image itself is never inlined: it is served by `GET /api/association/me/signatory/image`.
 *
 * @param signerName Full name of the authorised signer, printed on the receipts; null until set.
 * @param signerRole Role of the authorised signer (e.g. "Trésorier"); null until set.
 * @param mode How the signature was produced; null when no signature has been saved.
 * @param hasSignature True when a signature (always an image) has been saved.
 * @param attestedAt Instant of the latest signatory attestation; null when no signature.
 * @param updatedAt Instant of the latest save; null when no signature.
 */
@Schema(description = "Authorised receipt signer and receipt signature state")
data class SignatoryDto(
    val signerName: String?,
    val signerRole: String?,
    val mode: SignatureMode?,
    val hasSignature: Boolean,
    val attestedAt: Instant?,
    val updatedAt: Instant?,
)
