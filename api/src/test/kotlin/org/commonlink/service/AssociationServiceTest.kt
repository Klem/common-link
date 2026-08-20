package org.commonlink.service

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.commonlink.entity.AssociationProfile
import org.commonlink.entity.VerificationStatus
import org.commonlink.dto.UpdateAssociationProfileRequest
import org.commonlink.exception.ConflictException
import org.commonlink.exception.NotFoundException
import org.commonlink.repository.AssociationProfileRepository
import org.commonlink.repository.CampaignRepository
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.util.Optional
import java.util.UUID

/**
 * Plain mockk unit test for AssociationService — no Spring context.
 */
class AssociationServiceTest {

    private val associationRepo: AssociationProfileRepository = mockk()
    private val campaignRepo: CampaignRepository = mockk()
    // Relaxed: chain guards are no-ops here; dedicated gate behaviour is covered in OnboardingGateService tests.
    private val onboardingGate: OnboardingGateService = mockk(relaxed = true)

    private val service = AssociationService(associationRepo, campaignRepo, onboardingGate)

    private val associationId = UUID.fromString("00000000-0000-0000-0000-000000000001")

    private val mockAssociation: AssociationProfile = mockk(relaxed = true)

    private fun stubAssociation() {
        every { mockAssociation.id } returns associationId
        every { mockAssociation.identifier } returns "123456789"
        every { mockAssociation.verificationStatus = any() } returns Unit
        every { mockAssociation.verifiedAt = any() } returns Unit
        every { associationRepo.findById(associationId) } returns Optional.of(mockAssociation)
        every { associationRepo.save(mockAssociation) } returns mockAssociation
    }

    // ── markVerified / revokeAssociation / restoreAssociation ─────────────

    @Test
    fun `markVerified - sets status to VERIFIED`() {
        stubAssociation()

        service.markVerified(associationId)

        verify { mockAssociation.verificationStatus = VerificationStatus.VERIFIED }
    }

    @Test
    fun `revokeAssociation - sets status to UNVERIFIED`() {
        stubAssociation()

        service.revokeAssociation(associationId)

        verify { mockAssociation.verificationStatus = VerificationStatus.UNVERIFIED }
    }

    @Test
    fun `restoreAssociation - sets status to VERIFIED`() {
        stubAssociation()

        service.restoreAssociation(associationId)

        verify { mockAssociation.verificationStatus = VerificationStatus.VERIFIED }
    }

    // ── updateProfile guards ─────────────────────────────────────────────────

    @Test
    fun `updateProfile - siren cannot be modified once VERIFIED`() {
        val userId = UUID.randomUUID()
        val profile = mockk<AssociationProfile>(relaxed = true)
        every { profile.verificationStatus } returns VerificationStatus.VERIFIED
        every { associationRepo.findByUserId(userId) } returns Optional.of(profile)

        val req = UpdateAssociationProfileRequest(
            contactName = null, city = null, postalCode = null, description = null,
            siren = "123456789", creationYear = null, contactEmail = null, phone = null,
            widgetDestinationCampaignId = null,
        )

        assertThrows<ConflictException> { service.updateProfile(userId, req) }
    }

    @Test
    fun `updateProfile - creationYear cannot be modified once VERIFIED`() {
        val userId = UUID.randomUUID()
        val profile = mockk<AssociationProfile>(relaxed = true)
        every { profile.verificationStatus } returns VerificationStatus.VERIFIED
        every { associationRepo.findByUserId(userId) } returns Optional.of(profile)

        val req = UpdateAssociationProfileRequest(
            contactName = null, city = null, postalCode = null, description = null,
            siren = null, creationYear = 2020, contactEmail = null, phone = null,
            widgetDestinationCampaignId = null,
        )

        assertThrows<ConflictException> { service.updateProfile(userId, req) }
    }

    @Test
    fun `updateProfile - contactName cannot be modified once Mollie KYC started`() {
        val userId = UUID.randomUUID()
        val profile = mockk<AssociationProfile>(relaxed = true)
        every { profile.verificationStatus } returns VerificationStatus.UNVERIFIED
        every { associationRepo.findByUserId(userId) } returns Optional.of(profile)
        every { onboardingGate.isMollieKycStarted(userId) } returns true

        val req = UpdateAssociationProfileRequest(
            contactName = "Jean Martin", city = null, postalCode = null, description = null,
            siren = null, creationYear = null, contactEmail = null, phone = null,
            widgetDestinationCampaignId = null,
        )

        assertThrows<ConflictException> { service.updateProfile(userId, req) }
    }

    @Test
    fun `updateProfile - contactEmail cannot be modified once Mollie KYC started`() {
        val userId = UUID.randomUUID()
        val profile = mockk<AssociationProfile>(relaxed = true)
        every { profile.verificationStatus } returns VerificationStatus.UNVERIFIED
        every { associationRepo.findByUserId(userId) } returns Optional.of(profile)
        every { onboardingGate.isMollieKycStarted(userId) } returns true

        val req = UpdateAssociationProfileRequest(
            contactName = null, city = null, postalCode = null, description = null,
            siren = null, creationYear = null, contactEmail = "new@example.com", phone = null,
            widgetDestinationCampaignId = null,
        )

        assertThrows<ConflictException> { service.updateProfile(userId, req) }
    }

    @Test
    fun `updateProfile - siren same value is allowed when VERIFIED`() {
        val userId = UUID.randomUUID()
        val profile = mockk<AssociationProfile>(relaxed = true)
        every { profile.verificationStatus } returns VerificationStatus.VERIFIED
        every { profile.siren } returns "123456789"
        every { associationRepo.findByUserId(userId) } returns Optional.of(profile)
        every { associationRepo.save(profile) } returns profile

        val req = UpdateAssociationProfileRequest(
            contactName = null, city = null, postalCode = null, description = null,
            siren = "123456789", creationYear = null, contactEmail = null, phone = null,
            widgetDestinationCampaignId = null,
        )

        // Resending the stored value is not a modification — must not throw
        service.updateProfile(userId, req)
    }

    @Test
    fun `updateProfile - contactName same value is allowed when Mollie KYC started`() {
        val userId = UUID.randomUUID()
        val profile = mockk<AssociationProfile>(relaxed = true)
        every { profile.verificationStatus } returns VerificationStatus.UNVERIFIED
        every { profile.contactName } returns "Jean Martin"
        every { associationRepo.findByUserId(userId) } returns Optional.of(profile)
        every { onboardingGate.isMollieKycStarted(userId) } returns true
        every { associationRepo.save(profile) } returns profile

        val req = UpdateAssociationProfileRequest(
            contactName = "Jean Martin", city = null, postalCode = null, description = null,
            siren = null, creationYear = null, contactEmail = null, phone = null,
            widgetDestinationCampaignId = null,
        )

        // Resending the stored value is not a modification — must not throw
        service.updateProfile(userId, req)
    }

    // ── T6 : multi-tenant isolation ──────────────────────────────────────────

    @Test
    fun `updateProfile - widgetDestinationCampaignId from another association throws NotFoundException`() {
        val userId = UUID.randomUUID()
        val ownAssocId = UUID.randomUUID()
        val foreignCampaignId = UUID.randomUUID()

        val profile = mockk<AssociationProfile>(relaxed = true)
        every { profile.id } returns ownAssocId
        every { associationRepo.findByUserId(userId) } returns Optional.of(profile)
        // Campaign lookup scoped to ownAssocId returns empty → belongs to another association
        every { campaignRepo.findByIdAndAssociationId(foreignCampaignId, ownAssocId) } returns Optional.empty()

        val req = UpdateAssociationProfileRequest(
            contactName = null, city = null, postalCode = null, description = null,
            siren = null, creationYear = null, contactEmail = null, phone = null,
            widgetDestinationCampaignId = foreignCampaignId,
        )

        assertThrows<NotFoundException> { service.updateProfile(userId, req) }
    }

    @Test
    fun `updateProfile - a blank siren is stored as null, not as an empty string`() {
        // A cleared form field arrives as "". Stored verbatim, it later reaches Recherche
        // d'entreprises as an empty query term, which the registry rejects with a 400.
        val userId = UUID.randomUUID()
        val profile = mockk<AssociationProfile>(relaxed = true)
        every { profile.verificationStatus } returns VerificationStatus.PENDING
        every { associationRepo.findByUserId(userId) } returns Optional.of(profile)
        every { associationRepo.save(profile) } returns profile

        val req = UpdateAssociationProfileRequest(
            contactName = null, city = null, postalCode = null, description = null,
            siren = "   ", creationYear = null, contactEmail = null, phone = null,
            widgetDestinationCampaignId = null,
        )

        service.updateProfile(userId, req)

        verify { profile.siren = null }
    }

    @Test
    fun `updateProfile - a siren is trimmed before being stored`() {
        val userId = UUID.randomUUID()
        val profile = mockk<AssociationProfile>(relaxed = true)
        every { profile.verificationStatus } returns VerificationStatus.PENDING
        every { associationRepo.findByUserId(userId) } returns Optional.of(profile)
        every { associationRepo.save(profile) } returns profile

        val req = UpdateAssociationProfileRequest(
            contactName = null, city = null, postalCode = null, description = null,
            siren = " 123456789 ", creationYear = null, contactEmail = null, phone = null,
            widgetDestinationCampaignId = null,
        )

        service.updateProfile(userId, req)

        verify { profile.siren = "123456789" }
    }
}
