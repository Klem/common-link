package org.commonlink.onchain

import org.web3j.crypto.Hash
import org.web3j.utils.Numeric
import java.math.BigDecimal
import java.math.BigInteger
import java.nio.ByteBuffer
import java.util.UUID

/** Conversion helpers between domain types and Solidity primitives. */
object OnchainCodec {
    private val UINT96_MAX = BigInteger.TWO.pow(96) - BigInteger.ONE

    /** UUID → bytes32 (16 bytes prefix + 16 zero bytes). */
    fun uuidToBytes32(uuid: UUID): ByteArray {
        val bb = ByteBuffer.allocate(32)
        bb.putLong(uuid.mostSignificantBits)
        bb.putLong(uuid.leastSignificantBits)
        return bb.array()
    }

    /** keccak256 of the canonical SIREN/RNA string. */
    fun keccakSiren(siren: String): ByteArray = Hash.sha3(siren.toByteArray(Charsets.US_ASCII))

    /** EUR (BigDecimal, 2 decimals) → uint96 cents, validating overflow. */
    fun eurToCents(eur: BigDecimal): BigInteger {
        val cents = eur.movePointRight(2).toBigIntegerExact()
        require(cents.signum() >= 0 && cents <= UINT96_MAX) { "amount $eur out of uint96 range" }
        return cents
    }

    /** Hex string (with or without 0x) → bytes32 (left-padded). */
    fun hexToBytes32(hex: String): ByteArray {
        val clean = hex.removePrefix("0x")
        require(clean.length <= 64) { "value too long for bytes32: $hex" }
        return Numeric.hexStringToByteArray("0x" + clean.padStart(64, '0'))
    }

    /** Plain string → bytes32 (utf-8, max 32 bytes, right-padded with zeros). */
    fun stringToBytes32(s: String): ByteArray {
        val raw = s.toByteArray(Charsets.UTF_8)
        require(raw.size <= 32) { "value too long for bytes32: $s" }
        return raw + ByteArray(32 - raw.size)
    }
}
