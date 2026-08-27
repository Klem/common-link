package org.commonlink

import com.fasterxml.jackson.databind.ObjectMapper
import com.ninjasquad.springmockk.MockkBean
import org.commonlink.repository.AssociationProfileRepository
import org.commonlink.repository.AssociationRegistryCheckRepository
import org.commonlink.repository.BeneficialOwnerRepository
import org.commonlink.repository.DonorProfileRepository
import org.commonlink.repository.FreezeScreeningMatchRepository
import org.commonlink.repository.UserRepository
import org.commonlink.security.AuthRateLimiter
import org.commonlink.security.ClientIpResolver
import org.commonlink.security.JwtService
import org.commonlink.security.RefreshCookieFactory
import org.commonlink.security.SecurityConfig
import org.commonlink.security.UserDetailsServiceImpl
import org.commonlink.service.AssociationComplianceStatusService
import org.commonlink.service.AssociationDashboardService
import org.commonlink.service.AssociationLandingService
import org.commonlink.service.CampaignReportService
import org.commonlink.service.LegalAcceptanceService
import org.commonlink.service.MollieWebhookService
import org.commonlink.service.PublicWidgetService
import org.commonlink.service.AssociationRegistryCheckService
import org.commonlink.service.AssociationService
import org.commonlink.service.BeneficialOwnerService
import org.commonlink.service.AuthService
import org.commonlink.service.VerificationService
import org.commonlink.service.PayeeService
import org.commonlink.service.CampaignService
import org.commonlink.service.DonorAggregateService
import org.commonlink.service.DonorService
import org.commonlink.service.MandatePdfService
import org.commonlink.service.MandateService
import org.commonlink.service.MollieConnectService
import org.commonlink.service.MollieConnectTokenManager
import org.commonlink.service.ComplianceAlertService
import org.commonlink.service.ComplianceAssociationService
import org.commonlink.service.ComplianceAuditLogService
import org.commonlink.service.FreezeScreeningOnboardingService
import org.commonlink.service.OnboardingGateService
import org.commonlink.service.OnchainOutboxService
import org.commonlink.service.PayoutService
import org.commonlink.service.ReportingService
import org.commonlink.service.SireneSearchService
import org.junit.jupiter.api.Test
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.test.context.TestPropertySource

/**
 * Smoke test — verifies the web layer + security config load correctly.
 * Uses @WebMvcTest to avoid needing Docker/Testcontainers.
 */
@WebMvcTest
@Import(SecurityConfig::class, ClientIpResolver::class, RefreshCookieFactory::class)
@TestPropertySource(properties = [
    "app.jwt.secret=test-secret-key-must-be-at-least-32-chars!!",
    "app.frontend-url=http://localhost:3000"
])
class CommonLinkApplicationTests {

    @MockkBean lateinit var authService: AuthService
    @MockkBean lateinit var jwtService: JwtService
    @MockkBean lateinit var userDetailsService: UserDetailsServiceImpl
    @MockkBean lateinit var userRepository: UserRepository
    @MockkBean lateinit var associationService: AssociationService
    @MockkBean lateinit var donorService: DonorService
    @MockkBean lateinit var payeeService: PayeeService
    @MockkBean private lateinit var campaignService: CampaignService
    @MockkBean private lateinit var sireneSearchService: SireneSearchService
    @MockkBean private lateinit var onchainOutboxService: OnchainOutboxService
    @MockkBean private lateinit var dashboardService: AssociationDashboardService
    @MockkBean private lateinit var associationLandingService: AssociationLandingService
    @MockkBean private lateinit var authRateLimiter: AuthRateLimiter
    @MockkBean private lateinit var donorAggregateService: DonorAggregateService
    @MockkBean private lateinit var payoutService: PayoutService
    @MockkBean private lateinit var reportingService: ReportingService
    @MockkBean private lateinit var verificationService: VerificationService
    @MockkBean private lateinit var mandateService: MandateService
    @MockkBean private lateinit var mandatePdfService: MandatePdfService
    @MockkBean private lateinit var associationRegistryCheckService: AssociationRegistryCheckService
    @MockkBean private lateinit var beneficialOwnerService: BeneficialOwnerService
    @MockkBean private lateinit var publicWidgetService: PublicWidgetService
    @MockkBean private lateinit var mollieWebhookService: MollieWebhookService
    @MockkBean private lateinit var mollieConnectService: MollieConnectService
    @MockkBean private lateinit var mollieConnectTokenManager: MollieConnectTokenManager
    @MockkBean private lateinit var freezeScreeningOnboardingService: FreezeScreeningOnboardingService
    @MockkBean private lateinit var onboardingGateService: OnboardingGateService
    @MockkBean private lateinit var complianceAlertService: ComplianceAlertService
    @MockkBean private lateinit var complianceAssociationService: ComplianceAssociationService
    @MockkBean private lateinit var complianceAuditLogService: ComplianceAuditLogService
    @MockkBean private lateinit var associationRegistryCheckRepository: AssociationRegistryCheckRepository
    @MockkBean private lateinit var associationProfileRepository: AssociationProfileRepository
    @MockkBean private lateinit var beneficialOwnerRepository: BeneficialOwnerRepository
    @MockkBean private lateinit var donorProfileRepository: DonorProfileRepository
    @MockkBean private lateinit var freezeScreeningMatchRepository: FreezeScreeningMatchRepository
    @MockkBean private lateinit var associationComplianceStatusService: AssociationComplianceStatusService
    @MockkBean private lateinit var campaignReportService: CampaignReportService
    @MockkBean private lateinit var legalAcceptanceService: LegalAcceptanceService
    @MockkBean private lateinit var objectMapper: ObjectMapper

    @Test
    fun contextLoads() {
    }
}
