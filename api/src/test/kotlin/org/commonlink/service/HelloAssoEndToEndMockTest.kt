package org.commonlink.service

import com.ninjasquad.springmockk.MockkBean
import io.mockk.every
import org.commonlink.controller.HelloAssoMockController
import org.commonlink.dto.CreateGuestDonationRequest
import org.commonlink.dto.HelloAssoConnectRequest
import org.commonlink.dto.HelloAssoMandateDto
import org.commonlink.dto.HelloAssoVerifyRequest
import org.commonlink.entity.CampaignStatus
import org.commonlink.entity.HelloAssoConnectionState
import org.commonlink.entity.LegalDocument
import org.commonlink.entity.LegalDocumentType
import org.commonlink.payment.BankSetupStatus
import org.commonlink.payment.PaymentProviderId
import org.commonlink.repository.AssociationProfileRepository
import org.commonlink.repository.CampaignRepository
import org.commonlink.repository.DonationRepository
import org.commonlink.repository.HelloAssoConnectionRepository
import org.commonlink.repository.LegalDocumentRepository
import org.commonlink.repository.TestFixtures
import org.commonlink.repository.TestcontainersConfig
import org.commonlink.repository.UserRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.testcontainers.context.ImportTestcontainers
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.TestPropertySource
import org.springframework.test.context.jdbc.Sql
import java.math.BigDecimal
import java.time.Duration
import java.time.Instant

/**
 * Whole HelloAsso journey in mock mode (S6-12), on the full Spring context and the fake HelloAsso
 * ([FakeHelloAssoGateway]): notification URL → key verification → link with mandate → eligibility → checkout of a real
 * donation → HelloAsso notification → donation confirmed with its payment means (which the receipt prints) →
 * disconnection.
 *
 * The receipt itself is out of reach here: its pipeline (`DonationReceiptService`, on-chain worker) is not loaded under
 * the test profile. The receipt wording of HelloAsso payment means is covered by `ReceiptServiceTest`.
 *
 * Not transactional: the notification is processed asynchronously and must see committed rows. Data is unique per
 * run. Runs on the test profile's database (H2 in PostgreSQL mode, schema by Hibernate), hence the audit-log lock
 * script. The sandbox scenarios of flow §12 are run by hand and recorded in the flow, never simulated here.
 */
@Tag("testcontainers")
@SpringBootTest
@Sql(scripts = ["/sql/compliance_audit_log_test_schema.sql"], executionPhase = Sql.ExecutionPhase.BEFORE_TEST_CLASS)
@ImportTestcontainers(TestcontainersConfig::class)
@ActiveProfiles("test")
@TestPropertySource(properties = [
    "app.jwt.secret=test-secret-key-must-be-at-least-32-chars!!",
    "app.frontend-url=http://localhost:3000",
    "app.vop.demo-mode=true",
    "app.helloasso.mock=true",
    "app.helloasso.webhook-url=https://api.common-link.test/api/public/webhooks/helloasso",
])
class HelloAssoEndToEndMockTest {

    @Autowired private lateinit var userRepository: UserRepository
    @Autowired private lateinit var associationProfileRepository: AssociationProfileRepository
    @Autowired private lateinit var campaignRepository: CampaignRepository
    @Autowired private lateinit var donationRepository: DonationRepository
    @Autowired private lateinit var connectionRepository: HelloAssoConnectionRepository
    @Autowired private lateinit var legalDocumentRepository: LegalDocumentRepository
    @Autowired private lateinit var credentialsService: HelloAssoCredentialsService
    @Autowired private lateinit var linkService: HelloAssoLinkService
    @Autowired private lateinit var eligibilityService: HelloAssoEligibilityService
    @Autowired private lateinit var associationPaymentService: AssociationPaymentService
    @Autowired private lateinit var publicWidgetService: PublicWidgetService
    @Autowired private lateinit var mockController: HelloAssoMockController
    @Autowired private lateinit var fake: FakeHelloAssoGateway

    @MockkBean private lateinit var freezeScreeningDonationService: FreezeScreeningDonationService

    private fun <T> awaitNotNull(timeout: Duration = Duration.ofSeconds(15), read: () -> T?): T {
        val deadline = Instant.now().plus(timeout)
        while (Instant.now().isBefore(deadline)) {
            read()?.let { return it }
            Thread.sleep(200)
        }
        return read() ?: throw AssertionError("condition not met within $timeout")
    }

    @Test
    fun `verification, link, eligibility, checkout, notification and confirmation in mock mode`() {
        every { freezeScreeningDonationService.runFreezeCheck(any(), any(), any()) } returns ScreeningOutcome.CLEAR
        for (type in listOf(LegalDocumentType.CGU, LegalDocumentType.CGV)) {
            if (legalDocumentRepository.findTopByDocumentTypeOrderByPublishedAtDesc(type) == null) {
                legalDocumentRepository.save(LegalDocument(documentType = type, version = "test", content = "test $type"))
            }
        }
        val rna = "W" + (100000000 + (System.nanoTime() % 899999999)).toString().padStart(9, '0').takeLast(9)
        val user = userRepository.save(TestFixtures.associationUser(email = "ha-e2e-${System.nanoTime()}@example.com"))
        val association = associationProfileRepository.save(
            TestFixtures.associationProfile(user, identifier = rna).apply { paymentProvider = PaymentProviderId.HELLOASSO }
        )
        val userId = user.id!!

        // 1. Notification URL, then key check (mock key carrying the association's RNA).
        val url = credentialsService.ensureNotificationUrl(userId).notificationUrl
        val token = url.substringAfterLast('/')
        val verification = credentialsService.verify(userId, HelloAssoVerifyRequest("mock:$rna", "any-secret"))
        assertTrue(verification.matchesKyb)

        // 2. Link with the mandate: pending until HelloAsso validates the organization.
        linkService.connect(userId, HelloAssoConnectRequest(verification.verifyId!!, HelloAssoMandateDto(true, HelloAssoLinkService.MANDATE_VERSION)))
        assertEquals(BankSetupStatus.IN_REVIEW, associationPaymentService.getPaymentStatus(userId).status)

        // 3. HelloAsso validates the organization; "Vérifier mon dossier" reads it.
        mockController.setCashIn(verification.slug, true)
        eligibilityService.requestRefresh(userId)
        val status = associationPaymentService.getPaymentStatus(userId)
        assertEquals(BankSetupStatus.COMPLETED, status.status)
        assertEquals(url, status.helloasso!!.notificationUrl)

        // 4. A real donation through the public widget.
        val campaign = campaignRepository.save(TestFixtures.campaign(association, status = CampaignStatus.LIVE))
        val widgetToken = "clk_ha_${System.nanoTime()}"
        association.widgetToken = widgetToken
        association.widgetDestinationCampaign = campaign
        associationProfileRepository.save(association)
        assertTrue(publicWidgetService.getWidget(widgetToken).collectAvailable)
        val donation = publicWidgetService.createDonation(
            widgetToken,
            CreateGuestDonationRequest(
                amount = BigDecimal("25.00"), donorEmail = "donor-${System.nanoTime()}@example.com", donorFullName = "Jean Dupont",
                donorAddressLine1 = "12 rue de la Paix", donorPostalCode = "75001", donorCity = "Paris", donorCountry = "FR",
                consent = true, cguAccepted = true, cgvAccepted = true,
            ),
        )
        assertTrue(donation.checkoutUrl.contains("code=succeeded"))
        val pending = donationRepository.findByProviderRef("helloasso:${donation.checkoutUrl.substringAfter("checkoutIntentId=").substringBefore('&')}")
        assertNotNull(pending)
        assertEquals(null, pending!!.confirmedAt)

        // 5. HelloAsso notifies; the payment is re-read and the donation confirmed with its payment means.
        val checkoutIntentId = pending.providerRef.removePrefix("helloasso:").toLong()
        assertEquals(HelloAssoNotificationOutcome.PAYMENT, mockController.notifyOrder(token, checkoutIntentId).body)
        val confirmed = awaitNotNull { donationRepository.findByProviderRef(pending.providerRef)?.takeIf { it.confirmedAt != null } }
        assertEquals("Card", confirmed.paymentMethod)
        assertNotNull(connectionRepository.findByAssociationId(association.id!!)!!.webhookLastReceivedAt)

        // 6. Disconnection: donations pause through the readiness gate, the notification token survives.
        linkService.disconnect(userId)
        val after = connectionRepository.findByAssociationId(association.id!!)!!
        assertEquals(HelloAssoConnectionState.DISCONNECTED, after.state)
        assertEquals(token, after.webhookToken)
        assertEquals(false, publicWidgetService.getWidget(widgetToken).collectAvailable)
    }
}
