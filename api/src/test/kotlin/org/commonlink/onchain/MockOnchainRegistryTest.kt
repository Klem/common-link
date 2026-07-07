package org.commonlink.onchain

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.web3j.protocol.core.methods.response.TransactionReceipt
import java.math.BigInteger

class MockOnchainRegistryTest {

    private val mock = MockOnchainRegistry()

    private fun assertSyntheticReceipt(receipt: TransactionReceipt) {
        assertNotNull(receipt)
        assertTrue(receipt.transactionHash.startsWith("0x"), "tx hash must start with 0x")
        assertEquals(66, receipt.transactionHash.length, "tx hash must be 0x + 64 hex chars")
        assertEquals("0x1", receipt.status)
        assertTrue(receipt.blockNumber >= BigInteger.ONE, "block number must be >= 1")
    }

    @Test
    fun `verifyAssociation returns synthetic receipt`() =
        assertSyntheticReceipt(mock.verifyAssociation("0xabc", ByteArray(32)))

    @Test
    fun `revokeAssociation returns synthetic receipt`() =
        assertSyntheticReceipt(mock.revokeAssociation("0xabc"))

    @Test
    fun `restoreAssociation returns synthetic receipt`() =
        assertSyntheticReceipt(mock.restoreAssociation("0xabc"))

    @Test
    fun `createCampaign returns synthetic receipt`() =
        assertSyntheticReceipt(mock.createCampaign(ByteArray(32), "0xabc", BigInteger.TEN, BigInteger.valueOf(3), ByteArray(32)))

    @Test
    fun `publishCampaign returns synthetic receipt`() =
        assertSyntheticReceipt(mock.publishCampaign(ByteArray(32)))

    @Test
    fun `updateCampaignBudget returns synthetic receipt`() =
        assertSyntheticReceipt(mock.updateCampaignBudget(ByteArray(32), ByteArray(32)))

    @Test
    fun `pauseCampaign returns synthetic receipt`() =
        assertSyntheticReceipt(mock.pauseCampaign(ByteArray(32)))

    @Test
    fun `unpauseCampaign returns synthetic receipt`() =
        assertSyntheticReceipt(mock.unpauseCampaign(ByteArray(32)))

    @Test
    fun `cancelCampaign returns synthetic receipt`() =
        assertSyntheticReceipt(mock.cancelCampaign(ByteArray(32)))

    @Test
    fun `completeCampaign returns synthetic receipt`() =
        assertSyntheticReceipt(mock.completeCampaign(ByteArray(32)))

    @Test
    fun `revertCampaignToDraft returns synthetic receipt`() =
        assertSyntheticReceipt(mock.revertCampaignToDraft(ByteArray(32)))

    @Test
    fun `recordDonation returns synthetic receipt`() =
        assertSyntheticReceipt(mock.recordDonation(ByteArray(32), "0xdonor", ByteArray(32), BigInteger.valueOf(5000), ByteArray(32), ByteArray(32)))

    @Test
    fun `markMilestoneReached returns synthetic receipt`() =
        assertSyntheticReceipt(mock.markMilestoneReached(ByteArray(32), BigInteger.ONE, ByteArray(32)))

    @Test
    fun `recordPayout returns synthetic receipt`() =
        assertSyntheticReceipt(mock.recordPayout(ByteArray(32), ByteArray(32), BigInteger.valueOf(10000)))

    @Test
    fun `block numbers are strictly increasing`() {
        val fresh = MockOnchainRegistry()
        val r1 = fresh.verifyAssociation("0x1", ByteArray(32))
        val r2 = fresh.revokeAssociation("0x1")
        val r3 = fresh.publishCampaign(ByteArray(32))
        assertTrue(r2.blockNumber > r1.blockNumber)
        assertTrue(r3.blockNumber > r2.blockNumber)
    }

    @Test
    fun `tx hashes are unique across calls`() {
        val fresh = MockOnchainRegistry()
        val hashes = (1..10).map { fresh.publishCampaign(ByteArray(32)).transactionHash }.toSet()
        assertEquals(10, hashes.size, "all tx hashes must be unique")
    }
}
