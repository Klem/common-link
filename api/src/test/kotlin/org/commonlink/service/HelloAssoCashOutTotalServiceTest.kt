package org.commonlink.service

import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.commonlink.config.CashOutExportAmountUnit
import org.commonlink.config.HelloAssoCashOutProperties
import org.commonlink.config.PaymentsProperties
import org.commonlink.entity.HelloAssoCashOut
import org.commonlink.entity.HelloAssoConnection
import org.commonlink.entity.HelloAssoConnectionState
import org.commonlink.exception.HelloAssoUnavailableException
import org.commonlink.repository.HelloAssoCashOutRepository
import org.commonlink.repository.HelloAssoConnectionRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate
import java.math.BigDecimal
import java.util.UUID

/** Net total of a HelloAsso payout: read once, one token per batch, unit conversion, never blocking, never on a plain read. */
class HelloAssoCashOutTotalServiceTest {

    private val repository = mockk<HelloAssoCashOutRepository>()
    private val connectionRepository = mockk<HelloAssoConnectionRepository>()
    private val tokenManager = mockk<HelloAssoTokenManager>()
    private val gateway = mockk<HelloAssoGateway>()
    private val association = UUID.randomUUID()
    private val stored = mutableMapOf<Long, BigDecimal>()

    private fun serviceWith(unit: CashOutExportAmountUnit = CashOutExportAmountUnit.EUROS) = HelloAssoCashOutTotalService(
        repository, connectionRepository, tokenManager, gateway,
        PaymentsProperties(helloasso = HelloAssoCashOutProperties(cashoutExportAmountUnit = unit)),
        TransactionTemplate(mockk<PlatformTransactionManager>(relaxed = true)),
    )

    init {
        every { repository.findByAssociationIdAndIdCashOut(association, any()) } answers {
            stored[secondArg()]?.let { HelloAssoCashOut(association, secondArg(), it) }
        }
        every { repository.insertIfMissing(association, any(), any(), any()) } answers {
            if (stored.containsKey(secondArg())) 0 else { stored[secondArg()] = thirdArg(); 1 }
        }
        every { connectionRepository.findByAssociationId(association) } returns connection(HelloAssoConnectionState.ACTIVE)
        every { tokenManager.getValidAccessToken(association) } returns "access"
    }

    private fun connection(state: HelloAssoConnectionState) =
        HelloAssoConnection(association = mockk(relaxed = true), webhookToken = "t", state = state, organizationSlug = "mon-asso")

    @Test
    fun `a stored total is read without any call`() {
        stored[1] = BigDecimal("12.00")
        val service = serviceWith()

        assertEquals(BigDecimal("12.00"), service.storedNetTotal(association, 1))
        assertNull(service.storedNetTotal(association, 2))
        verify(exactly = 0) { tokenManager.getValidAccessToken(any()) }
        verify(exactly = 0) { gateway.getCashOutNetTotal(any(), any(), any()) }
    }

    @Test
    fun `fetchMissing reads only the missing totals, with one token for all, and stores them in euros`() {
        stored[1] = BigDecimal("12.00")
        every { gateway.getCashOutNetTotal("access", "mon-asso", 2) } returns BigDecimal("30.0")
        every { gateway.getCashOutNetTotal("access", "mon-asso", 3) } returns BigDecimal("8")

        serviceWith().fetchMissing(association, listOf(1, 2, 3, 2))

        verify(exactly = 1) { tokenManager.getValidAccessToken(association) }
        verify(exactly = 0) { gateway.getCashOutNetTotal(any(), any(), 1) }
        assertEquals(mapOf(1L to BigDecimal("12.00"), 2L to BigDecimal("30.00"), 3L to BigDecimal("8.00")), stored)
    }

    @Test
    fun `nothing missing means no token and no call`() {
        stored[1] = BigDecimal("12.00")
        serviceWith().fetchMissing(association, listOf(1))
        verify(exactly = 0) { tokenManager.getValidAccessToken(any()) }
    }

    @Test
    fun `an export in cents is converted to euros`() {
        every { gateway.getCashOutNetTotal("access", "mon-asso", 1) } returns BigDecimal("3050")
        serviceWith(CashOutExportAmountUnit.CENTS).fetchMissing(association, listOf(1))
        assertEquals(BigDecimal("30.50"), stored[1])
    }

    @Test
    fun `a HelloAsso failure stores nothing, and the next call tries again`() {
        every { gateway.getCashOutNetTotal("access", "mon-asso", 2) } throws HelloAssoUnavailableException("GET", 404) andThen BigDecimal("12.5")
        val service = serviceWith()

        service.fetchMissing(association, listOf(2))
        assertEquals(emptyMap<Long, BigDecimal>(), stored)
        service.fetchMissing(association, listOf(2))
        assertEquals(BigDecimal("12.50"), stored[2])
    }

    @Test
    fun `without an active link nothing is called`() {
        every { connectionRepository.findByAssociationId(association) } returns connection(HelloAssoConnectionState.DISCONNECTED)

        serviceWith().fetchMissing(association, listOf(3))
        verify(exactly = 0) { tokenManager.getValidAccessToken(any()) }
        verify(exactly = 0) { gateway.getCashOutNetTotal(any(), any(), any()) }
    }

    @Test
    fun `a caller holding a token does not request another one, and only the total is written`() {
        every { gateway.getCashOutNetTotal("held", "mon-asso", 4) } returns BigDecimal("8")
        val total = slot<BigDecimal>()
        every { repository.insertIfMissing(association, 4, capture(total), any()) } answers { stored[4] = total.captured; 1 }

        assertEquals(BigDecimal("8.00"), serviceWith().netTotalOf(association, 4, "held", "mon-asso"))
        verify(exactly = 0) { tokenManager.getValidAccessToken(any()) }
        verify(exactly = 1) { repository.insertIfMissing(association, 4, any(), any()) }
        verify(exactly = 0) { repository.save(any<HelloAssoCashOut>()) }
    }
}
