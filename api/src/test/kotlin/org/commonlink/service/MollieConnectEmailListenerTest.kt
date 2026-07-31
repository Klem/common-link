package org.commonlink.service

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.commonlink.entity.AssociationProfile
import org.commonlink.entity.MollieOnboardingStatus
import org.commonlink.event.MollieOnboardingStatusChangedEvent
import org.commonlink.repository.AssociationProfileRepository
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.Optional
import java.util.UUID

/**
 * Unit tests for [MollieConnectEmailListener].
 *
 * Verifies that each status transition triggers the correct [EmailService] method exactly once,
 * including the regression path (IN_REVIEW → NEEDS_DATA). Runs without Spring context.
 */
class MollieConnectEmailListenerTest {

    private val emailService: EmailService = mockk(relaxed = true)
    private val associationRepo: AssociationProfileRepository = mockk()
    private lateinit var listener: MollieConnectEmailListener

    private val associationId: UUID = UUID.randomUUID()
    private val associationName = "Les Restos du Cœur"
    private val contactEmail = "contact@restos.org"

    @BeforeEach
    fun setUp() {
        listener = MollieConnectEmailListener(emailService, associationRepo)
        val profile: AssociationProfile = mockk()
        every { profile.contactEmail } returns contactEmail
        every { profile.name } returns associationName
        every { associationRepo.findById(associationId) } returns Optional.of(profile)
    }

    @Test
    fun `sends needs-data email on NEEDS_DATA transition`() {
        listener.onStatusChanged(event(MollieOnboardingStatus.IN_REVIEW, MollieOnboardingStatus.NEEDS_DATA))

        verify(exactly = 1) { emailService.sendMollieOnboardingNeedsData(associationName, contactEmail) }
        verify(exactly = 0) { emailService.sendMollieOnboardingInReview(associationName, contactEmail) }
        verify(exactly = 0) { emailService.sendMollieOnboardingCompleted(associationName, contactEmail) }
    }

    @Test
    fun `sends in-review email on IN_REVIEW transition`() {
        listener.onStatusChanged(event(MollieOnboardingStatus.NEEDS_DATA, MollieOnboardingStatus.IN_REVIEW))

        verify(exactly = 1) { emailService.sendMollieOnboardingInReview(associationName, contactEmail) }
        verify(exactly = 0) { emailService.sendMollieOnboardingNeedsData(associationName, contactEmail) }
        verify(exactly = 0) { emailService.sendMollieOnboardingCompleted(associationName, contactEmail) }
    }

    @Test
    fun `sends completed email on COMPLETED transition`() {
        listener.onStatusChanged(event(MollieOnboardingStatus.IN_REVIEW, MollieOnboardingStatus.COMPLETED))

        verify(exactly = 1) { emailService.sendMollieOnboardingCompleted(associationName, contactEmail) }
        verify(exactly = 0) { emailService.sendMollieOnboardingNeedsData(associationName, contactEmail) }
        verify(exactly = 0) { emailService.sendMollieOnboardingInReview(associationName, contactEmail) }
    }

    @Test
    fun `sends needs-data email on regression IN_REVIEW to NEEDS_DATA`() {
        listener.onStatusChanged(event(MollieOnboardingStatus.IN_REVIEW, MollieOnboardingStatus.NEEDS_DATA))

        verify(exactly = 1) { emailService.sendMollieOnboardingNeedsData(associationName, contactEmail) }
    }

    @Test
    fun `skips email when association not found`() {
        every { associationRepo.findById(associationId) } returns Optional.empty()

        listener.onStatusChanged(event(MollieOnboardingStatus.NEEDS_DATA, MollieOnboardingStatus.IN_REVIEW))

        verify(exactly = 0) { emailService.sendMollieOnboardingInReview(any(), any()) }
    }

    @Test
    fun `skips email when contactEmail is null`() {
        val profileNoEmail: AssociationProfile = mockk()
        every { profileNoEmail.contactEmail } returns null
        every { profileNoEmail.name } returns associationName
        every { associationRepo.findById(associationId) } returns Optional.of(profileNoEmail)

        listener.onStatusChanged(event(MollieOnboardingStatus.NEEDS_DATA, MollieOnboardingStatus.IN_REVIEW))

        verify(exactly = 0) { emailService.sendMollieOnboardingInReview(any(), any()) }
    }

    private fun event(previous: MollieOnboardingStatus, next: MollieOnboardingStatus) =
        MollieOnboardingStatusChangedEvent(
            associationId = associationId,
            previousStatus = previous,
            newStatus = next,
        )
}
