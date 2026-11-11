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
 * Read once per payout — at the receipt confirmation or at a refresh of the Payments tab, never by a plain read of
 * the funds — then served from `helloasso_cash_outs`. Only the total is stored: the export's rows
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

    /** The stored total of payout [idCashOut] of [associationId] in euros, `null` until read. Never calls HelloAsso. */
    fun storedNetTotal(associationId: UUID, idCashOut: Long): BigDecimal? =
        repository.findByAssociationIdAndIdCashOut(associationId, idCashOut)?.netTotal

    /**
     * Reads at HelloAsso the totals of [idCashOuts] not stored yet, with **one** access token for the association
     * (the refresh of the Payments tab). Without an active link, or without any missing total, makes no call. Never
     * throws; a failed read leaves that total unknown until the next refresh. Must not run inside a transaction.
     */
    fun fetchMissing(associationId: UUID, idCashOuts: Collection<Long>) {
        val missing = idCashOuts.distinct().filter { storedNetTotal(associationId, it) == null }
        if (missing.isEmpty()) return
        val connection = connectionRepository.findByAssociationId(associationId)
        if (connection?.state != HelloAssoConnectionState.ACTIVE) return
        val token = try {
            tokenManager.getValidAccessToken(associationId)
        } catch (ex: Exception) {
            log.warn("HelloAsso payout totals: no token for association {}: {}", associationId, ex.javaClass.simpleName)
            return
        }
        missing.forEach { fetch(associationId, it, token, connection.organizationSlug) }
    }

    /**
     * The total of payout [idCashOut], read at HelloAsso if not stored yet, for a caller that already holds the
     * association's [accessToken] and organization [slug] (the receipt confirmation): no token request. `null` if
     * unknown. Must not run inside a transaction (HTTP call).
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
