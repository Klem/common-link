package org.commonlink.service

import org.commonlink.config.HelloAssoProperties
import org.commonlink.dto.HelloAssoNotificationUrlResponse
import org.commonlink.dto.HelloAssoOrganizationJson
import org.commonlink.dto.HelloAssoVerifyRequest
import org.commonlink.dto.HelloAssoVerifyResponse
import org.commonlink.entity.AssociationProfile
import org.commonlink.entity.HelloAssoKybOutcome
import org.commonlink.entity.HelloAssoLinkAttempt
import org.commonlink.entity.HelloAssoLinkAttemptOutcome
import org.commonlink.exception.BadGatewayException
import org.commonlink.exception.HelloAssoInvalidKeysException
import org.commonlink.exception.HelloAssoNotificationUrlNotConfiguredException
import org.commonlink.exception.HelloAssoOrganizationAlreadyLinkedException
import org.commonlink.exception.HelloAssoOrganizationUnresolvedException
import org.commonlink.exception.HelloAssoRateLimitedException
import org.commonlink.exception.HelloAssoUnauthorizedException
import org.commonlink.exception.HelloAssoUnavailableException
import org.commonlink.exception.HelloAssoNotEligibleException
import org.commonlink.exception.HelloAssoRequestRefusedException
import org.commonlink.exception.RateLimitException
import org.commonlink.exception.UserNotFoundException
import org.commonlink.repository.AssociationProfileRepository
import org.commonlink.repository.AssociationRegistryCheckRepository
import org.commonlink.repository.HelloAssoConnectionRepository
import org.commonlink.security.AuthRateLimiter
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.Instant
import java.util.UUID

/**
 * First steps of an association's HelloAsso link: its notification URL, then the check of the API keys it entered.
 *
 * **The `client_secret` is never kept** — it goes into a single token call and nowhere else: no field, no log, no
 * exception message, no database row. The key check persists no link; a matching result is kept in memory
 * ([HelloAssoPendingLinkStore]) until the association confirms it with its mandate. Every check that reaches
 * HelloAsso is journaled ([HelloAssoLinkAttemptRecorder]) for the curators' statistics.
 */
@Service
class HelloAssoCredentialsService(
    private val associationProfileRepository: AssociationProfileRepository,
    private val client: HelloAssoGateway,
    private val pendingLinks: HelloAssoPendingLinkStore,
    private val rateLimiter: AuthRateLimiter,
    private val properties: HelloAssoProperties,
    private val rows: HelloAssoConnectionRows,
    private val connectionRepository: HelloAssoConnectionRepository,
    private val registryCheckRepository: AssociationRegistryCheckRepository,
    private val attemptRecorder: HelloAssoLinkAttemptRecorder,
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
     * What a key check learned so far, journaled once with its outcome ([HelloAssoLinkAttempt]).
     * Mutable on purpose: filled step by step as HelloAsso answers.
     */
    private class AttemptDraft(val associationId: UUID, val clientIdMasked: String?, val associationRna: String?) {
        var organizationsCount: Int? = null
        var organizationSlug: String? = null
        var organizationName: String? = null
        var organizationType: String? = null
        var organizationRna: String? = null

        fun toAttempt(outcome: HelloAssoLinkAttemptOutcome) = HelloAssoLinkAttempt(
            associationId = associationId,
            outcome = outcome,
            clientIdMasked = clientIdMasked,
            organizationsCount = organizationsCount,
            organizationSlug = organizationSlug,
            organizationName = organizationName,
            organizationType = organizationType,
            organizationRna = organizationRna,
            associationRna = associationRna,
        )
    }

    /**
     * Checks the association's API keys at HelloAsso: token, the single organization they reach, that no other
     * association holds it, its eligibility, and whether it is the association verified on CommonLink
     * ([HelloAssoKybMatcher]). Every outcome past the local rate limit is journaled.
     *
     * The association's RNA is its identifier when it is one, otherwise the first RNA found in its registry scans (the
     * one that informed the KYB decision, then the latest) — see [knownRna].
     *
     * @return the verification; `verifyId` null, [HelloAssoVerifyResponse.mismatchReason] set and nothing kept when the
     *   organization does not match.
     * @throws org.commonlink.exception.HelloAssoNotAssociationProviderException when the association is not on HELLOASSO.
     * @throws RateLimitException after [VERIFY_MAX_ATTEMPTS] checks within [VERIFY_WINDOW_MINUTES] minutes, or when
     *   HelloAsso rate-limits.
     * @throws HelloAssoInvalidKeysException when HelloAsso refuses the keys.
     * @throws HelloAssoOrganizationUnresolvedException when the keys reach no or several organizations.
     * @throws HelloAssoOrganizationAlreadyLinkedException when another association holds the organization.
     * @throws BadGatewayException when HelloAsso is unavailable.
     */
    fun verify(userId: UUID, request: HelloAssoVerifyRequest): HelloAssoVerifyResponse {
        val association = resolveAssociation(userId)
        rows.requireHelloAssoProvider(association)
        val associationId = association.id!!
        rateLimiter.check("helloasso-verify:association:$associationId", VERIFY_MAX_ATTEMPTS, VERIFY_WINDOW_MINUTES)

        val draft = AttemptDraft(associationId, HelloAssoLinkService.maskClientId(request.clientId), knownRna(association))
        val (tokens, organization) = try {
            fetchOrganization(request, draft)
        } catch (ex: RuntimeException) {
            outcomeOf(ex)?.let { journal(draft, it) }
            throw ex
        }

        val slug = draft.organizationSlug!!
        val orgName = organization.name.orEmpty()
        val canCollect = organization.cashInCompliant == true
        if (connectionRepository.existsByOrganizationSlugAndAssociationIdNot(slug, associationId)) {
            journal(draft, HelloAssoLinkAttemptOutcome.ORGANIZATION_ALREADY_LINKED)
            log.info("HelloAsso organization {} is already linked to another association; refused for {}", slug, associationId)
            throw HelloAssoOrganizationAlreadyLinkedException()
        }

        val kyb = HelloAssoKybMatcher.evaluate(draft.associationRna, association.name, orgName, organization.rnaNumber)
        if (!kyb.matches) {
            journal(draft, if (kyb == HelloAssoKybOutcome.RNA_MISMATCH) {
                HelloAssoLinkAttemptOutcome.RNA_MISMATCH
            } else {
                HelloAssoLinkAttemptOutcome.NAME_MISMATCH
            })
            log.info(
                "HelloAsso organization {} does not match association {} ({}, type {}, HelloAsso RNA {}); nothing kept",
                slug, associationId, kyb, draft.organizationType, draft.organizationRna ?: "none",
            )
            return HelloAssoVerifyResponse(
                verifyId = null, orgName = orgName, slug = slug, matchesKyb = false, canCollect = canCollect,
                mismatchReason = kyb, associationRna = draft.associationRna, organizationRna = draft.organizationRna,
            )
        }
        val verifyId = pendingLinks.put(
            HelloAssoPendingLink(associationId, request.clientId, slug, orgName, canCollect, tokens, Instant.now())
        )
        journal(draft, HelloAssoLinkAttemptOutcome.MATCHED)
        log.info("HelloAsso keys of association {} verified for organization {} ({})", associationId, slug, kyb)
        return HelloAssoVerifyResponse(
            verifyId = verifyId, orgName = orgName, slug = slug, matchesKyb = true, canCollect = canCollect,
            associationRna = draft.associationRna, organizationRna = draft.organizationRna,
        )
    }

    /**
     * Token, the single organization the keys reach, then its details; fills [draft] as HelloAsso answers.
     * HelloAsso failures leave mapped to API errors ([helloAsso]).
     */
    private fun fetchOrganization(
        request: HelloAssoVerifyRequest,
        draft: AttemptDraft,
    ): Pair<HelloAssoTokens, HelloAssoOrganizationJson> = helloAsso {
        val tokens = try {
            client.obtainTokens(request.clientId, request.clientSecret)
        } catch (ex: HelloAssoUnauthorizedException) {
            log.info("HelloAsso refused the API keys of association {}", draft.associationId)
            throw HelloAssoInvalidKeysException()
        }
        val organizations = client.listOrganizations(tokens.accessToken)
        draft.organizationsCount = organizations.size
        val single = organizations.singleOrNull()
        draft.organizationName = single?.name
        val slug = single?.organizationSlug?.takeIf { it.isNotBlank() } ?: run {
            log.info("HelloAsso keys of association {} reach {} organizations", draft.associationId, organizations.size)
            throw HelloAssoOrganizationUnresolvedException()
        }
        draft.organizationSlug = slug
        val organization = client.getOrganization(tokens.accessToken, slug)
        if (organization.organizationSlug.isNullOrBlank()) throw HelloAssoOrganizationUnresolvedException()
        draft.organizationSlug = organization.organizationSlug
        draft.organizationName = organization.name
        draft.organizationType = organization.type?.takeIf { it.isNotBlank() }
        draft.organizationRna = HelloAssoKybMatcher.normalizeRna(organization.rnaNumber)
        tokens to organization
    }

    /** Journal outcome of an exception thrown by [fetchOrganization]; null for an unexpected one (not journaled). */
    private fun outcomeOf(ex: RuntimeException): HelloAssoLinkAttemptOutcome? = when (ex) {
        is HelloAssoInvalidKeysException -> HelloAssoLinkAttemptOutcome.INVALID_KEYS
        is HelloAssoOrganizationUnresolvedException -> HelloAssoLinkAttemptOutcome.ORGANIZATION_UNRESOLVED
        is RateLimitException -> HelloAssoLinkAttemptOutcome.HELLOASSO_RATE_LIMITED
        is BadGatewayException -> HelloAssoLinkAttemptOutcome.HELLOASSO_UNAVAILABLE
        else -> null
    }

    /** Journals the attempt; statistics only, so a failure to write is logged and never blocks the key check. */
    private fun journal(draft: AttemptDraft, outcome: HelloAssoLinkAttemptOutcome) {
        try {
            attemptRecorder.record(draft.toAttempt(outcome))
        } catch (ex: Exception) {
            log.warn(
                "Could not journal the HelloAsso key check of association {} ({}): {}",
                draft.associationId, outcome, ex.javaClass.simpleName,
            )
        }
    }

    /**
     * The association's RNA as known by CommonLink: its identifier when it is a RNA, otherwise the first RNA found in
     * the registry scan that informed its KYB decision, then in its latest scan. A scan without RNA never hides a later
     * one: every scan made before 2026-11-26 holds none (the RNA was misread), so a fresh curator scan must count.
     * Null when none is known.
     */
    private fun knownRna(association: AssociationProfile): String? {
        fun valid(value: String?) =
            HelloAssoKybMatcher.normalizeRna(value)?.takeIf { RechercheEntreprisesResults.RNA_PATTERN.matches(it) }
        valid(association.identifier)?.let { return it }
        val decisionScan = association.decisionRegistryCheckId?.let { registryCheckRepository.findById(it).orElse(null) }
        return valid(decisionScan?.rna)
            ?: valid(registryCheckRepository.findTopByAssociationIdOrderByCheckedAtDesc(association.id!!)?.rna)
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
