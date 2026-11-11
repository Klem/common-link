package org.commonlink.service

import org.commonlink.config.CashOutExportAmountUnit
import org.commonlink.config.PaymentsProperties
import org.commonlink.entity.HelloAssoConnectionState
import org.commonlink.repository.HelloAssoCashOutRepository
import org.commonlink.repository.HelloAssoConnectionRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.support.TransactionTemplate
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Instant
import java.util.UUID

/**
 * Net amount of a HelloAsso payout as it reaches the association's bank account (Σ credits − Σ debits of HelloAsso's
 * payout export), shown next to the "transfer received" confirmation. It may differ from the sum of CommonLink's
 * donations: the payout can carry other payments of the association and be lowered by refunds or contestations.
 *
 * Read lazily, once per payout, then served from `helloasso_cash_outs`. Only the total is stored: the export's rows
 * (third-party payers' data) are summed in memory by the gateway and dropped. Never blocks: any failure (no link,
 * HelloAsso unavailable, payout not exported yet) gives `null`, and the next call tries again.
 */
@Service
class HelloAssoCashOutTotalService(
    private val repository: HelloAssoCashOutRepository,
    private val connectionRepository: HelloAssoConnectionRepository,
    private val tokenManager: HelloAssoTokenManager,
    private val gateway: HelloAssoGateway,
    private val properties: PaymentsProperties,
    private val transactionTemplate: TransactionTemplate,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    /** The total of payout [idCashOut] of [associationId] in euros, read at HelloAsso if not stored yet; `null` if unknown. */
    fun netTotalOf(associationId: UUID, idCashOut: Long): BigDecimal? {
        repository.findByAssociationIdAndIdCashOut(associationId, idCashOut)?.let { return it.netTotal }
        val connection = connectionRepository.findByAssociationId(associationId)
        if (connection?.state != HelloAssoConnectionState.ACTIVE) return null
        val token = try {
            tokenManager.getValidAccessToken(associationId)
        } catch (ex: Exception) {
            log.warn("HelloAsso payout {} total: no token for association {}: {}", idCashOut, associationId, ex.javaClass.simpleName)
            return null
        }
        return fetch(associationId, idCashOut, token, connection.organizationSlug)
    }

    /**
     * Same as [netTotalOf] for a caller that already holds the association's [accessToken] and organization [slug]
     * (the receipt confirmation): no second token request. Must not run inside a transaction (HTTP call).
     */
    fun netTotalOf(associationId: UUID, idCashOut: Long, accessToken: String, slug: String?): BigDecimal? =
        repository.findByAssociationIdAndIdCashOut(associationId, idCashOut)?.netTotal
            ?: fetch(associationId, idCashOut, accessToken, slug)

    private fun fetch(associationId: UUID, idCashOut: Long, accessToken: String, slug: String?): BigDecimal? {
        if (slug.isNullOrBlank()) return null
        val raw = try {
            gateway.getCashOutNetTotal(accessToken, slug, idCashOut)
        } catch (ex: Exception) {
            log.warn("HelloAsso payout {} total unreadable for association {}: {}", idCashOut, associationId, ex.javaClass.simpleName)
            return null
        }
        val total = toEuros(raw)
        transactionTemplate.executeWithoutResult {
            repository.insertIfMissing(associationId, idCashOut, total, Instant.now())
        }
        log.info("HelloAsso payout {} of association {}: net total stored", idCashOut, associationId)
        return repository.findByAssociationIdAndIdCashOut(associationId, idCashOut)?.netTotal ?: total
    }

    /** Converts an export amount to euros (2 decimals) according to the configured unit (question U3). */
    fun toEuros(raw: BigDecimal): BigDecimal = when (properties.helloasso.cashoutExportAmountUnit) {
        CashOutExportAmountUnit.EUROS -> raw.setScale(2, RoundingMode.HALF_UP)
        CashOutExportAmountUnit.CENTS -> raw.movePointLeft(2).setScale(2, RoundingMode.HALF_UP)
    }
}
