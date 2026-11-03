package org.commonlink.service

import org.commonlink.dto.HelloAssoCheckoutIntentJson
import org.commonlink.dto.HelloAssoCheckoutIntentResponseJson
import org.commonlink.dto.HelloAssoInitCheckoutJson
import org.commonlink.dto.HelloAssoOrderJson
import org.commonlink.dto.HelloAssoOrganizationJson
import org.commonlink.dto.HelloAssoOrganizationLightJson
import org.commonlink.dto.HelloAssoPaymentJson
import org.commonlink.exception.HelloAssoNotEligibleException
import org.commonlink.exception.HelloAssoUnauthorizedException
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Primary
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/**
 * In-memory HelloAsso for local runs and E2E tests (`app.helloasso.mock=true`, never under `prod`): replaces
 * [HelloAssoClient] so a whole journey — keys, link, eligibility, checkout, notification, confirmation — plays without
 * any network call (HelloAsso return URLs require HTTPS, which local development lacks). No business logic: it only
 * answers like HelloAsso would.
 *
 * Keys drive the scenario through the client id:
 * - `mock:<RNA>` (e.g. `mock:W751121684`) → one organization carrying that RNA (so it matches the association whose
 *   identifier it is), slug `mock-<rna>`;
 * - any other client id, or the secret `invalid` → HelloAsso refuses the keys.
 *
 * Eligibility (`isCashInCompliant`) starts false and is switched by [setCashIn]; a checkout-intent is paid
 * (`Authorized`, card) as soon as it is created unless [setPaymentState] says otherwise. Both are driven by the mock
 * controller.
 */
@Component
@Primary
@Profile("!prod")
@ConditionalOnProperty(prefix = "app.helloasso", name = ["mock"], havingValue = "true")
class FakeHelloAssoGateway : HelloAssoGateway {

    companion object {
        const val KEY_PREFIX = "mock:"
        const val INVALID_SECRET = "invalid"
        fun slugFor(rna: String) = "mock-${rna.lowercase()}"
    }

    private data class Intent(val body: HelloAssoInitCheckoutJson, var state: String = "Authorized")

    private val organizationsByToken = ConcurrentHashMap<String, String>()
    private val rnaBySlug = ConcurrentHashMap<String, String>()
    private val cashIn = ConcurrentHashMap<String, Boolean>()
    private val intents = ConcurrentHashMap<Long, Intent>()
    private val ids = AtomicLong(1000)

    override fun obtainTokens(clientId: String, clientSecret: String): HelloAssoTokens {
        if (!clientId.startsWith(KEY_PREFIX) || clientSecret == INVALID_SECRET) {
            throw HelloAssoUnauthorizedException("client_credentials", 401)
        }
        val rna = clientId.removePrefix(KEY_PREFIX).uppercase()
        val slug = slugFor(rna)
        rnaBySlug[slug] = rna
        return issue(slug)
    }

    override fun refreshTokens(refreshToken: String): HelloAssoTokens {
        val slug = organizationsByToken[refreshToken] ?: throw HelloAssoUnauthorizedException("refresh_token", 400)
        return issue(slug)
    }

    override fun listOrganizations(accessToken: String): List<HelloAssoOrganizationLightJson> {
        val slug = organizationsByToken[accessToken] ?: throw HelloAssoUnauthorizedException("GET /users/me/organizations", 401)
        return listOf(HelloAssoOrganizationLightJson(slug, "Organisation fictive ${rnaBySlug[slug]}"))
    }

    override fun getOrganization(accessToken: String, slug: String): HelloAssoOrganizationJson {
        if (organizationsByToken[accessToken] != slug) throw HelloAssoUnauthorizedException("GET /organizations", 401)
        return HelloAssoOrganizationJson(slug, "Organisation fictive ${rnaBySlug[slug]}", rnaBySlug[slug], cashIn[slug] ?: false)
    }

    override fun createCheckoutIntent(
        accessToken: String,
        slug: String,
        body: HelloAssoInitCheckoutJson,
    ): HelloAssoCheckoutIntentResponseJson {
        if (organizationsByToken[accessToken] != slug) throw HelloAssoUnauthorizedException("POST /checkout-intents", 401)
        if (cashIn[slug] != true) throw HelloAssoNotEligibleException("POST /checkout-intents")
        val id = ids.incrementAndGet()
        intents[id] = Intent(body)
        // Straight back to CommonLink's return page, as HelloAsso does after a successful payment.
        val redirect = body.returnUrl + (if ('?' in body.returnUrl) "&" else "?") + "checkoutIntentId=$id&code=succeeded&orderId=$id"
        return HelloAssoCheckoutIntentResponseJson(id, redirect)
    }

    override fun getCheckoutIntent(accessToken: String, slug: String, checkoutIntentId: Long): HelloAssoCheckoutIntentJson {
        val intent = intents[checkoutIntentId] ?: throw org.commonlink.exception.HelloAssoUnavailableException("GET /checkout-intents", 404)
        val order = if (intent.state in setOf("Authorized", "Refunded", "Refunding", "Contested")) {
            HelloAssoOrderJson(checkoutIntentId, checkoutIntentId, listOf(HelloAssoPaymentJson(checkoutIntentId, intent.body.totalAmount, intent.state, "Card")))
        } else null
        return HelloAssoCheckoutIntentJson(checkoutIntentId, intent.body.metadata, order)
    }

    /** Switches an organization's eligibility (mock controller). */
    fun setCashIn(slug: String, compliant: Boolean) {
        cashIn[slug] = compliant
    }

    /** Sets the payment state of a checkout-intent (`Authorized`, `Refused`, `Refunded`, `Contested`…). */
    fun setPaymentState(checkoutIntentId: Long, state: String): Boolean {
        val intent = intents[checkoutIntentId] ?: return false
        intent.state = state
        return true
    }

    /** Metadata of a created checkout-intent, to build a simulated notification. */
    fun metadataOf(checkoutIntentId: Long): Map<String, String>? = intents[checkoutIntentId]?.body?.metadata

    private fun issue(slug: String): HelloAssoTokens {
        val tokens = HelloAssoTokens("mock-access-${UUID.randomUUID()}", "mock-refresh-${UUID.randomUUID()}", 1800)
        organizationsByToken[tokens.accessToken] = slug
        organizationsByToken[tokens.refreshToken] = slug
        return tokens
    }
}
