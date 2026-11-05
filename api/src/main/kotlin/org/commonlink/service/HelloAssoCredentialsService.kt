package org.commonlink.service

import org.commonlink.config.HelloAssoProperties
import org.commonlink.dto.HelloAssoNotificationUrlResponse
import org.commonlink.dto.HelloAssoVerifyRequest
import org.commonlink.dto.HelloAssoVerifyResponse
import org.commonlink.entity.AssociationProfile
import org.commonlink.exception.BadGatewayException
import org.commonlink.exception.HelloAssoInvalidKeysException
import org.commonlink.exception.HelloAssoNotificationUrlNotConfiguredException
import org.commonlink.exception.HelloAssoOrganizationUnresolvedException
import org.commonlink.exception.HelloAssoRateLimitedException
import org.commonlink.exception.HelloAssoUnauthorizedException
import org.commonlink.exception.HelloAssoUnavailableException
import org.commonlink.exception.HelloAssoNotEligibleException
import org.commonlink.exception.HelloAssoRequestRefusedException
import org.commonlink.exception.RateLimitException
import org.commonlink.exception.UserNotFoundException
import org.commonlink.repository.AssociationProfileRepository
import org.commonlink.security.AuthRateLimiter
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.Instant
import java.util.UUID

/**
 * First steps of an association's HelloAsso link: its notification URL, then the check of the API keys it entered.
 *
 * **The `client_secret` is never kept** — it goes into a single token call and nowhere else: no field, no log, no
 * exception message, no database row. The key check persists nothing; a matching result is kept in memory
 * ([HelloAssoPendingLinkStore]) until the association confirms it with its mandate.
 */
@Service
class HelloAssoCredentialsService(
    private val associationProfileRepository: AssociationProfileRepository,
    private val client: HelloAssoGateway,
    private val pendingLinks: HelloAssoPendingLinkStore,
    private val rateLimiter: AuthRateLimiter,
    private val properties: HelloAssoProperties,
    private val rows: HelloAssoConnectionRows,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    companion object {
        /** Key checks allowed per association within [VERIFY_WINDOW_MINUTES]. */
        const val VERIFY_MAX_ATTEMPTS = 5
        const val VERIFY_WINDOW_MINUTES = 10L

        /**
         * An association's notification URL: [webhookUrl] (`app.helloasso.webhook-url`, path included) followed by
         * its [token]; null when [webhookUrl] is blank.
         */
        fun notificationUrl(webhookUrl: String, token: String): String? =
            webhookUrl.trim().trimEnd('/').takeIf { it.isNotEmpty() }?.let { "$it/$token" }
    }

    /**
     * Checks the association's API keys at HelloAsso: token, the single organization they reach, its eligibility, and
     * whether it is the association verified on CommonLink ([HelloAssoKybMatcher]).
     *
     * @return the verification; `verifyId` null and nothing kept when the organization does not match.
     * @throws org.commonlink.exception.HelloAssoNotAssociationProviderException when the association is not on HELLOASSO.
     * @throws RateLimitException after [VERIFY_MAX_ATTEMPTS] checks within [VERIFY_WINDOW_MINUTES] minutes.
     * @throws HelloAssoInvalidKeysException when HelloAsso refuses the keys.
     * @throws HelloAssoOrganizationUnresolvedException when the keys reach no or several organizations.
     * @throws BadGatewayException when HelloAsso is unavailable.
     */
    fun verify(userId: UUID, request: HelloAssoVerifyRequest): HelloAssoVerifyResponse {
        val association = resolveAssociation(userId)
        rows.requireHelloAssoProvider(association)
        val associationId = association.id!!
        rateLimiter.check("helloasso-verify:association:$associationId", VERIFY_MAX_ATTEMPTS, VERIFY_WINDOW_MINUTES)

        val (tokens, organization) = helloAsso {
            val tokens = try {
                client.obtainTokens(request.clientId, request.clientSecret)
            } catch (ex: HelloAssoUnauthorizedException) {
                log.info("HelloAsso refused the API keys of association {}", associationId)
                throw HelloAssoInvalidKeysException()
            }
            val organizations = client.listOrganizations(tokens.accessToken)
            val slug = organizations.singleOrNull()?.organizationSlug?.takeIf { it.isNotBlank() } ?: run {
                log.info("HelloAsso keys of association {} reach {} organizations", associationId, organizations.size)
                throw HelloAssoOrganizationUnresolvedException()
            }
            tokens to client.getOrganization(tokens.accessToken, slug)
        }

        val slug = organization.organizationSlug ?: throw HelloAssoOrganizationUnresolvedException()
        val orgName = organization.name.orEmpty()
        val canCollect = organization.cashInCompliant == true
        val matches = HelloAssoKybMatcher.matches(association, orgName, organization.rnaNumber)
        if (!matches) {
            log.info("HelloAsso organization {} does not match association {}; nothing kept", slug, associationId)
            return HelloAssoVerifyResponse(null, orgName, slug, matchesKyb = false, canCollect = canCollect)
        }
        val verifyId = pendingLinks.put(
            HelloAssoPendingLink(associationId, request.clientId, slug, orgName, canCollect, tokens, Instant.now())
        )
        log.info("HelloAsso keys of association {} verified for organization {}", associationId, slug)
        return HelloAssoVerifyResponse(verifyId, orgName, slug, matchesKyb = true, canCollect = canCollect)
    }

    /**
     * Returns the association's notification URL, creating its HelloAsso row (NOT_LINKED) with a random, stable token
     * on first call. Idempotent: the token never changes afterwards, whatever the link state.
     *
     * Not transactional on purpose: the row is saved (and committed) before the configuration check, so the token
     * exists even when no URL can be shown yet.
     *
     * @throws org.commonlink.exception.HelloAssoNotAssociationProviderException when the association is not on HELLOASSO.
     * @throws HelloAssoNotificationUrlNotConfiguredException when `app.helloasso.webhook-url` is blank.
     */
    fun ensureNotificationUrl(userId: UUID): HelloAssoNotificationUrlResponse {
        val association = resolveAssociation(userId)
        rows.requireHelloAssoProvider(association)
        val connection = rows.findOrCreate(association)
        val url = notificationUrl(properties.webhookUrl, connection.webhookToken) ?: run {
            log.warn("app.helloasso.webhook-url is blank: cannot show the HelloAsso notification URL")
            throw HelloAssoNotificationUrlNotConfiguredException()
        }
        return HelloAssoNotificationUrlResponse(url)
    }

    /** Maps HelloAsso infrastructure failures to API errors; refusals already mapped inside [call] pass through. */
    private fun <T> helloAsso(call: () -> T): T =
        try {
            call()
        } catch (ex: HelloAssoUnauthorizedException) {
            throw HelloAssoInvalidKeysException()
        } catch (ex: HelloAssoRateLimitedException) {
            throw RateLimitException("HelloAsso rate limit reached. Try again later", retryAfterSeconds = 60)
        } catch (ex: HelloAssoUnavailableException) {
            throw BadGatewayException("HelloAsso is unavailable")
        } catch (ex: HelloAssoNotEligibleException) {
            throw BadGatewayException("HelloAsso refused the request")
        } catch (ex: HelloAssoRequestRefusedException) {
            throw BadGatewayException("HelloAsso refused the request")
        }

    private fun resolveAssociation(userId: UUID): AssociationProfile =
        associationProfileRepository.findByUserId(userId)
            .orElseThrow { UserNotFoundException("Association profile not found for user $userId") }
}
