package org.commonlink.service

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import io.mockk.verifyOrder
import org.commonlink.entity.AssociationProfile
import org.commonlink.entity.HelloAssoConnection
import org.commonlink.exception.HelloAssoNotAssociationProviderException
import org.commonlink.payment.PaymentProviderId
import org.commonlink.repository.AssociationProfileRepository
import org.commonlink.repository.HelloAssoConnectionRepository
import org.commonlink.repository.TestFixtures
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate
import java.time.Duration
import java.time.Instant
import java.util.Optional
import java.util.UUID

/** One reminder at D+1, one at D+7, none twice, none when nothing is pending. */
class HelloAssoOnboardingReminderServiceTest {

    private val associations = mockk<AssociationProfileRepository>()
    private val repo = mockk<HelloAssoConnectionRepository>()
    private val email = mockk<EmailService>(relaxed = true)
    private val txManager = mockk<PlatformTransactionManager>(relaxed = true)
    private val service = HelloAssoOnboardingReminderService(
        associations, repo, HelloAssoConnectionRows(repo), email, "https://app.test", TransactionTemplate(txManager),
    )

    private val userId = UUID.randomUUID()
    private val associationId = UUID.randomUUID()
    private lateinit var association: AssociationProfile
    private lateinit var row: HelloAssoConnection

    @BeforeEach
    fun setUp() {
        association = AssociationProfile(
            id = associationId, user = TestFixtures.associationUser(), name = "Techno", identifier = "W751121684",
            contactEmail = "contact@techno.org", paymentProvider = PaymentProviderId.HELLOASSO,
        )
        row = HelloAssoConnection(association = association, webhookToken = "t")
        every { associations.findByUserId(userId) } returns Optional.of(association)
        every { repo.findByAssociationId(associationId) } returns row
        every { repo.findByAssociationIdForUpdate(associationId) } returns row
        every { repo.save(any()) } answers { firstArg() }
        every { repo.markOnboardingStarted(associationId, any()) } answers {
            if (row.onboardingStartedAt != null) 0 else { row.onboardingStartedAt = secondArg(); 1 }
        }
        every { repo.existsPendingHelloAssoReminder() } returns true
        every { repo.findPendingHelloAssoReminders() } answers { if (row.reminderJ7SentAt == null) listOf(row) else emptyList() }
    }

    private fun startedAgo(d: Duration) { row.onboardingStartedAt = Instant.now().minus(d) }

    @Test
    fun `marking the start is idempotent and reserved to HELLOASSO associations`() {
        service.markStarted(userId)
        val first = row.onboardingStartedAt
        assertNotNull(first)
        service.markStarted(userId)
        assertEquals(first, row.onboardingStartedAt)

        association.paymentProvider = PaymentProviderId.MOLLIE
        assertThrows<HelloAssoNotAssociationProviderException> { service.markStarted(userId) }
    }

    @Test
    fun `marking the start writes only its timestamp, never the whole row`() {
        service.markStarted(userId)

        verify { repo.markOnboardingStarted(associationId, any()) }
        verify(exactly = 0) { repo.save(any()) }
    }

    @Test
    fun `a sent reminder is recorded under the row lock inside a transaction (no self-invocation bypass)`() {
        startedAgo(Duration.ofHours(25))

        service.sendDueReminders()

        verifyOrder {
            txManager.getTransaction(any())
            repo.findByAssociationIdForUpdate(associationId)
            repo.save(row)
            txManager.commit(any())
        }
        assertNotNull(row.reminderJ1SentAt)
    }

    @Test
    fun `nothing before D+1`() {
        startedAgo(Duration.ofHours(20))
        service.sendDueReminders()
        verify(exactly = 0) { email.sendHelloAssoOnboardingReminder(any(), any(), any(), any()) }
    }

    @Test
    fun `one reminder at D+1, then one at D+7, each once`() {
        startedAgo(Duration.ofHours(25))
        service.sendDueReminders()
        service.sendDueReminders()
        verify(exactly = 1) {
            email.sendHelloAssoOnboardingReminder("Techno", "contact@techno.org", "https://app.test/fr/dashboard/association/profile?tab=bank", false)
        }

        startedAgo(Duration.ofDays(8))
        service.sendDueReminders()
        service.sendDueReminders()
        verify(exactly = 1) { email.sendHelloAssoOnboardingReminder(any(), any(), any(), true) }
    }

    @Test
    fun `a missed D+1 is superseded by the D+7 reminder - never two emails at once`() {
        startedAgo(Duration.ofDays(8))
        service.sendDueReminders()
        verify(exactly = 1) { email.sendHelloAssoOnboardingReminder(any(), any(), any(), true) }
        verify(exactly = 0) { email.sendHelloAssoOnboardingReminder(any(), any(), any(), false) }
    }

    @Test
    fun `no pending reminder - exits after one query`() {
        every { repo.existsPendingHelloAssoReminder() } returns false
        service.sendDueReminders()
        verify(exactly = 0) { repo.findPendingHelloAssoReminders() }
    }
}
