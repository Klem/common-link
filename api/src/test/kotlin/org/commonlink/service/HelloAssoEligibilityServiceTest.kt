package org.commonlink.service

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.commonlink.config.HelloAssoProperties
import org.commonlink.dto.HelloAssoOrganizationJson
import org.commonlink.dto.HelloAssoOrganizationLightJson
import org.commonlink.entity.AssociationProfile
import org.commonlink.entity.HelloAssoConnection
import org.commonlink.entity.HelloAssoConnectionState
import org.commonlink.exception.HelloAssoNotLinkedException
import org.commonlink.exception.HelloAssoUnauthorizedException
import org.commonlink.exception.RateLimitException
import org.commonlink.payment.PaymentProviderId
import org.commonlink.repository.AssociationProfileRepository
import org.commonlink.repository.HelloAssoConnectionRepository
import org.commonlink.repository.TestFixtures
import org.commonlink.security.AuthRateLimiter
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.util.Optional
import java.util.UUID

/** Eligibility reads: stored with a sticky everCompliant, BROKEN on a rejected token, rate-limited on request. */
class HelloAssoEligibilityServiceTest {

    private val repo = mockk<HelloAssoConnectionRepository>()
    private val associations = mockk<AssociationProfileRepository>()
    private val tokenManager = mockk<HelloAssoTokenManager>()
    private val gateway = mockk<HelloAssoGateway>()
    private val breaker = mockk<HelloAssoConnectionBreaker>(relaxed = true)
    private val service = HelloAssoEligibilityService(
        repo, associations, tokenManager, gateway, breaker, HelloAssoConnectionRows(repo), AuthRateLimiter(), HelloAssoProperties(),
    )

    private val userId = UUID.randomUUID()
    private val associationId = UUID.randomUUID()
    private lateinit var row: HelloAssoConnection

    @BeforeEach
    fun setUp() {
        val association = AssociationProfile(
            id = associationId, user = TestFixtures.associationUser(), name = "Techno", identifier = "W751121684",
            paymentProvider = PaymentProviderId.HELLOASSO,
        )
        row = HelloAssoConnection(association = association, webhookToken = "t", state = HelloAssoConnectionState.ACTIVE, organizationSlug = "techno")
        every { associations.findByUserId(userId) } returns Optional.of(association)
        every { repo.findByAssociationId(associationId) } returns row
        every { repo.findByAssociationIdForUpdate(associationId) } returns row
        every { repo.save(any()) } answers { firstArg() }
        every { tokenManager.getValidAccessToken(associationId) } returns "access"
        every { gateway.listOrganizations("access") } returns listOf(HelloAssoOrganizationLightJson("techno", "Techno"))
    }

    @Test
    fun `an eligible read sets canCollect and everCompliant, a later non-eligible one keeps everCompliant`() {
        every { gateway.getOrganization("access", "techno") } returns HelloAssoOrganizationJson(cashInCompliant = true)
        assertTrue(service.refresh(associationId))
        assertTrue(row.canCollect)
        assertTrue(row.everCompliant)
        assertNotNull(row.lastEligibilityCheckAt)

        every { gateway.getOrganization("access", "techno") } returns HelloAssoOrganizationJson(cashInCompliant = false)
        service.refresh(associationId)
        assertFalse(row.canCollect)
        assertTrue(row.everCompliant)
    }

    @Test
    fun `a renamed organization gets its new slug stored, and stays eligible`() {
        every { gateway.listOrganizations("access") } returns listOf(HelloAssoOrganizationLightJson("techno-renamed", "Techno Plus"))
        every { gateway.getOrganization("access", "techno-renamed") } returns HelloAssoOrganizationJson(cashInCompliant = true)

        assertTrue(service.refresh(associationId))
        assertEquals("techno-renamed", row.organizationSlug)
        assertTrue(row.canCollect)
    }

    @Test
    fun `a rejected token marks the link BROKEN and never throws`() {
        every { tokenManager.getValidAccessToken(associationId) } throws HelloAssoUnauthorizedException("refresh_token", 400)

        assertFalse(service.refresh(associationId))
        verify { breaker.markBroken(associationId) }
    }

    @Test
    fun `nothing is read for a link that is not ACTIVE`() {
        row.state = HelloAssoConnectionState.DISCONNECTED

        assertFalse(service.refresh(associationId))
        verify(exactly = 0) { gateway.getOrganization(any(), any()) }
    }

    @Test
    fun `the association can ask once a minute, only when linked`() {
        every { gateway.getOrganization(any(), any()) } returns HelloAssoOrganizationJson(cashInCompliant = false)
        service.requestRefresh(userId)
        assertThrows<RateLimitException> { service.requestRefresh(userId) }

        row.state = HelloAssoConnectionState.NOT_LINKED
        assertThrows<HelloAssoNotLinkedException> { service.requestRefresh(userId) }
    }

    @Test
    fun `the scheduled sweep exits at once without an active HelloAsso link`() {
        every { repo.existsActiveForHelloAssoAssociations() } returns false

        HelloAssoEligibilityExecutor(repo, service, HelloAssoProperties()).sweep()

        verify(exactly = 0) { repo.findActiveHelloAssoEligibilityCandidates(any(), any()) }
    }
}
