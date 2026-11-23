package org.commonlink.service

import org.commonlink.entity.AssociationProfile
import org.commonlink.entity.ReceiptPrerequisite
import org.commonlink.repository.AssociationSignatureRepository
import org.commonlink.repository.FiscalMandateRepository
import org.springframework.stereotype.Service

/**
 * Single place deciding whether an association can have fiscal receipts issued in its name.
 *
 * A receipt is generated automatically once a donation is paid, so every step upstream of the payment
 * applies the same rule: signing the fiscal mandate ([MandateService.signMandate], prerequisites only),
 * publishing a campaign ([CampaignService] publish guard), collecting a donation and showing the donate
 * button ([PublicWidgetService]); [ReceiptService.generate] re-checks it defensively.
 *
 * Database reads only (two primary-key lookups at most), no network call: safe on unauthenticated public GETs.
 */
@Service
class ReceiptPrerequisiteService(
    private val signatureRepository: AssociationSignatureRepository,
    private val mandateRepository: FiscalMandateRepository,
) {

    /**
     * Receipt prerequisites [association] is missing, in [ReceiptPrerequisite] declaration order; empty when
     * receipts can carry every mandatory element.
     *
     * - [ReceiptPrerequisite.SIGNATURE]: no `association_signature` row.
     * - [ReceiptPrerequisite.LEGAL_OBJECT]: [AssociationProfile.legalObject] blank.
     * - [ReceiptPrerequisite.HEAD_OFFICE_ADDRESS]: any of [AssociationProfile.addressLine1],
     *   [AssociationProfile.postalCode], [AssociationProfile.city] blank.
     *
     * The signer name and role are not part of it: [MandateService.signMandate] already requires them, and
     * they are only written together with the signature ([SignatoryService.saveSignatory]).
     */
    fun missing(association: AssociationProfile): List<ReceiptPrerequisite> = buildList {
        if (!signatureRepository.existsById(association.id!!)) add(ReceiptPrerequisite.SIGNATURE)
        if (association.legalObject.isNullOrBlank()) add(ReceiptPrerequisite.LEGAL_OBJECT)
        if (association.addressLine1.isNullOrBlank() ||
            association.postalCode.isNullOrBlank() ||
            association.city.isNullOrBlank()
        ) {
            add(ReceiptPrerequisite.HEAD_OFFICE_ADDRESS)
        }
    }

    /** True when [association] holds an active (non-revoked) fiscal mandate — the receipts' legal basis. */
    fun hasActiveMandate(association: AssociationProfile): Boolean =
        mandateRepository.findByAssociationIdAndRevokedAtIsNull(association.id!!) != null

    /**
     * True when a receipt can be issued for a donation to [association] right now: [hasActiveMandate] and no
     * [ReceiptPrerequisite] is [missing].
     */
    fun canIssueReceipts(association: AssociationProfile): Boolean =
        hasActiveMandate(association) && missing(association).isEmpty()
}
