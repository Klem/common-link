package org.commonlink.service

import org.commonlink.config.HelloAssoProperties
import org.commonlink.entity.HelloAssoConnectionState
import org.commonlink.exception.HelloAssoException
import org.commonlink.exception.HelloAssoNotLinkedException
import org.commonlink.exception.HelloAssoUnauthorizedException
import org.commonlink.exception.UserNotFoundException
import org.commonlink.repository.AssociationProfileRepository
import org.commonlink.repository.HelloAssoConnectionRepository
import org.commonlink.security.AuthRateLimiter
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import org.springframework.stereotype.Service
import org.springframework.transaction.support.TransactionTemplate
import java.time.Instant
import java.util.UUID

/**
 * Reads an organization's cash-in eligibility at HelloAsso (`isCashInCompliant` of `GET /v5/organizations/{slug}`)
 * into its link: `can_collect`, `ever_compliant` (sticky), `last_eligibility_check_at`.
 *
 * Read at linking, on the association's request (rate-limited), on its return to the page (throttled, through the
 * status read), on a schedule (2 h while not ready, daily once ready) and after any `Organization` notification —
 * never trusting a notification's content. **No checkout-intent is ever created to probe eligibility.**
 */
@Service
class HelloAssoEligibilityService(
    private val connectionRepository: HelloAssoConnectionRepository,
    private val associationProfileRepository: AssociationProfileRepository,
    private val tokenManager: HelloAssoTokenManager,
    private val gateway: HelloAssoGateway,
    private val breaker: HelloAssoConnectionBreaker,
    private val rows: HelloAssoConnectionRows,
    private val rateLimiter: AuthRateLimiter,
    private val properties: HelloAssoProperties,
    private val transactionTemplate: TransactionTemplate,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    /**
     * Re-reads the eligibility of [associationId]'s ACTIVE link. The organization's current slug is read first: a
     * renamed organization (HelloAsso `Organization` event) gets a new slug, and the stored one would then fail every
     * later call. A rejected token marks the link BROKEN.
     *
     * @return false when there is nothing to read (no ACTIVE link) or HelloAsso failed — never throws.
     */
    fun refresh(associationId: UUID): Boolean {
        val connection = connectionRepository.findByAssociationId(associationId) ?: return false
        if (connection.state != HelloAssoConnectionState.ACTIVE) return false
        return try {
            val accessToken = tokenManager.getValidAccessToken(associationId)
            val slug = gateway.listOrganizations(accessToken).singleOrNull()?.organizationSlug?.takeIf { it.isNotBlank() }
                ?: connection.organizationSlug
                ?: return false
            val organization = gateway.getOrganization(accessToken, slug)
            store(associationId, organization.cashInCompliant == true, slug)
            true
        } catch (ex: HelloAssoUnauthorizedException) {
            breaker.markBroken(associationId)
            false
        } catch (ex: HelloAssoException) {
            log.warn("HelloAsso eligibility read failed for association {}: {}", associationId, ex.javaClass.simpleName)
            false
        } catch (ex: IllegalStateException) {
            log.debug("HelloAsso eligibility read skipped for association {}: {}", associationId, ex.message)
            false
        }
    }

    /**
     * Records [canCollect] as the latest eligibility read, and the organization's current [slug] when given. Also used
     * when HelloAsso answers 409 to a checkout (organization not eligible any more), before re-reading.
     *
     * Runs under the link's row lock in a transaction of its own (or the caller's), through [transactionTemplate]
     * rather than `@Transactional`: [refresh] calls it on `this`, where the proxy would be bypassed.
     */
    fun store(associationId: UUID, canCollect: Boolean, slug: String? = null) {
        transactionTemplate.executeWithoutResult { storeLocked(associationId, canCollect, slug) }
    }

    private fun storeLocked(associationId: UUID, canCollect: Boolean, slug: String?) {
        val connection = connectionRepository.findByAssociationIdForUpdate(associationId) ?: return
        val now = Instant.now()
        if (slug != null && slug != connection.organizationSlug) {
            log.info("HelloAsso organization of association {} renamed: slug {} → {}", associationId, connection.organizationSlug, slug)
            connection.organizationSlug = slug
        }
        if (connection.canCollect != canCollect) {
            log.info("HelloAsso eligibility of association {} is now {}", associationId, canCollect)
        }
        connection.canCollect = canCollect
        connection.everCompliant = connection.everCompliant || canCollect
        connection.lastEligibilityCheckAt = now
        connection.updatedAt = now
        connectionRepository.save(connection)
    }

    /**
     * "Vérifier mon dossier": the association asks for an eligibility read now. At most once per
     * [HelloAssoProperties.eligibilityRefreshRateLimit] per association.
     *
     * @throws org.commonlink.exception.HelloAssoNotAssociationProviderException when the association is not on HELLOASSO.
     * @throws HelloAssoNotLinkedException when it has no ACTIVE link.
     * @throws org.commonlink.exception.RateLimitException when asked again too soon.
     */
    fun requestRefresh(userId: UUID) {
        val association = associationProfileRepository.findByUserId(userId)
            .orElseThrow { UserNotFoundException("Association profile not found for user $userId") }
        rows.requireHelloAssoProvider(association)
        val associationId = association.id!!
        val connection = connectionRepository.findByAssociationId(associationId)
        if (connection?.state != HelloAssoConnectionState.ACTIVE) throw HelloAssoNotLinkedException()
        val window = properties.eligibilityRefreshRateLimit
        rateLimiter.check("helloasso-eligibility:association:$associationId", 1, maxOf(1, window.toMinutes()))
        refresh(associationId)
    }

    /**
     * Throttled read for the association's own status display (return to the page): only while it cannot collect,
     * and at most once per [HelloAssoProperties.eligibilityRefreshRateLimit]. Never throws.
     */
    fun refreshIfStale(associationId: UUID) {
        val connection = connectionRepository.findByAssociationId(associationId) ?: return
        if (connection.state != HelloAssoConnectionState.ACTIVE || connection.canCollect) return
        val last = connection.lastEligibilityCheckAt
        if (last != null && last.isAfter(Instant.now().minus(properties.eligibilityRefreshRateLimit))) return
        refresh(associationId)
    }
}

/**
 * Scheduled eligibility reads: every [HelloAssoProperties.eligibilityPendingInterval] while a link cannot collect,
 * every [HelloAssoProperties.eligibilityReadyInterval] once it can. Exits at once when no HELLOASSO association has an
 * ACTIVE link. Never throws.
 */
@Service
class HelloAssoEligibilityExecutor(
    private val connectionRepository: HelloAssoConnectionRepository,
    private val eligibilityService: HelloAssoEligibilityService,
    private val properties: HelloAssoProperties,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    /** Re-reads the eligibility of every due link (see [HelloAssoEligibilityService.refresh]). */
    fun sweep() {
        if (!connectionRepository.existsActiveForHelloAssoAssociations()) {
            log.debug("HelloAsso eligibility sweep: no active HelloAsso link")
            return
        }
        val now = Instant.now()
        val due = connectionRepository.findActiveHelloAssoEligibilityCandidates(
            pendingBefore = now.minus(properties.eligibilityPendingInterval),
            readyBefore = now.minus(properties.eligibilityReadyInterval),
        )
        val read = due.count { eligibilityService.refresh(it) }
        if (due.isNotEmpty()) log.info("HelloAsso eligibility sweep: {} due, {} read", due.size, read)
    }
}

/** Thin scheduler of [HelloAssoEligibilityExecutor] (checks every 10 minutes; each link follows its own cadence). */
@Component
class HelloAssoEligibilityScheduler(private val executor: HelloAssoEligibilityExecutor) {

    /** Runs one eligibility sweep. */
    @Scheduled(
        initialDelayString = "\${app.helloasso.eligibility-initial-delay-ms:240000}",
        fixedDelayString = "\${app.helloasso.eligibility-check-delay-ms:600000}",
    )
    fun tick() = executor.sweep()
}
