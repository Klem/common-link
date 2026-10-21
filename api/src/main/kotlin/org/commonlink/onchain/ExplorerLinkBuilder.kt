package org.commonlink.onchain

import org.commonlink.config.OnchainConfig
import org.springframework.stereotype.Component

/**
 * Builds public block-explorer deep links (Blockscout URL scheme `/address/<a>` and `/tx/<hash>`).
 *
 * No explorer host is hard-coded: it comes from `onchain.explorer.base-url`. Links are null in mock mode
 * (mock tx hashes are random and exist on no chain) or when no explorer is configured.
 */
@Component
class ExplorerLinkBuilder(private val cfg: OnchainConfig) {

    /**
     * @param address an on-chain address (donor, association, contract).
     * @return the explorer page of [address], or null in mock mode / without explorer.
     */
    fun addressUrl(address: String?): String? = link("address", address)

    /**
     * @param txHash a transaction hash.
     * @return the explorer page of [txHash], or null in mock mode / without explorer.
     */
    fun txUrl(txHash: String?): String? = link("tx", txHash)

    private fun link(kind: String, value: String?): String? {
        if (cfg.mock || value.isNullOrBlank()) return null
        val base = cfg.explorer.baseUrl.trim().trimEnd('/')
        if (base.isEmpty()) return null
        return "$base/$kind/$value"
    }
}
