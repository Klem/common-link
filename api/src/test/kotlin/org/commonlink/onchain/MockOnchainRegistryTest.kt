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
    fun `registerAssociation returns synthetic receipt`() =
        assertSyntheticReceipt(mock.registerAssociation("0xabc", ByteArray(32)))

    @Test
    fun `registerCampaign returns synthetic receipt`() =
        assertSyntheticReceipt(mock.registerCampaign(ByteArray(32), "0xabc", ByteArray(32)))

    @Test
    fun `updateCampaignBudget returns synthetic receipt`() =
        assertSyntheticReceipt(mock.updateCampaignBudget(ByteArray(32), ByteArray(32)))

    @Test
    fun `recordDonation returns synthetic receipt`() =
        assertSyntheticReceipt(
            mock.recordDonation(ByteArray(32), "0xdonor", ByteArray(32), BigInteger.valueOf(5000), ByteArray(32), ByteArray(32))
        )

    @Test
    fun `recordPayout returns synthetic receipt`() =
        assertSyntheticReceipt(mock.recordPayout(ByteArray(32), ByteArray(32), BigInteger.valueOf(10000), ByteArray(32)))

    @Test
    fun `block numbers are strictly increasing`() {
        val fresh = MockOnchainRegistry()
        val r1 = fresh.registerAssociation("0x1", ByteArray(32))
        val r2 = fresh.registerCampaign(ByteArray(32), "0x1", ByteArray(32))
        val r3 = fresh.updateCampaignBudget(ByteArray(32), ByteArray(32))
        assertTrue(r2.blockNumber > r1.blockNumber)
        assertTrue(r3.blockNumber > r2.blockNumber)
    }

    @Test
    fun `tx hashes are unique across calls`() {
        val fresh = MockOnchainRegistry()
        val hashes = (1..10).map { fresh.updateCampaignBudget(ByteArray(32), ByteArray(32)).transactionHash }.toSet()
        assertEquals(10, hashes.size, "all tx hashes must be unique")
    }
}
