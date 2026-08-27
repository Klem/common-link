package org.commonlink.repository

import org.commonlink.entity.LegalAcceptance
import org.commonlink.entity.LegalAcceptanceSubjectType
import org.commonlink.entity.LegalDocumentType
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface LegalAcceptanceRepository : JpaRepository<LegalAcceptance, UUID> {

    /**
     * Whether [subjectId] already has a row for (documentType, documentVersion). Backed by the
     * partial unique index `legal_acceptance_association_version_uq` for ASSOCIATION subjects;
     * also used, without that constraint, to make donor-row writes idempotent per donation.
     */
    fun existsBySubjectTypeAndSubjectIdAndDocumentTypeAndDocumentVersion(
        subjectType: LegalAcceptanceSubjectType,
        subjectId: UUID,
        documentType: LegalDocumentType,
        documentVersion: String,
    ): Boolean

    /** Whether a donor-acceptance row already exists for this donation + document — idempotency guard. */
    fun existsByDonationIdAndDocumentType(donationId: UUID, documentType: LegalDocumentType): Boolean

    /** Full acceptance history for one account — backs the compliance restitution endpoint. */
    fun findAllBySubjectTypeAndSubjectIdOrderByAcceptedAtDesc(
        subjectType: LegalAcceptanceSubjectType,
        subjectId: UUID,
    ): List<LegalAcceptance>
}
