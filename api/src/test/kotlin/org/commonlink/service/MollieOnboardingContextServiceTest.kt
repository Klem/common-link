package org.commonlink.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.commonlink.dto.MollieStructureVariant
import org.commonlink.dto.structureVariantOf
import org.commonlink.entity.AssociationDocumentType
import org.commonlink.entity.AssociationProfile
import org.commonlink.entity.AssociationRegistryCheck
import org.commonlink.entity.BeneficialOwner
import org.commonlink.entity.BeneficialOwnerOrigin
import org.commonlink.entity.BeneficialOwnerType
import org.commonlink.entity.ComplianceAuditLog
import org.commonlink.entity.ComplianceAuditSubjectType
import org.commonlink.exception.NotFoundException
import org.commonlink.repository.AssociationDocumentMetadata
import org.commonlink.repository.AssociationDocumentRepository
import org.commonlink.repository.AssociationProfileRepository
import org.commonlink.repository.AssociationRegistryCheckRepository
import org.commonlink.repository.BeneficialOwnerRepository
import org.commonlink.repository.ComplianceAuditLogRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import java.time.Instant
import java.util.Optional
import java.util.UUID

/**
 * Unit tests for [MollieOnboardingContextService] and the [structureVariantOf] mapping.
 *
 * The service has no dependency on [org.commonlink.repository.MollieConnectionRepository] nor on
 * [OnboardingGateService]: it structurally cannot change `MollieConnection.canCollectDonations()`.
 * The attestation idempotency against a real journal is covered by
 * [ComplianceAuditLogServiceLogicTest].
 */
class MollieOnboardingContextServiceTest {

    private val associationRepo: AssociationProfileRepository = mockk()
    private val registryCheckRepo: AssociationRegistryCheckRepository = mockk()
    private val beneficialOwnerRepo: BeneficialOwnerRepository = mockk()
    private val documentRepo: AssociationDocumentRepository = mockk()
    private val complianceAuditLogService: ComplianceAuditLogService = mockk()

    private val service = MollieOnboardingContextService(
        associationRepo, registryCheckRepo, beneficialOwnerRepo, documentRepo, complianceAuditLogService,
    )

    private val userId: UUID = UUID.fromString("00000000-0000-0000-0000-000000000001")
    private val associationId: UUID = UUID.fromString("00000000-0000-0000-0000-000000000042")
    private val decisionCheckId: UUID = UUID.fromString("00000000-0000-0000-0000-0000000000c1")
    private val verifiedAt: Instant = Instant.parse("2026-09-12T10:00:00Z")

    private fun profile(siren: String? = null, decisionCheck: UUID? = decisionCheckId): AssociationProfile {
        val profile: AssociationProfile = mockk(relaxed = true)
        every { profile.id } returns associationId
        every { profile.siren } returns siren
        every { profile.verifiedAt } returns verifiedAt
        every { profile.decisionRegistryCheckId } returns decisionCheck
        every { associationRepo.findByUserId(userId) } returns Optional.of(profile)
        return profile
    }

    private fun bo(name: String, role: String?, discarded: Boolean = false, type: BeneficialOwnerType = BeneficialOwnerType.BENEFICIAL_OWNER) =
        BeneficialOwner(
            associationId = associationId,
            name = name,
            role = role,
            dateOfBirth = "1970-01-01",
            origin = BeneficialOwnerOrigin.STATUTS,
            type = type,
            collectedAt = Instant.parse("2026-09-01T10:00:00Z"),
            confirmedBy = UUID.randomUUID(),
            discarded = discarded,
        )

    private fun docMeta(type: AssociationDocumentType): AssociationDocumentMetadata {
        val meta: AssociationDocumentMetadata = mockk()
        every { meta.id } returns UUID.nameUUIDFromBytes(type.name.toByteArray())
        every { meta.docType } returns type
        return meta
    }

    private fun stubEmptyCollections() {
        every { beneficialOwnerRepo.findAllByAssociationIdOrderByCollectedAtAsc(associationId) } returns emptyList()
        every { documentRepo.findMetadataByAssociationId(associationId) } returns emptyList()
        every { complianceAuditLogService.findMollieSelfCompletionAttestation(associationId) } returns null
    }

    private fun journalRow(occurredAt: Instant) = ComplianceAuditLog(
        sequenceNo = 1,
        eventType = ComplianceAuditLogService.MOLLIE_SELF_COMPLETION_ATTESTED,
        subjectType = ComplianceAuditSubjectType.ASSOCIATION,
        subjectId = associationId,
        payload = "{}",
        actorUserId = userId,
        occurredAt = occurredAt,
        rowHash = "hash",
    )

    // ── structureVariantOf ───────────────────────────────────────────────────

    @ParameterizedTest
    @CsvSource(
        "9220, DECLARED",
        "9221, DECLARED",
        "9222, DECLARED",
        "9223, DECLARED",
        "9230, ARUP",
        "9260, ALSACE_MOSELLE",
        "9300, UNKNOWN",
        "5710, UNKNOWN",
    )
    fun `structureVariantOf maps INSEE legal categories to variants`(category: String, expected: MollieStructureVariant) {
        assertEquals(expected, structureVariantOf(category))
    }

    @Test
    fun `structureVariantOf - null or blank category is UNKNOWN`() {
        assertEquals(MollieStructureVariant.UNKNOWN, structureVariantOf(null))
        assertEquals(MollieStructureVariant.UNKNOWN, structureVariantOf(""))
    }

    // ── getOnboardingContext ─────────────────────────────────────────────────

    @Test
    fun `variant and siren come from the KYB decision registry check`() {
        profile(siren = null)
        stubEmptyCollections()
        every { registryCheckRepo.findById(decisionCheckId) } returns
            Optional.of(AssociationRegistryCheck(associationId = associationId, legalCategory = "9230", siren = "912345678"))

        val ctx = service.getOnboardingContext(userId)

        assertEquals(MollieStructureVariant.ARUP, ctx.structureVariant)
        assertEquals("912345678", ctx.siren)
        assertEquals(verifiedAt, ctx.verifiedAt)
        assertNull(ctx.selfCompletionAttestedAt)
        verify(exactly = 0) { registryCheckRepo.findTopByAssociationIdOrderByCheckedAtDesc(any()) }
    }

    @Test
    fun `falls back to the latest registry scan, and profile siren wins over the scan`() {
        profile(siren = "111222333", decisionCheck = null)
        stubEmptyCollections()
        every { registryCheckRepo.findTopByAssociationIdOrderByCheckedAtDesc(associationId) } returns
            AssociationRegistryCheck(associationId = associationId, legalCategory = "9260", siren = "999999999")

        val ctx = service.getOnboardingContext(userId)

        assertEquals(MollieStructureVariant.ALSACE_MOSELLE, ctx.structureVariant)
        assertEquals("111222333", ctx.siren)
    }

    @Test
    fun `no registry scan at all yields UNKNOWN variant and null siren`() {
        profile(siren = null, decisionCheck = null)
        stubEmptyCollections()
        every { registryCheckRepo.findTopByAssociationIdOrderByCheckedAtDesc(associationId) } returns null

        val ctx = service.getOnboardingContext(userId)

        assertEquals(MollieStructureVariant.UNKNOWN, ctx.structureVariant)
        assertNull(ctx.siren)
    }

    @Test
    fun `discarded beneficial owner is absent and no date of birth is serialized`() {
        profile()
        stubEmptyCollections()
        every { registryCheckRepo.findById(decisionCheckId) } returns Optional.empty()
        every { registryCheckRepo.findTopByAssociationIdOrderByCheckedAtDesc(associationId) } returns null
        every { beneficialOwnerRepo.findAllByAssociationIdOrderByCollectedAtAsc(associationId) } returns listOf(
            bo("Claire Martin", "Présidente", type = BeneficialOwnerType.REPRESENTATIVE),
            bo("Ancien Trésorier", "Trésorier", discarded = true),
            bo("Karim Benali", "Trésorier"),
        )

        val ctx = service.getOnboardingContext(userId)

        assertEquals(listOf("Claire Martin", "Karim Benali"), ctx.beneficialOwners.map { it.name })
        assertEquals(listOf("Présidente", "Trésorier"), ctx.beneficialOwners.map { it.role })
        val json = ObjectMapper().registerKotlinModule().registerModule(JavaTimeModule()).writeValueAsString(ctx)
        assertFalse(json.contains("dateOfBirth"), "date of birth must never be exposed: $json")
        assertFalse(json.contains("1970-01-01"), "date of birth value must never be exposed: $json")
        assertFalse(json.contains("Ancien Trésorier"), "discarded beneficial owner must be absent: $json")
    }

    @Test
    fun `provided documents are the KYB kit slots only, in kit order`() {
        profile()
        stubEmptyCollections()
        every { registryCheckRepo.findById(decisionCheckId) } returns Optional.empty()
        every { registryCheckRepo.findTopByAssociationIdOrderByCheckedAtDesc(associationId) } returns null
        every { documentRepo.findMetadataByAssociationId(associationId) } returns listOf(
            docMeta(AssociationDocumentType.VERIF_REPRESENTATIVE_ID),
            docMeta(AssociationDocumentType.OPTIONAL),
            docMeta(AssociationDocumentType.MANDATE_STATUTS),
            docMeta(AssociationDocumentType.VERIF_STATUTS),
        )

        val ctx = service.getOnboardingContext(userId)

        assertEquals(
            listOf(AssociationDocumentType.VERIF_STATUTS, AssociationDocumentType.VERIF_REPRESENTATIVE_ID),
            ctx.providedDocuments.map { it.type },
        )
    }

    @Test
    fun `selfCompletionAttestedAt is the first attestation timestamp`() {
        profile()
        stubEmptyCollections()
        every { registryCheckRepo.findById(decisionCheckId) } returns Optional.empty()
        every { registryCheckRepo.findTopByAssociationIdOrderByCheckedAtDesc(associationId) } returns null
        val attestedAt = Instant.parse("2026-10-12T09:00:00Z")
        every { complianceAuditLogService.findMollieSelfCompletionAttestation(associationId) } returns journalRow(attestedAt)

        assertEquals(attestedAt, service.getOnboardingContext(userId).selfCompletionAttestedAt)
    }

    @Test
    fun `unknown user has no association - NotFoundException`() {
        every { associationRepo.findByUserId(userId) } returns Optional.empty()

        assertThrows(NotFoundException::class.java) { service.getOnboardingContext(userId) }
        assertThrows(NotFoundException::class.java) { service.attestSelfCompletion(userId) }
    }

    // ── attestSelfCompletion ─────────────────────────────────────────────────

    @Test
    fun `attestation delegates to the idempotent journal helper with the text version only`() {
        profile()
        val attestedAt = Instant.parse("2026-10-12T09:00:00Z")
        every {
            complianceAuditLogService.appendMollieSelfCompletionAttestedOnce(
                associationId, userId, MollieOnboardingContextService.SELF_COMPLETION_ATTESTATION_TEXT_VERSION,
            )
        } returns journalRow(attestedAt)

        val dto = service.attestSelfCompletion(userId)

        assertEquals(attestedAt, dto.attestedAt)
    }

    @Test
    fun `attestation payload written to the real journal holds neither IP nor user-agent`() {
        profile()
        val repo: ComplianceAuditLogRepository = mockk(relaxed = true)
        every { repo.findBySubjectIdAndEventTypeInOrderBySequenceNoAsc(any(), any()) } returns emptyList()
        every { repo.nextSequenceValue() } returns 1L
        every { repo.findTopByOrderBySequenceNoDesc() } returns null
        val saved = slot<ComplianceAuditLog>()
        every { repo.save(capture(saved)) } answers { saved.captured }
        val serviceWithRealJournal = MollieOnboardingContextService(
            associationRepo, registryCheckRepo, beneficialOwnerRepo, documentRepo,
            ComplianceAuditLogService(repo, ObjectMapper()),
        )

        serviceWithRealJournal.attestSelfCompletion(userId)

        val payload = saved.captured.payload
        assertFalse(payload.contains("ipAddress") || payload.contains("userAgent"), "payload: $payload")
        assertEquals(
            """{"textVersion":"${MollieOnboardingContextService.SELF_COMPLETION_ATTESTATION_TEXT_VERSION}"}""",
            payload,
        )
    }
}
