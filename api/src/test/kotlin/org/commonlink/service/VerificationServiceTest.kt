package org.commonlink.service

import io.mockk.every
import io.mockk.justRun
import io.mockk.mockk
import io.mockk.verify
import org.commonlink.config.RiskClassificationProperties
import org.commonlink.entity.AssociationProfile
import org.commonlink.entity.AssociationRegistryCheck
import org.commonlink.entity.BeneficialOwnerType
import org.commonlink.entity.ScopeVerdict
import org.commonlink.entity.User
import org.commonlink.entity.UserRole
import org.commonlink.entity.VerificationStatus
import org.commonlink.exception.ConflictException
import org.commonlink.repository.AssociationDocumentRepository
import org.commonlink.repository.AssociationProfileRepository
import org.commonlink.repository.AssociationRegistryCheckRepository
import org.commonlink.repository.BeneficialOwnerRepository
import org.commonlink.repository.UserRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import java.util.Optional
import java.util.UUID

/**
 * Unit tests for admin approve/reject email notification logic in [VerificationService].
 */
class VerificationServiceTest {

    private val associationRepo: AssociationProfileRepository = mockk()
    private val documentRepo: AssociationDocumentRepository = mockk()
    private val registryCheckRepo: AssociationRegistryCheckRepository = mockk(relaxed = true)
    private val emailService: EmailService = mockk(relaxed = true)
    private val userRepository: UserRepository = mockk()
    private val complianceAuditLogService: ComplianceAuditLogService = mockk(relaxed = true)
    private val beneficialOwnerRepo: BeneficialOwnerRepository = mockk()
    private val riskClassificationProperties: RiskClassificationProperties = mockk(relaxed = true)
    private val freezeScreeningOnboardingService: FreezeScreeningOnboardingService = mockk()

    private val service = VerificationService(associationRepo, documentRepo, registryCheckRepo, emailService, userRepository, complianceAuditLogService, beneficialOwnerRepo, riskClassificationProperties, freezeScreeningOnboardingService)

    private val associationId: UUID = UUID.fromString("00000000-0000-0000-0000-000000000042")
    private val userId: UUID = UUID.fromString("00000000-0000-0000-0000-000000000001")

    private fun mockCurator(email: String): User {
        val curator: User = mockk()
        every { curator.email } returns email
        return curator
    }

    private fun mockUnverifiedProfile(): AssociationProfile {
        val profile: AssociationProfile = mockk(relaxed = true)
        every { profile.id } returns associationId
        every { profile.name } returns "Association Test"
        every { profile.verificationStatus } returns VerificationStatus.UNVERIFIED
        every { associationRepo.findByUserId(userId) } returns Optional.of(profile)
        every { associationRepo.save(profile) } returns profile
        every { documentRepo.existsByAssociationIdAndDocType(any(), any()) } returns true
        return profile
    }

    private fun mockPendingProfile(
        contactEmail: String? = "contact@assoc.fr",
        userEmail: String = "user@assoc.fr",
        hasRepresentative: Boolean = true,
    ): AssociationProfile {
        val user: User = mockk(relaxed = true)
        every { user.email } returns userEmail

        val profile: AssociationProfile = mockk(relaxed = true)
        every { profile.id } returns associationId
        every { profile.name } returns "Association Test"
        every { profile.verificationStatus } returns VerificationStatus.PENDING
        every { profile.contactEmail } returns contactEmail
        every { profile.user } returns user

        every { associationRepo.findById(associationId) } returns Optional.of(profile)
        every { associationRepo.save(profile) } returns profile
        every { beneficialOwnerRepo.existsByAssociationIdAndTypeAndDiscardedFalse(associationId, BeneficialOwnerType.REPRESENTATIVE) } returns hasRepresentative
        every { freezeScreeningOnboardingService.runFreezeCheck(associationId, any()) } returns ScreeningOutcome.CLEAR

        return profile
    }

    // ─── submitVerification ──────────────────────────────────────────────────

    @Test
    fun `submitVerification notifies all CURATOR users by email`() {
        mockUnverifiedProfile()
        val curator1 = mockCurator("curator1@commonlink.org")
        val curator2 = mockCurator("curator2@commonlink.org")
        every { userRepository.findAllByRole(UserRole.CURATOR) } returns listOf(curator1, curator2)

        service.submitVerification(userId)

        verify(exactly = 1) { emailService.sendVerificationSubmittedToAdmin("Association Test", "curator1@commonlink.org") }
        verify(exactly = 1) { emailService.sendVerificationSubmittedToAdmin("Association Test", "curator2@commonlink.org") }
    }

    @Test
    fun `submitVerification does not throw when email service fails for a curator`() {
        mockUnverifiedProfile()
        every { userRepository.findAllByRole(UserRole.CURATOR) } returns listOf(mockCurator("curator@commonlink.org"))
        every { emailService.sendVerificationSubmittedToAdmin(any(), any()) } throws RuntimeException("SMTP unavailable")

        service.submitVerification(userId)
    }

    @Test
    fun `submitVerification succeeds silently when no CURATOR users exist`() {
        mockUnverifiedProfile()
        every { userRepository.findAllByRole(UserRole.CURATOR) } returns emptyList()

        service.submitVerification(userId)

        verify(exactly = 0) { emailService.sendVerificationSubmittedToAdmin(any(), any()) }
    }

    // ─── adminApprove ────────────────────────────────────────────────────────

    @Test
    fun `adminApprove sends approval email to contactEmail when present`() {
        val profile = mockPendingProfile(contactEmail = "contact@assoc.fr")

        service.adminApprove(associationId)

        verify(exactly = 1) {
            emailService.sendVerificationApprovedToAssociation("Association Test", "contact@assoc.fr")
        }
    }

    @Test
    fun `adminApprove falls back to user email when contactEmail is null`() {
        val profile = mockPendingProfile(contactEmail = null, userEmail = "user@assoc.fr")

        service.adminApprove(associationId)

        verify(exactly = 1) {
            emailService.sendVerificationApprovedToAssociation("Association Test", "user@assoc.fr")
        }
    }

    @Test
    fun `adminApprove does not throw when email service fails`() {
        mockPendingProfile()
        every {
            emailService.sendVerificationApprovedToAssociation(any(), any())
        } throws RuntimeException("SMTP unavailable")

        // Must not propagate the email failure
        service.adminApprove(associationId)
    }

    @Test
    fun `adminApprove freezes the latest registry check as decision evidence`() {
        val profile = mockPendingProfile()
        val checkId = UUID.randomUUID()
        val latest: AssociationRegistryCheck = mockk()
        every { latest.id } returns checkId
        every { latest.scopeVerdict } returns ScopeVerdict.IN_SCOPE
        every { registryCheckRepo.findTopByAssociationIdOrderByCheckedAtDesc(associationId) } returns latest

        service.adminApprove(associationId)

        verify { profile.decisionRegistryCheckId = checkId }
    }

    @Test
    fun `adminApprove freezes null when association was never scanned`() {
        val profile = mockPendingProfile()
        every { registryCheckRepo.findTopByAssociationIdOrderByCheckedAtDesc(associationId) } returns null

        service.adminApprove(associationId)

        verify { profile.decisionRegistryCheckId = null }
    }

    @Test
    fun `adminApprove throws ConflictException when status is not PENDING`() {
        val profile: AssociationProfile = mockk(relaxed = true)
        every { profile.verificationStatus } returns VerificationStatus.VERIFIED
        every { associationRepo.findById(associationId) } returns Optional.of(profile)

        assertThrows(ConflictException::class.java) {
            service.adminApprove(associationId)
        }
        verify(exactly = 0) { emailService.sendVerificationApprovedToAssociation(any(), any()) }
    }

    // ─── adminReject ─────────────────────────────────────────────────────────

    @Test
    fun `adminReject sends rejection email with reason to contactEmail when present`() {
        mockPendingProfile(contactEmail = "contact@assoc.fr")
        val reason = "Documents illisibles."

        service.adminReject(associationId, reason)

        verify(exactly = 1) {
            emailService.sendVerificationRejectedToAssociation("Association Test", "contact@assoc.fr", reason)
        }
    }

    @Test
    fun `adminReject falls back to user email when contactEmail is null`() {
        mockPendingProfile(contactEmail = null, userEmail = "user@assoc.fr")
        val reason = "Pièce d'identité expirée."

        service.adminReject(associationId, reason)

        verify(exactly = 1) {
            emailService.sendVerificationRejectedToAssociation("Association Test", "user@assoc.fr", reason)
        }
    }

    @Test
    fun `adminReject does not throw when email service fails`() {
        mockPendingProfile()
        every {
            emailService.sendVerificationRejectedToAssociation(any(), any(), any())
        } throws RuntimeException("SMTP unavailable")

        service.adminReject(associationId, "Motif test.")
    }

    @Test
    fun `adminReject throws ConflictException when status is not PENDING`() {
        val profile: AssociationProfile = mockk(relaxed = true)
        every { profile.verificationStatus } returns VerificationStatus.REJECTED
        every { associationRepo.findById(associationId) } returns Optional.of(profile)

        assertThrows(ConflictException::class.java) {
            service.adminReject(associationId, "Motif.")
        }
        verify(exactly = 0) { emailService.sendVerificationRejectedToAssociation(any(), any(), any()) }
    }

    // ─── scope check (catégorie 9220) ────────────────────────────────────────

    @Test
    fun `adminApprove succeeds when latest scan is IN_SCOPE`() {
        val profile = mockPendingProfile()
        val inScopeCheck: AssociationRegistryCheck = mockk(relaxed = true)
        every { inScopeCheck.scopeVerdict } returns ScopeVerdict.IN_SCOPE
        every { registryCheckRepo.findTopByAssociationIdOrderByCheckedAtDesc(associationId) } returns inScopeCheck

        service.adminApprove(associationId)

        verify(exactly = 0) { complianceAuditLogService.appendOutOfScopeRefusal(any(), any()) }
        verify(exactly = 1) { associationRepo.save(profile) }
    }

    @Test
    fun `adminApprove blocks and logs when latest scan is OUT_OF_SCOPE`() {
        mockPendingProfile()
        val outOfScopeCheck: AssociationRegistryCheck = mockk(relaxed = true)
        every { outOfScopeCheck.scopeVerdict } returns ScopeVerdict.OUT_OF_SCOPE
        every { outOfScopeCheck.legalCategory } returns "9230"
        every { registryCheckRepo.findTopByAssociationIdOrderByCheckedAtDesc(associationId) } returns outOfScopeCheck

        assertThrows(ConflictException::class.java) {
            service.adminApprove(associationId)
        }
        verify(exactly = 1) { complianceAuditLogService.appendOutOfScopeRefusal(associationId, "9230") }
        verify(exactly = 0) { emailService.sendVerificationApprovedToAssociation(any(), any()) }
    }

    @Test
    fun `adminApprove does not block when latest scan is UNDETERMINED`() {
        val profile = mockPendingProfile()
        val undeterminedCheck: AssociationRegistryCheck = mockk(relaxed = true)
        every { undeterminedCheck.scopeVerdict } returns ScopeVerdict.UNDETERMINED
        every { registryCheckRepo.findTopByAssociationIdOrderByCheckedAtDesc(associationId) } returns undeterminedCheck

        service.adminApprove(associationId)

        verify(exactly = 0) { complianceAuditLogService.appendOutOfScopeRefusal(any(), any()) }
        verify(exactly = 1) { associationRepo.save(profile) }
    }

    @Test
    fun `adminApprove does not block when no scan exists`() {
        val profile = mockPendingProfile()
        every { registryCheckRepo.findTopByAssociationIdOrderByCheckedAtDesc(associationId) } returns null

        service.adminApprove(associationId)

        verify(exactly = 0) { complianceAuditLogService.appendOutOfScopeRefusal(any(), any()) }
        verify(exactly = 1) { associationRepo.save(profile) }
    }

    // ─── representative check ─────────────────────────────────────────────────

    @Test
    fun `adminApprove throws ConflictException when no legal representative has been confirmed`() {
        mockPendingProfile(hasRepresentative = false)

        assertThrows(ConflictException::class.java) {
            service.adminApprove(associationId)
        }
        verify(exactly = 1) { complianceAuditLogService.appendNoRepresentativeRefusal(associationId) }
        verify(exactly = 0) { emailService.sendVerificationApprovedToAssociation(any(), any()) }
    }

    @Test
    fun `adminApprove proceeds when at least one legal representative is confirmed`() {
        val profile = mockPendingProfile(hasRepresentative = true)

        service.adminApprove(associationId)

        verify(exactly = 0) { complianceAuditLogService.appendNoRepresentativeRefusal(any()) }
        verify(exactly = 1) { associationRepo.save(profile) }
    }

    @Test
    fun `adminApprove succeeds when representative is confirmed but no beneficial owners exist`() {
        // BENEFICIAL_OWNER is optional — only REPRESENTATIVE absence blocks approval (art. R.561-3 CMF)
        val profile = mockPendingProfile(hasRepresentative = true)

        service.adminApprove(associationId)

        verify(exactly = 0) { complianceAuditLogService.appendNoRepresentativeRefusal(any()) }
        verify(exactly = 1) { associationRepo.save(profile) }
    }

    // ─── freeze screening ────────────────────────────────────────────────────

    @Test
    fun `adminApprove succeeds when freeze screening is clear`() {
        val profile = mockPendingProfile()
        every { freezeScreeningOnboardingService.runFreezeCheck(associationId, any()) } returns ScreeningOutcome.CLEAR

        service.adminApprove(associationId)

        verify(exactly = 1) { associationRepo.save(profile) }
    }

    @Test
    fun `adminApprove throws ConflictException and does not save when freeze screening has a hit`() {
        mockPendingProfile()
        every { freezeScreeningOnboardingService.runFreezeCheck(associationId, any()) } returns ScreeningOutcome.HIT

        val ex = assertThrows(ConflictException::class.java) { service.adminApprove(associationId) }

        assertEquals("Cannot approve: a match was found in the asset-freeze register — review compliance audit log", ex.message)
        verify(exactly = 0) { associationRepo.save(any()) }
        verify(exactly = 0) { emailService.sendVerificationApprovedToAssociation(any(), any()) }
    }

    @Test
    fun `adminApprove throws ConflictException and does not save when freeze screening is unavailable`() {
        mockPendingProfile()
        every { freezeScreeningOnboardingService.runFreezeCheck(associationId, any()) } returns ScreeningOutcome.UNAVAILABLE

        val ex = assertThrows(ConflictException::class.java) { service.adminApprove(associationId) }

        assertEquals("Cannot approve: freeze screening could not be completed — see compliance audit log for details", ex.message)
        verify(exactly = 0) { associationRepo.save(any()) }
        verify(exactly = 0) { emailService.sendVerificationApprovedToAssociation(any(), any()) }
    }
}
