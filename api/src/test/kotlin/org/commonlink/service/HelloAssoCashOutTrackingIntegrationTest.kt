package org.commonlink.service

import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.commonlink.config.HelloAssoProperties
import org.commonlink.config.PaymentsProperties
import org.commonlink.entity.CampaignStatus
import org.commonlink.entity.Donation
import org.commonlink.entity.FundsState
import org.commonlink.entity.HelloAssoConnection
import org.commonlink.entity.HelloAssoConnectionState
import org.commonlink.entity.HelloAssoDonationFunds
import org.commonlink.entity.ReceiptConfirmationMode
import org.commonlink.repository.AssociationProfileRepository
import org.commonlink.repository.CampaignRepository
import org.commonlink.repository.DonationRepository
import org.commonlink.repository.DonorProfileRepository
import org.commonlink.repository.HelloAssoCashOutSyncRepository
import org.commonlink.repository.HelloAssoConnectionRepository
import org.commonlink.repository.HelloAssoDonationFundsRepository
import org.commonlink.repository.RealPostgresTest
import org.commonlink.repository.TestFixtures
import org.commonlink.repository.UserRepository
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.MediaType
import org.springframework.test.web.client.ExpectedCount
import org.springframework.test.web.client.MockRestServiceServer
import org.springframework.test.web.client.match.MockRestRequestMatchers.header
import org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo
import org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess
import org.springframework.transaction.support.TransactionTemplate
import org.springframework.web.client.RestClient
import java.math.BigDecimal
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID

/**
 * Cash-out tracking end to end on real PostgreSQL (MD3 acceptance): the real [HelloAssoClient], bound to a
 * [MockRestServiceServer] serving the HelloAsso fixtures, the real repositories and journal, a controlled clock.
 * Only the access token is stubbed (the token flow has its own tests). Not transactional: every write commits, as in
 * production.
 */
class HelloAssoCashOutTrackingIntegrationTest : RealPostgresTest() {

    @Autowired private lateinit var fundsRepository: HelloAssoDonationFundsRepository
    @Autowired private lateinit var syncRepository: HelloAssoCashOutSyncRepository
    @Autowired private lateinit var connectionRepository: HelloAssoConnectionRepository
    @Autowired private lateinit var breaker: HelloAssoConnectionBreaker
    @Autowired private lateinit var complianceAuditLogService: ComplianceAuditLogService
    @Autowired private lateinit var transactionTemplate: TransactionTemplate
    @Autowired private lateinit var donationRepository: DonationRepository
    @Autowired private lateinit var donorProfileRepository: DonorProfileRepository
    @Autowired private lateinit var campaignRepository: CampaignRepository
    @Autowired private lateinit var associationProfileRepository: AssociationProfileRepository
    @Autowired private lateinit var userRepository: UserRepository
    @Autowired private lateinit var allocationService: DonationAllocationService

    private val properties = HelloAssoProperties()
    private val api = properties.apiBaseUrl
    private lateinit var server: MockRestServiceServer
    private lateinit var executor: HelloAssoCashOutSyncExecutor
    private val alerts = mockk<TechnicalAlertService>(relaxed = true)
    private val appender = ListAppender<ILoggingEvent>()
    private val loggers = listOf(HelloAssoClient::class.java, HelloAssoCashOutSyncExecutor::class.java, HelloAssoDonationFundsService::class.java)
        .map { LoggerFactory.getLogger(it) as Logger }

    private lateinit var campaignId: UUID
    private lateinit var associationId: UUID
    private lateinit var helloAssoDonationId: UUID
    private lateinit var mollieDonationId: UUID

    @BeforeEach
    fun setUp() {
        fundsRepository.deleteAllInBatch()
        val builder = RestClient.builder()
        server = MockRestServiceServer.bindTo(builder).build()
        val client = HelloAssoClient(properties, builder, HelloAssoAuthRateLimiter())
        val tokenManager = mockk<HelloAssoTokenManager> { every { getValidAccessToken(any()) } returns "ACCESS-token" }
        executor = HelloAssoCashOutSyncExecutor(
            fundsRepository, syncRepository, connectionRepository, tokenManager, breaker, client,
            HelloAssoDonationFundsService(fundsRepository, alerts, complianceAuditLogService), alerts,
            PaymentsProperties(), transactionTemplate,
        )
        appender.start()
        loggers.forEach { it.addAppender(appender) }
        seed()
    }

    @AfterEach
    fun tearDown() {
        loggers.forEach { it.detachAppender(appender) }
    }

    private fun seed() {
        val tag = UUID.randomUUID().toString().take(8)
        val donor = donorProfileRepository.save(TestFixtures.donorProfile(userRepository.save(TestFixtures.donorUser(email = "d-$tag@example.com"))))
        val association = associationProfileRepository.save(
            TestFixtures.associationProfile(
                userRepository.save(TestFixtures.associationUser(email = "a-$tag@example.com")),
                identifier = "8${tag.hashCode().mod(100_000_000).toString().padStart(8, '0')}",
            ),
        )
        associationId = association.id!!
        connectionRepository.save(
            HelloAssoConnection(association = association, webhookToken = "wh-$tag", state = HelloAssoConnectionState.ACTIVE, organizationSlug = "asso-exemple"),
        )
        val campaign = campaignRepository.save(TestFixtures.campaign(association, status = CampaignStatus.LIVE))
        campaignId = campaign.id!!
        mollieDonationId = donationRepository.save(
            TestFixtures.donation(donor, campaign, amount = BigDecimal("40.00"), providerRef = "mollie:tr_$tag")
                .also { it.fundsAvailableAt = it.confirmedAt },
        ).id!!
        val helloAsso = donationRepository.save(
            TestFixtures.donation(donor, campaign, amount = BigDecimal("10.00"), providerRef = "helloasso:$tag"),
        )
        helloAssoDonationId = helloAsso.id!!
        fundsRepository.save(HelloAssoDonationFunds(donation = helloAsso, haPaymentId = 70001, cashOutState = "Transfered"))
    }

    private fun at(instant: String) {
        executor.clock = Clock.fixed(Instant.parse(instant), ZoneOffset.UTC)
    }

    private fun serve(fixture: String) {
        server.expect(ExpectedCount.once(), requestTo("$api/payments/70001"))
            .andExpect(header("Authorization", "Bearer ACCESS-token"))
            .andRespond(withSuccess(javaClass.getResource("/helloasso/cashout/$fixture")!!.readText(), MediaType.APPLICATION_JSON))
    }

    private fun row() = fundsRepository.findById(helloAssoDonationId).get()
    private fun donation(): Donation = donationRepository.findById(helloAssoDonationId).get()
    private fun available() = donationRepository.sumAvailableAmountByCampaignId(campaignId)!!

    @Test
    fun `Transfered then WaitingForCashOutConfirmation then CashedOut, available after the settlement delay`() {
        serve("payment-transfered.json")
        serve("payment-waiting-cashout.json")
        serve("payment-cashedout.json")
        serve("payment-cashedout.json")

        at("2026-11-10T08:00:00Z")
        executor.syncAssociation(associationId)
        assertEquals(FundsState.AT_PROVIDER, row().fundsState)

        at("2026-11-11T08:00:00Z")
        executor.syncAssociation(associationId)
        assertEquals(FundsState.PAYOUT_PREPARING, row().fundsState)

        // Paid out Thu 12/11 at 10:00 (Paris): + 2 TARGET2 business days = Mon 16/11.
        at("2026-11-13T08:00:00Z")
        executor.syncAssociation(associationId)
        assertEquals(FundsState.IN_TRANSIT, row().fundsState)
        assertEquals(Instant.parse("2026-11-12T09:00:00Z"), row().cashOutDate)
        assertEquals(60001L, row().idCashOut)
        assertNull(donation().fundsAvailableAt)
        assertEquals(0, BigDecimal("40.00").compareTo(available()))

        at("2026-11-16T07:30:00Z")
        val result = executor.syncAssociation(associationId)
        server.verify()

        assertEquals(FundsState.AVAILABLE, row().fundsState)
        assertEquals(ReceiptConfirmationMode.AUTO, row().receiptConfirmationMode)
        assertEquals(Instant.parse("2026-11-16T07:30:00Z"), donation().fundsAvailableAt)
        assertEquals(0, BigDecimal("50.00").compareTo(available()))
        // Append-only FIFO: the HelloAsso donation, available last, comes after the Mollie one.
        assertEquals(listOf(mollieDonationId, helloAssoDonationId), allocationService.allocateCampaign(campaignId).map { it.donationId })
        assertEquals(CashOutSyncResult(Instant.parse("2026-11-16T07:30:00Z"), null), result)
    }

    @Test
    fun `CashedOut then Refunded withdraws the donation, with a single alert`() {
        serve("payment-cashedout.json")
        serve("payment-refunded.json")

        at("2026-11-13T08:00:00Z")
        executor.syncAssociation(associationId)
        assertEquals(FundsState.IN_TRANSIT, row().fundsState)

        at("2026-11-14T08:00:00Z")
        executor.syncAssociation(associationId)
        executor.syncAssociation(associationId) // withdrawn: no longer read, no second alert
        server.verify()

        assertEquals(FundsState.WITHDRAWN, row().fundsState)
        assertNull(donation().fundsAvailableAt)
        verify(exactly = 1) { alerts.reportFailure(TechnicalAlertKind.PAYMENT_REVERSED, null, donation().providerRef, null) }
    }

    @Test
    fun `no donation to track sends no request at all`() {
        fundsRepository.deleteAllInBatch()

        at("2026-11-10T08:00:00Z")
        executor.syncAll()

        server.verify() // no expectation: any request would have failed the pass and this check
        assertFalse(syncRepository.existsById(associationId))
    }

    @Test
    fun `no HelloAsso response body reaches the logs`() {
        serve("payment-cashedout.json")
        serve("payment-refunded.json")

        at("2026-11-13T08:00:00Z")
        executor.syncAll()
        at("2026-11-14T08:00:00Z")
        executor.syncAll()

        val logs = appender.list.joinToString("\n") { it.formattedMessage }
        for (personal in listOf("donateur@example.org", "Exemple", "rue de l", "75000", "paiement-attestation", "StripeV2")) {
            assertFalse(logs.contains(personal), "logged '$personal' in: $logs")
        }
    }
}
