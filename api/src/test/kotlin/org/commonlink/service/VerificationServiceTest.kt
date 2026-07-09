package org.commonlink.service

import io.mockk.every
import io.mockk.justRun
import io.mockk.mockk
import io.mockk.verify
import org.commonlink.entity.AssociationProfile
import org.commonlink.entity.User
import org.commonlink.entity.VerificationStatus
import org.commonlink.exception.ConflictException
import org.commonlink.repository.AssociationDocumentRepository
import org.commonlink.repository.AssociationProfileRepository
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
    private val emailService: EmailService = mockk(relaxed = true)

    private val service = VerificationService(associationRepo, documentRepo, emailService)

    private val associationId: UUID = UUID.fromString("00000000-0000-0000-0000-000000000042")

    private fun mockPendingProfile(contactEmail: String? = "contact@assoc.fr", userEmail: String = "user@assoc.fr"): AssociationProfile {
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

        return profile
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
}
