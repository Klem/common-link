package org.commonlink.payment

import io.mockk.every
import io.mockk.mockk
import org.commonlink.entity.AssociationProfile
import org.commonlink.entity.HelloAssoConnection
import org.commonlink.entity.HelloAssoConnectionState
import org.commonlink.repository.HelloAssoConnectionRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID

/** Flow §10.5 mapping, case by case, from the database only. */
class HelloAssoProviderAdapterTest {

    private val repo = mockk<HelloAssoConnectionRepository>()
    private val adapter = HelloAssoProviderAdapter(repo, mockk(relaxed = true), mockk(relaxed = true))
    private val id = UUID.randomUUID()

    private fun row(
        state: HelloAssoConnectionState,
        canCollect: Boolean = false,
        everCompliant: Boolean = false,
        received: Instant? = null,
        missing: Instant? = null,
    ) = HelloAssoConnection(
        association = mockk<AssociationProfile>(relaxed = true), webhookToken = "t", state = state,
        organizationSlug = "techno-plus", canCollect = canCollect, everCompliant = everCompliant,
        webhookLastReceivedAt = received, webhookMissingAt = missing,
    )

    private fun given(row: HelloAssoConnection?) = every { repo.findByAssociationId(id) } returns row

    @Test
    fun `no row, NOT_LINKED or DISCONNECTED - not connected`() {
        for (r in listOf(null, row(HelloAssoConnectionState.NOT_LINKED), row(HelloAssoConnectionState.DISCONNECTED))) {
            given(r)
            assertEquals(ProviderReadinessStatus.NOT_CONNECTED, adapter.readiness(id).status)
            assertEquals(BankSetupStatus.NOT_CONNECTED, adapter.paymentStatus(id).status)
        }
    }

    @Test
    fun `BROKEN - broken`() {
        given(row(HelloAssoConnectionState.BROKEN, everCompliant = true))
        assertEquals(ProviderReadinessStatus.BROKEN, adapter.readiness(id).status)
        assertEquals(BankSetupStatus.BROKEN, adapter.paymentStatus(id).status)
    }

    @Test
    fun `LINKED_PENDING - in review, not ready`() {
        given(row(HelloAssoConnectionState.ACTIVE))
        assertEquals(ProviderReadinessStatus.PENDING, adapter.readiness(id).status)
        assertEquals(BankSetupStatus.IN_REVIEW, adapter.paymentStatus(id).status)
    }

    @Test
    fun `SUSPENDED - needs data with the HelloAsso verification page`() {
        given(row(HelloAssoConnectionState.ACTIVE, everCompliant = true))
        val status = adapter.paymentStatus(id)
        assertEquals(BankSetupStatus.NEEDS_DATA, status.status)
        assertEquals("https://admin.helloasso.com/techno-plus/verification", status.actionUrl)
        assertEquals(ProviderReadinessStatus.PENDING, adapter.readiness(id).status)
    }

    @Test
    fun `COLLECT_READY - completed and ready`() {
        given(row(HelloAssoConnectionState.ACTIVE, canCollect = true, everCompliant = true))
        assertEquals(ProviderReadinessStatus.READY, adapter.readiness(id).status)
        assertEquals(BankSetupStatus.COMPLETED, adapter.paymentStatus(id).status)
        assertEquals(emptyList<String>(), adapter.paymentStatus(id).warnings)
    }

    @Test
    fun `a missed notification not followed by a received one warns WEBHOOK_MISSING without blocking`() {
        val t = Instant.parse("2026-11-02T10:00:00Z")
        given(row(HelloAssoConnectionState.ACTIVE, canCollect = true, received = t, missing = t.plusSeconds(60)))
        assertEquals(BankSetupStatus.COMPLETED, adapter.paymentStatus(id).status)
        assertEquals(listOf(HelloAssoProviderAdapter.WEBHOOK_MISSING), adapter.paymentStatus(id).warnings)
        assertEquals(ProviderReadinessStatus.READY, adapter.readiness(id).status)

        given(row(HelloAssoConnectionState.ACTIVE, canCollect = true, received = t.plusSeconds(120), missing = t))
        assertEquals(emptyList<String>(), adapter.paymentStatus(id).warnings)
    }

    @Test
    fun `webhook state - unknown, received, missing`() {
        val t = Instant.now()
        assertEquals(HelloAssoWebhookState.UNKNOWN, HelloAssoWebhookState.of(row(HelloAssoConnectionState.ACTIVE)))
        assertEquals(HelloAssoWebhookState.RECEIVED, HelloAssoWebhookState.of(row(HelloAssoConnectionState.ACTIVE, received = t)))
        assertEquals(HelloAssoWebhookState.MISSING, HelloAssoWebhookState.of(row(HelloAssoConnectionState.ACTIVE, missing = t)))
    }

    @Test
    fun `no contact data to lock`() {
        assertFalse(adapter.isLinkStarted(id))
    }
}
