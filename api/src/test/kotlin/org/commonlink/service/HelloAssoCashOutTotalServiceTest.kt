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

/** Lazy read of a HelloAsso payout's net total: once per payout, unit conversion, never blocking. */
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
    fun `the first read fetches and stores the total in euros, the next ones are served from the table`() {
        every { gateway.getCashOutNetTotal("access", "mon-asso", 60001) } returns BigDecimal("30.0")
        val service = serviceWith()

        assertEquals(BigDecimal("30.00"), service.netTotalOf(association, 60001))
        assertEquals(BigDecimal("30.00"), service.netTotalOf(association, 60001))

        verify(exactly = 1) { gateway.getCashOutNetTotal(any(), any(), any()) }
        verify(exactly = 1) { tokenManager.getValidAccessToken(association) }
        assertEquals(mapOf(60001L to BigDecimal("30.00")), stored)
    }

    @Test
    fun `an export in cents is converted to euros`() {
        every { gateway.getCashOutNetTotal("access", "mon-asso", 1) } returns BigDecimal("3050")
        assertEquals(BigDecimal("30.50"), serviceWith(CashOutExportAmountUnit.CENTS).netTotalOf(association, 1))
    }

    @Test
    fun `a HelloAsso failure gives null, stores nothing, and the next call tries again`() {
        every { gateway.getCashOutNetTotal("access", "mon-asso", 2) } throws HelloAssoUnavailableException("GET", 404) andThen BigDecimal("12.5")
        val service = serviceWith()

        assertNull(service.netTotalOf(association, 2))
        assertEquals(emptyMap<Long, BigDecimal>(), stored)
        assertEquals(BigDecimal("12.50"), service.netTotalOf(association, 2))
    }

    @Test
    fun `without an active link nothing is called and the total is unknown`() {
        every { connectionRepository.findByAssociationId(association) } returns connection(HelloAssoConnectionState.DISCONNECTED)

        assertNull(serviceWith().netTotalOf(association, 3))
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
