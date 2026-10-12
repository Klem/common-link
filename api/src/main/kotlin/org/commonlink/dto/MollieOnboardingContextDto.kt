package org.commonlink.dto

import org.commonlink.entity.AssociationDocumentType
import org.commonlink.entity.BeneficialOwnerType
import java.time.Instant
import java.util.UUID

/**
 * Legal structure variant of an association, as used by the "Bank account" tab's Mollie
 * onboarding guide to personalise the proof-of-existence document an association must gather.
 *
 * Derived from the INSEE legal category (`nature_juridique`) captured during the CommonLink KYB
 * registry scan — see [structureVariantOf]. Informational only: it never gates anything.
 */
enum class MollieStructureVariant {
    /** Declared association, loi 1901 (INSEE 9220-9223). Proof: prefecture receipt or JOAFE publication. */
    DECLARED,

    /** Association recognised as being of public utility (INSEE 9230). Proof: recognition decree. */
    ARUP,

    /** Association registered under Alsace-Moselle local law (INSEE 9260). Proof: court register extract. */
    ALSACE_MOSELLE,

    /** Legal category unknown (no scan, source unavailable) or outside the three variants above. */
    UNKNOWN,
}

/**
 * Maps an INSEE legal category to the [MollieStructureVariant] shown in the Mollie onboarding guide.
 *
 * Single mapping point for this derivation — do not duplicate it in the frontend.
 *
 * @param legalCategory INSEE `nature_juridique` code (e.g. "9220"), or null when unknown.
 * @return [MollieStructureVariant.DECLARED] for 9220-9223, [MollieStructureVariant.ARUP] for 9230,
 *   [MollieStructureVariant.ALSACE_MOSELLE] for 9260, [MollieStructureVariant.UNKNOWN] otherwise
 *   (including null and blank values).
 */
fun structureVariantOf(legalCategory: String?): MollieStructureVariant = when (legalCategory?.trim()) {
    "9220", "9221", "9222", "9223" -> MollieStructureVariant.DECLARED
    "9230" -> MollieStructureVariant.ARUP
    "9260" -> MollieStructureVariant.ALSACE_MOSELLE
    else -> MollieStructureVariant.UNKNOWN
}

/**
 * Context needed by the "Bank account" tab to guide an association through its own Mollie
 * onboarding (`GET /api/mollie/connect/onboarding-context`).
 *
 * Read-only projection of CommonLink's KYB data. Nothing here is sent to or received from Mollie,
 * and nothing here unlocks anything — the collection lock remains
 * [org.commonlink.entity.MollieConnection.canCollectDonations].
 */
data class MollieOnboardingContextDto(
    /** Legal structure variant derived from the KYB registry scan's INSEE legal category. */
    val structureVariant: MollieStructureVariant,
    /** When CommonLink's KYB dossier was approved, null if not (yet) verified. */
    val verifiedAt: Instant?,
    /** Beneficial owners confirmed during KYB and not discarded — display name and role only. */
    val beneficialOwners: List<OnboardingBeneficialOwnerDto>,
    /** KYB documents already uploaded to CommonLink, re-downloadable by the association. */
    val providedDocuments: List<OnboardingProvidedDocumentDto>,
    /** SIREN (9 digits) if known, null otherwise. */
    val siren: String?,
    /** When the association first attested having filled its Mollie dossier itself, null if never. */
    val selfCompletionAttestedAt: Instant?,
)

/**
 * A beneficial owner as exposed to its own association in the onboarding guide.
 *
 * Deliberately excludes the date of birth and any identity document: only what the guide needs
 * to tell the association whose ID Mollie will ask for.
 */
data class OnboardingBeneficialOwnerDto(
    /** Decrypted display name. */
    val name: String,
    /** Role in the association (e.g. "Présidente"), as recorded by the curator, if any. */
    val role: String?,
    /** Whether the person is a strict beneficial owner or a legal representative. */
    val type: BeneficialOwnerType,
)

/** A KYB document already provided to CommonLink — type and id only, no file name or content. */
data class OnboardingProvidedDocumentDto(
    /** Document id, usable with `GET /api/association/documents/{id}/content`. */
    val id: UUID,
    /** KYB slot: [AssociationDocumentType.VERIF_STATUTS], [AssociationDocumentType.VERIF_RNA_RECEIPT] or [AssociationDocumentType.VERIF_REPRESENTATIVE_ID]. */
    val type: AssociationDocumentType,
)

/** Response of `POST /api/mollie/connect/self-completion-attestation`. */
data class MollieSelfCompletionAttestationDto(
    /** Timestamp of the (first) attestation recorded in the compliance journal. */
    val attestedAt: Instant,
)
