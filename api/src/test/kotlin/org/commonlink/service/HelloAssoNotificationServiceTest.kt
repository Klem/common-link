package org.commonlink.service

import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import com.fasterxml.jackson.databind.ObjectMapper
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.commonlink.config.HelloAssoProperties
import org.commonlink.entity.AssociationProfile
import org.commonlink.entity.Campaign
import org.commonlink.entity.Donation
import org.commonlink.entity.HelloAssoConnection
import org.commonlink.entity.HelloAssoConnectionState
import org.commonlink.repository.DonationRepository
import org.commonlink.repository.HelloAssoConnectionRepository
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.slf4j.LoggerFactory
import java.util.UUID

/** Filtering first: foreign events leave no trace; ours and organization events are scheduled for a re-read. */
class HelloAssoNotificationServiceTest {

    private val connectionRepo = mockk<HelloAssoConnectionRepository>()
    private val donationRepo = mockk<DonationRepository>()
    private val processor = mockk<HelloAssoNotificationProcessor>(relaxed = true)
    private val service = HelloAssoNotificationService(connectionRepo, donationRepo, ObjectMapper(), processor, HelloAssoProperties())

    private val associationId = UUID.randomUUID()
    private val association = mockk<AssociationProfile> { every { id } returns associationId }
    private val connection = HelloAssoConnection(association = association, webhookToken = "tok", state = HelloAssoConnectionState.ACTIVE)
    private val publicRef = UUID.randomUUID()
    private lateinit var appender: ListAppender<ILoggingEvent>
    private val rootLogger = LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME) as Logger

    /** A foreign membership payment: personal data that must never be logged nor stored. */
    private val foreignBody = """{"eventType":"Payment","data":{"id":991,"state":"Authorized","payer":{"email":"member@private.org",""" +
        """"firstName":"Secret"},"order":{"formType":"Membership"}},"metadata":null}"""

    private fun donation(owner: UUID = associationId, ref: String = "helloasso:4242") = mockk<Donation> {
        every { id } returns UUID.randomUUID()
        every { providerRef } returns ref
        every { campaign } returns mockk<Campaign> {
            every { this@mockk.association } returns mockk { every { id } returns owner }
        }
    }

    @BeforeEach
    fun setUp() {
        every { connectionRepo.findByWebhookToken("tok") } returns connection
        every { connectionRepo.save(any()) } answers { firstArg() }
        every { donationRepo.findByPublicRef(any()) } returns null
        every { donationRepo.findByProviderRef(any()) } returns null
        appender = ListAppender<ILoggingEvent>().apply { start() }
        rootLogger.addAppender(appender)
    }

    @AfterEach
    fun tearDown() {
        rootLogger.detachAppender(appender)
    }

    @Test
    fun `a foreign event is ignored without logging its body, only the reception time is kept`() {
        val outcome = service.receive("tok", foreignBody)

        assertEquals(HelloAssoNotificationOutcome.IGNORED, outcome)
        assertNotNull(connection.webhookLastReceivedAt)
        verify(exactly = 0) { processor.refreshPayment(any()) }
        verify(exactly = 0) { donationRepo.save(any()) }
        val logs = appender.list.joinToString("\n") { it.formattedMessage }
        assertFalse(logs.contains("member@private.org"))
        assertFalse(logs.contains("Secret"))
        assertFalse(logs.contains("991"))
    }

    @Test
    fun `an Order of one of our checkout-intents schedules its re-read`() {
        every { donationRepo.findByProviderRef("helloasso:4242") } returns donation()

        val outcome = service.receive("tok", """{"eventType":"Order","data":{"id":1,"checkoutIntentId":4242}}""")

        assertEquals(HelloAssoNotificationOutcome.PAYMENT, outcome)
        verify { processor.refreshPayment("helloasso:4242") }
    }

    @Test
    fun `a Payment carrying our publicRef schedules its re-read`() {
        every { donationRepo.findByPublicRef(publicRef) } returns donation()

        val outcome = service.receive("tok", """{"eventType":"Payment","data":{"id":7},"metadata":{"publicRef":"$publicRef"}}""")

        assertEquals(HelloAssoNotificationOutcome.PAYMENT, outcome)
        verify { processor.refreshPayment("helloasso:4242") }
    }

    @Test
    fun `another association's or a Mollie donation is not ours`() {
        every { donationRepo.findByProviderRef("helloasso:4242") } returns donation(owner = UUID.randomUUID())
        every { donationRepo.findByPublicRef(publicRef) } returns donation(ref = "mollie:tr_1")

        assertEquals(HelloAssoNotificationOutcome.IGNORED, service.receive("tok", """{"eventType":"Order","data":{"checkoutIntentId":4242}}"""))
        assertEquals(
            HelloAssoNotificationOutcome.IGNORED,
            service.receive("tok", """{"eventType":"Payment","metadata":{"publicRef":"$publicRef"}}"""),
        )
        verify(exactly = 0) { processor.refreshPayment(any()) }
    }

    @Test
    fun `an organization event schedules an eligibility re-read, never trusting its content`() {
        val outcome = service.receive(
            "tok", """{"eventType":"Organization.IsCashinCompliant","data":{"organization_slug":"x","is_cashin_compliant":true}}""",
        )

        assertEquals(HelloAssoNotificationOutcome.ORGANIZATION, outcome)
        verify { processor.refreshEligibility(associationId) }
    }

    @Test
    fun `an unknown token reads and writes nothing`() {
        every { connectionRepo.findByWebhookToken("nope") } returns null

        assertEquals(HelloAssoNotificationOutcome.UNKNOWN_TOKEN, service.receive("nope", foreignBody))
        verify(exactly = 0) { connectionRepo.save(any()) }
    }

    @Test
    fun `an unreadable body is ignored`() {
        assertEquals(HelloAssoNotificationOutcome.IGNORED, service.receive("tok", "not json"))
    }

    @Test
    fun `only HelloAsso's notification IPs are allowed`() {
        assertTrue(service.isAllowedSource("4.233.135.234"))
        assertFalse(service.isAllowedSource("203.0.113.9"))
    }
}
