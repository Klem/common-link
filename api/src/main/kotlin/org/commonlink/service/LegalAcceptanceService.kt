package org.commonlink.service

import org.commonlink.dto.DonorLegalAcceptanceGroupDto
import org.commonlink.dto.LegalAcceptanceDto
import org.commonlink.dto.LegalAcceptanceStateDto
import org.commonlink.dto.LegalDocumentDto
import org.commonlink.dto.PublishTermsAcceptance
import org.commonlink.entity.LegalAcceptance
import org.commonlink.entity.LegalAcceptanceSubjectType
import org.commonlink.entity.LegalDocument
import org.commonlink.entity.LegalDocumentType
import org.commonlink.exception.LegalDocumentVersionOutdatedException
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
 * Proof-of-acceptance engine for the legal documents — CGU, CGV and privacy policy (ACPR notice; art. 1740 A
 * CGI; GDPR transparency).
 *
 * Two distinct write shapes over the same [LegalAcceptance] table — see that entity's KDoc for why:
 * association rows are deduplicated per (association, document, version); donor rows are written
 * fresh on every donation.
 *
 * Every write records the version **the client displayed** and refuses it with
 * [LegalDocumentVersionOutdatedException] when it is no longer the current one: a new text may have been
 * published (at deploy time, see [org.commonlink.bootstrap.LegalDocumentSeeder]) while the form was open.
 * Only the current version is ever asked for: versions published and superseded between two visits are
 * never presented, so several changes in a row cost one acceptance.
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
     * Records the association's acceptance of [acceptedVersion] of [documentType] outside any campaign
     * publication: the post-login gate shown when a new version was published since the last acceptance.
     * No-op when the current version is already accepted (double click, second tab). The signer is
     * snapshotted exactly as at publication time: legal signatory's name, else the association's, and the
     * account e-mail.
     *
     * @throws LegalDocumentVersionOutdatedException if [acceptedVersion] is not the current version.
     */
    @Transactional
    fun acceptForAssociationUser(
        userId: UUID,
        documentType: LegalDocumentType,
        acceptedVersion: String,
    ): LegalAcceptanceStateDto {
        val profile = associationProfileRepository.findByUserId(userId)
            .orElseThrow { UserNotFoundException("Association profile not found for user $userId") }
        val associationId = profile.id!!
        val current = currentDocument(documentType)
        if (hasAssociationAccepted(associationId, documentType, current.version)) {
            return LegalAcceptanceStateDto(documentType, current.version, true)
        }
        requireCurrentVersion(current, acceptedVersion)
        saveAssociationAcceptance(
            associationId, current, signerName = profile.signerName ?: profile.name,
            signerEmail = profile.user.email, campaignId = null,
        )
        return LegalAcceptanceStateDto(documentType, current.version, true)
    }

    /**
     * Donation-time guard, called before anything is created: every document of [DONOR_DOCUMENTS] must be
     * present in [acceptedVersions] with its current version.
     *
     * @throws LegalDocumentVersionOutdatedException otherwise.
     */
    @Transactional(readOnly = true)
    fun requireCurrentDonorVersions(acceptedVersions: Map<LegalDocumentType, String>) {
        for (documentType in DONOR_DOCUMENTS) {
            requireCurrentVersion(currentDocument(documentType), acceptedVersions[documentType])
        }
    }

    /**
     * Publish-time gate over every document of [ASSOCIATION_DOCUMENTS]. A document [associationId] already
     * accepted in its current version is skipped — this is what makes the frontend's "auto-checked and greyed
     * out for later campaigns" behaviour safe rather than a stale rubber stamp: a version bump makes it pending
     * again and the gate re-engages. Every pending document is checked before any is recorded, so a refusal
     * writes nothing.
     *
     * @param acceptances what the publish dialog sent for each document; a document missing from the map counts
     *   as not accepted. Ignored for the documents already accepted.
     * @throws UnprocessableEntityException if a pending document is not accepted.
     * @throws LegalDocumentVersionOutdatedException if a pending document is accepted in a version that is not
     *   the current one.
     */
    @Transactional
    fun requireAssociationAcceptances(
        associationId: UUID,
        acceptances: Map<LegalDocumentType, PublishTermsAcceptance>,
        signerName: String?,
        signerEmail: String?,
        campaignId: UUID,
    ) {
        val pending = ASSOCIATION_DOCUMENTS
            .map { currentDocument(it) }
            .filterNot { hasAssociationAccepted(associationId, it.documentType, it.version) }
        for (current in pending) {
            val acceptance = acceptances[current.documentType]
            if (acceptance?.accepted != true) {
                throw UnprocessableEntityException(
                    "You must accept the current ${current.documentType} (version ${current.version}) before publishing"
                )
            }
            requireCurrentVersion(current, acceptance.version)
        }
        pending.forEach { saveAssociationAcceptance(associationId, it, signerName, signerEmail, campaignId) }
    }

    private fun hasAssociationAccepted(associationId: UUID, documentType: LegalDocumentType, version: String): Boolean =
        legalAcceptanceRepository.existsBySubjectTypeAndSubjectIdAndDocumentTypeAndDocumentVersion(
            LegalAcceptanceSubjectType.ASSOCIATION, associationId, documentType, version,
        )

    private fun saveAssociationAcceptance(
        associationId: UUID,
        current: LegalDocument,
        signerName: String?,
        signerEmail: String?,
        campaignId: UUID?,
    ) {
        legalAcceptanceRepository.save(
            LegalAcceptance(
                subjectType = LegalAcceptanceSubjectType.ASSOCIATION,
                subjectId = associationId,
                documentType = current.documentType,
                documentVersion = current.version,
                signerName = signerName,
                signerEmail = signerEmail,
                campaignId = campaignId,
            )
        )
        logger.info(
            "Legal acceptance recorded: subjectType=ASSOCIATION subjectId={} documentType={} version={} campaignId={}",
            associationId, current.documentType, current.version, campaignId,
        )
    }

    private fun requireCurrentVersion(current: LegalDocument, acceptedVersion: String?) {
        if (acceptedVersion != current.version) {
            logger.info(
                "Legal acceptance refused, outdated version: documentType={} accepted={} current={}",
                current.documentType, acceptedVersion, current.version,
            )
            throw LegalDocumentVersionOutdatedException(
                "${current.documentType} version $acceptedVersion is not the current version (${current.version})"
            )
        }
    }

    /**
     * Records donor acceptance of [DONOR_DOCUMENTS] for one donation — a donation is a
     * transactional act, so acceptance is captured fresh every time rather than reused across
     * future donations. Idempotent per (donationId, documentType): a retried request writes
     * nothing twice.
     *
     * @param acceptedVersions the version of each document the donation form displayed, already checked
     *   against the current ones by [requireCurrentDonorVersions] before the donation was created.
     */
    @Transactional
    fun recordDonorAcceptance(
        donorProfileId: UUID,
        donationId: UUID,
        campaignId: UUID,
        signerName: String?,
        signerEmail: String?,
        acceptedVersions: Map<LegalDocumentType, String>,
    ) {
        for (documentType in DONOR_DOCUMENTS) {
            if (legalAcceptanceRepository.existsByDonationIdAndDocumentType(donationId, documentType)) continue
            val version = acceptedVersions[documentType]
                ?: throw IllegalArgumentException("No accepted version given for $documentType")
            legalAcceptanceRepository.save(
                LegalAcceptance(
                    subjectType = LegalAcceptanceSubjectType.DONOR,
                    subjectId = donorProfileId,
                    documentType = documentType,
                    documentVersion = version,
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

    /**
     * Donor acceptance proof (CGU and privacy policy) for one campaign, grouped by donor. Rows are read most-recent
     * first, and `groupBy` preserves that order per key (Kotlin backs it with a `LinkedHashMap`), so
     * no separate sort is needed for either the group order or a group's own [donorName]/[donorEmail]
     * snapshot to land on that donor's latest acceptance.
     *
     * Includes every acceptance written at donation-*intent* time — [recordDonorAcceptance] is
     * called right after the pending `Donation` row is created, before the Mollie payment
     * confirms. A donor here may therefore have never completed payment; this is correct for a
     * consent record (art. 1740 A CGI cares that the box was ticked, not whether the payment later
     * succeeded), but it means this is not the same donor set as "who funded this campaign".
     */
    @Transactional(readOnly = true)
    fun donorAcceptancesForCampaign(campaignId: UUID): List<DonorLegalAcceptanceGroupDto> =
        legalAcceptanceRepository.findAllByCampaignIdAndSubjectTypeOrderByAcceptedAtDesc(
            campaignId, LegalAcceptanceSubjectType.DONOR,
        )
            .groupBy { it.subjectId }
            .map { (donorId, rows) ->
                DonorLegalAcceptanceGroupDto(
                    donorId = donorId,
                    donorName = rows.first().signerName,
                    donorEmail = rows.first().signerEmail,
                    acceptances = rows.map { it.toDto() },
                )
            }

    private fun LegalDocument.toDto() = LegalDocumentDto(
        documentType = documentType,
        version = version,
        format = contentFormat,
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

    companion object {
        /**
         * What a donor accepts on every donation: the CGU (donor terms) and the privacy policy (decision of
         * 2026-11-24, accepted through the form's GDPR checkbox). The `CGV` document is the associations' CGU and
         * does not concern donors (decision of 2026-11-16).
         */
        val DONOR_DOCUMENTS = listOf(LegalDocumentType.CGU, LegalDocumentType.PRIVACY)

        /**
         * What an association accepts — at campaign publication and at the post-login gate: the `CGV` document,
         * which holds the beneficiary-organisation terms (the landing site's `cguAssociations`), not the donor
         * CGU (decision of 2026-11-16), and the privacy policy (decision of 2026-11-24).
         */
        val ASSOCIATION_DOCUMENTS = listOf(LegalDocumentType.CGV, LegalDocumentType.PRIVACY)
    }
}
