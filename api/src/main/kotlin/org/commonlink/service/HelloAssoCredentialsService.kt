package org.commonlink.service

import org.commonlink.config.HelloAssoProperties
import org.commonlink.dto.HelloAssoNotificationUrlResponse
import org.commonlink.dto.HelloAssoOrganizationJson
import org.commonlink.dto.HelloAssoOrganizationLightJson
import org.commonlink.dto.HelloAssoVerifyRequest
import org.commonlink.dto.HelloAssoVerifyResponse
import org.commonlink.entity.AssociationProfile
import org.commonlink.entity.HelloAssoConnection
import org.commonlink.exception.BadGatewayException
import org.commonlink.exception.HelloAssoInvalidKeysException
import org.commonlink.exception.HelloAssoNotificationUrlNotConfiguredException
import org.commonlink.exception.HelloAssoOrganizationUnresolvedException
import org.commonlink.exception.HelloAssoRateLimitedException
import org.commonlink.exception.HelloAssoUnauthorizedException
import org.commonlink.exception.HelloAssoUnavailableException
import org.commonlink.exception.HelloAssoNotEligibleException
import org.commonlink.exception.RateLimitException
import org.commonlink.exception.UserNotFoundException
import org.commonlink.repository.AssociationProfileRepository
import org.commonlink.repository.HelloAssoConnectionRepository
import org.commonlink.security.AuthRateLimiter
import org.slf4j.LoggerFactory
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.stereotype.Service
import java.security.SecureRandom
import java.time.Instant
import java.util.Base64
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
    private val connectionRepository: HelloAssoConnectionRepository,
    private val client: HelloAssoClient,
    private val pendingLinks: HelloAssoPendingLinkStore,
    private val rateLimiter: AuthRateLimiter,
    private val properties: HelloAssoProperties,
) {
    private val log = LoggerFactory.getLogger(javaClass)
    private val random = SecureRandom()

    companion object {
        /** Key checks allowed per association within [VERIFY_WINDOW_MINUTES]. */
        const val VERIFY_MAX_ATTEMPTS = 5
        const val VERIFY_WINDOW_MINUTES = 10L
        const val NOTIFICATION_PATH = "/api/public/webhooks/helloasso/"
    }

    /**
     * Checks the association's API keys at HelloAsso: token, the single organization they reach, its eligibility, and
     * whether it is the association verified on CommonLink ([HelloAssoKybMatcher]).
     *
     * @return the verification; `verifyId` null and nothing kept when the organization does not match.
     * @throws RateLimitException after [VERIFY_MAX_ATTEMPTS] checks within [VERIFY_WINDOW_MINUTES] minutes.
     * @throws HelloAssoInvalidKeysException when HelloAsso refuses the keys.
     * @throws HelloAssoOrganizationUnresolvedException when the keys reach no or several organizations.
     * @throws BadGatewayException when HelloAsso is unavailable.
     */
    fun verify(userId: UUID, request: HelloAssoVerifyRequest): HelloAssoVerifyResponse {
        val association = resolveAssociation(userId)
        val associationId = association.id!!
        rateLimiter.check("helloasso-verify:association:$associationId", VERIFY_MAX_ATTEMPTS, VERIFY_WINDOW_MINUTES)

        val (tokens, organization) = helloAsso {
            val tokens = try {
                client.obtainTokens(request.clientId, request.clientSecret)
            } catch (ex: HelloAssoUnauthorizedException) {
                log.info("HelloAsso refused the API keys of association {}", associationId)
                throw HelloAssoInvalidKeysException()
            }
            val organizations = client.get("/users/me/organizations", tokens.accessToken, Array<HelloAssoOrganizationLightJson>::class.java)
            val slug = organizations.singleOrNull()?.organizationSlug?.takeIf { it.isNotBlank() } ?: run {
                log.info("HelloAsso keys of association {} reach {} organizations", associationId, organizations.size)
                throw HelloAssoOrganizationUnresolvedException()
            }
            tokens to client.get("/organizations/$slug", tokens.accessToken, HelloAssoOrganizationJson::class.java)
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
     * @throws HelloAssoNotificationUrlNotConfiguredException when `app.helloasso.notification-base-url` is blank.
     */
    fun ensureNotificationUrl(userId: UUID): HelloAssoNotificationUrlResponse {
        val association = resolveAssociation(userId)
        val connection = connectionRepository.findByAssociationId(association.id!!) ?: create(association)
        val base = properties.notificationBaseUrl.trim().trimEnd('/')
        if (base.isEmpty()) {
            log.warn("app.helloasso.notification-base-url is blank: cannot show the HelloAsso notification URL")
            throw HelloAssoNotificationUrlNotConfiguredException()
        }
        return HelloAssoNotificationUrlResponse(base + NOTIFICATION_PATH + connection.webhookToken)
    }

    private fun create(association: AssociationProfile): HelloAssoConnection =
        try {
            connectionRepository.saveAndFlush(HelloAssoConnection(association = association, webhookToken = newToken()))
        } catch (ex: DataIntegrityViolationException) {
            // A concurrent call created the row first: keep that one, its token is the stable one.
            connectionRepository.findByAssociationId(association.id!!) ?: throw ex
        }

    /** 256-bit random, URL-safe, unpadded (43 characters). */
    private fun newToken(): String =
        Base64.getUrlEncoder().withoutPadding().encodeToString(ByteArray(32).also { random.nextBytes(it) })

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
        }

    private fun resolveAssociation(userId: UUID): AssociationProfile =
        associationProfileRepository.findByUserId(userId)
            .orElseThrow { UserNotFoundException("Association profile not found for user $userId") }
}
