package org.commonlink.service

import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.commonlink.entity.AssociationProfile
import org.commonlink.entity.Campaign
import org.commonlink.entity.ComplianceAuditSubjectType
import org.commonlink.entity.Donation
import org.commonlink.entity.FundsState
import org.commonlink.entity.HelloAssoDonationFunds
import org.commonlink.entity.HelloAssoFundsReleaseEvidence
import org.commonlink.entity.ReceiptConfirmationMode
import org.commonlink.exception.FundsReleaseNotAllowedException
import org.commonlink.exception.NotFoundException
import org.commonlink.exception.UnprocessableEntityException
import org.commonlink.repository.AssociationProfileRepository
import org.commonlink.repository.HelloAssoConnectionRepository
import org.commonlink.repository.HelloAssoDonationFundsRepository
import org.commonlink.repository.HelloAssoFundsReleaseEvidenceRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.mock.web.MockMultipartFile
import java.math.BigDecimal
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID

/** Curator release: strict evidence, own association's tracked donations only, one transaction, one journal entry. */
class HelloAssoFundsReleaseServiceTest {

    private val fundsRepo = mockk<HelloAssoDonationFundsRepository>()
    private val evidenceRepo = mockk<HelloAssoFundsReleaseEvidenceRepository>()
    private val associationRepo = mockk<AssociationProfileRepository>()
    private val connectionRepo = mockk<HelloAssoConnectionRepository>()
    private val auditLog = mockk<ComplianceAuditLogService>(relaxed = true)
    private val service = HelloAssoFundsReleaseService(fundsRepo, evidenceRepo, associationRepo, connectionRepo, auditLog)
        .also { it.clock = Clock.fixed(NOW, ZoneOffset.UTC) }

    private val association = UUID.randomUUID()
    private val otherAssociation = UUID.randomUUID()
    private val curator = UUID.randomUUID()
    private val rows = linkedMapOf<UUID, HelloAssoDonationFunds>()
    private val availableAt = mutableMapOf<UUID, Instant?>()
    private val evidences = mutableListOf<HelloAssoFundsReleaseEvidence>()

    private companion object {
        val NOW: Instant = Instant.parse("2026-11-12T09:00:00Z")
        val PDF = "%PDF-1.4\n%statement line\n".toByteArray()
    }

    init {
        every { associationRepo.existsById(association) } returns true
        every { associationRepo.existsById(otherAssociation) } returns true
        every { fundsRepo.lockAllForUpdate(any()) } answers { firstArg<Collection<UUID>>().sorted().mapNotNull { rows[it] } }
        every { fundsRepo.save(any<HelloAssoDonationFunds>()) } answers { firstArg() }
        every { evidenceRepo.save(any<HelloAssoFundsReleaseEvidence>()) } answers { firstArg<HelloAssoFundsReleaseEvidence>().also { evidences += it } }
    }

    private fun row(state: FundsState = FundsState.IN_TRANSIT, amount: String = "10.00", owner: UUID = association): HelloAssoDonationFunds {
        val assoc = mockk<AssociationProfile> { every { id } returns owner }
        val campaign = mockk<Campaign> { every { this@mockk.association } returns assoc }
        val donationId = UUID.randomUUID()
        val donation = mockk<Donation> {
            every { id } returns donationId
            every { this@mockk.campaign } returns campaign
            every { this@mockk.amount } returns BigDecimal(amount)
            every { fundsAvailableAt = any() } answers { availableAt[donationId] = firstArg() }
        }
        return HelloAssoDonationFunds(donation = donation, haPaymentId = 1, fundsState = state).also { it.donationId = donationId; rows[donationId] = it }
    }

    private fun file(bytes: ByteArray = PDF, type: String = "application/pdf") = MockMultipartFile("file", "line.pdf", type, bytes)

    private fun release(
        ids: List<UUID>,
        reason: String? = "HelloAsso link broken, payout received",
        line: String? = "12/11/2026 VIR HELLOASSO +30,00",
        f: MockMultipartFile? = file(),
    ) = service.release(association, ids, reason, line, f, curator)

    @Test
    fun `releases every non-terminal donation now, stores the evidence and journals once without the line nor the file`() {
        val a = row(FundsState.AT_PROVIDER, "10.00")
        val b = row(FundsState.PAYOUT_PREPARING, "5.50")
        val c = row(FundsState.IN_TRANSIT, "4.50")

        val response = release(listOf(c.donationId!!, a.donationId!!, b.donationId!!))

        for (r in listOf(a, b, c)) {
            assertEquals(FundsState.AVAILABLE, r.fundsState)
            assertEquals(ReceiptConfirmationMode.CURATOR_RELEASE, r.receiptConfirmationMode)
            assertEquals(curator, r.receiptConfirmedBy)
            assertEquals(NOW, r.receiptConfirmedAt)
            assertEquals(NOW, availableAt[r.donationId])
        }
        assertEquals(BigDecimal("20.00"), response.amount)
        val evidence = evidences.single()
        assertEquals("12/11/2026 VIR HELLOASSO +30,00", evidence.statementLine)
        assertEquals("application/pdf", evidence.contentType)
        assertEquals(curator, evidence.createdBy)
        val payload = slot<Map<String, Any?>>()
        verify(exactly = 1) {
            auditLog.append(ComplianceAuditLogService.HELLOASSO_FUNDS_MANUALLY_RELEASED, ComplianceAuditSubjectType.ASSOCIATION, capture(payload), association, curator)
        }
        assertEquals(evidence.id.toString(), payload.captured["evidenceId"])
        assertEquals("20.00", payload.captured["amount"])
        assertEquals(3, (payload.captured["donationIds"] as List<*>).size)
        assertFalse(payload.captured.values.any { it.toString().contains("VIR HELLOASSO") }, "statement line journaled")
    }

    @Test
    fun `a blank or too long reason or statement line is refused, nothing changes`() {
        val a = row()
        val id = listOf(a.donationId!!)
        assertThrows<UnprocessableEntityException> { release(id, reason = "  ") }
        assertThrows<UnprocessableEntityException> { release(id, reason = null) }
        assertThrows<UnprocessableEntityException> { release(id, reason = "x".repeat(2001)) }
        assertThrows<UnprocessableEntityException> { release(id, line = "") }
        assertThrows<UnprocessableEntityException> { release(id, line = "x".repeat(501)) }
        assertThrows<UnprocessableEntityException> { release(emptyList()) }
        assertNothingChanged(a)
    }

    @Test
    fun `a missing, wrong-type, disguised or too big file is refused, nothing changes`() {
        val a = row()
        val id = listOf(a.donationId!!)
        assertThrows<UnprocessableEntityException> { release(id, f = null) }
        assertThrows<UnprocessableEntityException> { release(id, f = file(PDF, "application/vnd.openxmlformats-officedocument.wordprocessingml.document")) }
        assertThrows<UnprocessableEntityException> { release(id, f = file("not a pdf".toByteArray())) }
        assertThrows<UnprocessableEntityException> { release(id, f = file(PDF + ByteArray(10 * 1024 * 1024))) }
        assertNothingChanged(a)
    }

    @Test
    fun `a donation of another association, or not tracked, is not found, nothing changes`() {
        val own = row()
        val foreign = row(owner = otherAssociation)

        assertThrows<NotFoundException> { release(listOf(own.donationId!!, foreign.donationId!!)) }
        assertThrows<NotFoundException> { release(listOf(own.donationId!!, UUID.randomUUID())) }
        assertNothingChanged(own)
        assertNothingChanged(foreign)
    }

    @Test
    fun `an available or withdrawn donation refuses the whole release, nothing changes`() {
        val pending = row()
        val available = row(FundsState.AVAILABLE)
        val withdrawn = row(FundsState.WITHDRAWN)

        assertThrows<FundsReleaseNotAllowedException> { release(listOf(pending.donationId!!, available.donationId!!)) }
        assertThrows<FundsReleaseNotAllowedException> { release(listOf(pending.donationId!!, withdrawn.donationId!!)) }
        assertNothingChanged(pending)
        assertEquals(FundsState.WITHDRAWN, withdrawn.fundsState)
    }

    @Test
    fun `an unknown association is not found`() {
        every { associationRepo.existsById(any()) } returns false
        assertThrows<NotFoundException> { service.listReleasable(UUID.randomUUID()) }
    }

    private fun assertNothingChanged(row: HelloAssoDonationFunds) {
        assertFalse(row.fundsState == FundsState.AVAILABLE)
        assertNull(availableAt[row.donationId])
        assertEquals(0, evidences.size)
        verify(exactly = 0) { auditLog.append(any(), any(), any(), any(), any()) }
    }
}
