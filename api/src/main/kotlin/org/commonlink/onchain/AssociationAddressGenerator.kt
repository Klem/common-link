package org.commonlink.onchain

import org.commonlink.config.OnchainConfig
import org.springframework.stereotype.Service
import org.web3j.crypto.Keys
import org.web3j.utils.Numeric
import java.util.UUID
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * Derives a deterministic, pseudonymous 20-byte EVM address for an association.
 *
 * The address is the last 20 bytes of HMAC-SHA256(associationAddressSecret, associationProfileId.toString()).
 * No private key is stored — the contract moves no funds. The address is used solely to
 * record association identity on-chain for certification purposes.
 */
@Service
class AssociationAddressGenerator(private val cfg: OnchainConfig) {

    private val mac: Mac by lazy {
        Mac.getInstance("HmacSHA256").apply {
            init(SecretKeySpec(cfg.associationAddressSecret.toByteArray(Charsets.UTF_8), "HmacSHA256"))
        }
    }

    /** Deterministic 20-byte address: last 20 bytes of HMAC-SHA256(secret, associationProfileId). */
    @Synchronized
    fun generate(associationProfileId: UUID): String {
        val mac = (mac.clone() as Mac).apply { reset() }
        val digest = mac.doFinal(associationProfileId.toString().toByteArray(Charsets.UTF_8))
        val addrBytes = digest.copyOfRange(digest.size - 20, digest.size)
        return Keys.toChecksumAddress("0x" + Numeric.toHexStringNoPrefix(addrBytes))
    }
}
