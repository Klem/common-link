package org.commonlink.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import org.commonlink.entity.Campaign
import org.springframework.stereotype.Component
import org.web3j.crypto.Hash
import org.web3j.utils.Numeric
import java.util.TreeMap

/**
 * Computes a canonical keccak256 hash of a campaign's budget for on-chain registration.
 *
 * Only budget content (sections + items) is hashed — no DB ids or timestamps.
 * Keys are sorted alphabetically and amounts are serialized as plain decimal strings
 * to ensure cross-environment determinism.
 */
@Component
class CampaignBudgetHasher(private val objectMapper: ObjectMapper) {

    fun hash(campaign: Campaign): String {
        val canonical = objectMapper.writer()
            .with(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS)
            .writeValueAsBytes(toCanonical(campaign))
        return Numeric.toHexString(Hash.sha3(canonical))
    }

    private fun toCanonical(c: Campaign): TreeMap<String, Any> {
        val sections = c.budgetSections
            .sortedBy { it.sortOrder }
            .map { section ->
                TreeMap<String, Any>().apply {
                    put("code", section.code)
                    put("items", section.items.sortedBy { it.sortOrder }.map { item ->
                        TreeMap<String, Any>().apply {
                            put("amount", item.amount.toPlainString())
                            put("label", item.label)
                        }
                    })
                    put("name", section.name)
                    put("side", section.side.name)
                }
            }
        return TreeMap<String, Any>().apply { put("sections", sections) }
    }
}
