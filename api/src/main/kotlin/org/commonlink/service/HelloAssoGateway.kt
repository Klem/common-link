package org.commonlink.service

import org.commonlink.dto.HelloAssoCheckoutIntentJson
import org.commonlink.dto.HelloAssoCheckoutIntentResponseJson
import org.commonlink.dto.HelloAssoInitCheckoutJson
import org.commonlink.dto.HelloAssoOrganizationJson
import org.commonlink.dto.HelloAssoOrganizationLightJson
import org.commonlink.dto.HelloAssoPaymentDetailJson

/**
 * Every HelloAsso operation CommonLink performs, in business terms. Implemented by [HelloAssoClient] (real HTTP) and,
 * in local / E2E mock mode only, by a fake that never touches the network (sprint step S5-03).
 *
 * All methods throw [org.commonlink.exception.HelloAssoException] subclasses on failure, never carrying a secret, a
 * token nor a HelloAsso response body.
 */
interface HelloAssoGateway {

    /** `grant_type=client_credentials`: the secret is used for this single call. */
    fun obtainTokens(clientId: String, clientSecret: String): HelloAssoTokens

    /** `grant_type=refresh_token`: the previous refresh token is superseded. */
    fun refreshTokens(refreshToken: String): HelloAssoTokens

    /** `GET /v5/users/me/organizations`: the organizations an association key reaches (one, confirmed in sandbox). */
    fun listOrganizations(accessToken: String): List<HelloAssoOrganizationLightJson>

    /** `GET /v5/organizations/{slug}`, including `isCashInCompliant` (returned to an organization admin). */
    fun getOrganization(accessToken: String, slug: String): HelloAssoOrganizationJson

    /** `POST /v5/organizations/{slug}/checkout-intents`. A 409 means the organization cannot collect. */
    fun createCheckoutIntent(accessToken: String, slug: String, body: HelloAssoInitCheckoutJson): HelloAssoCheckoutIntentResponseJson

    /** `GET /v5/organizations/{slug}/checkout-intents/{id}`: the source of truth of a payment, never a notification body. */
    fun getCheckoutIntent(accessToken: String, slug: String, checkoutIntentId: Long): HelloAssoCheckoutIntentJson

    /** `GET /v5/payments/{id}`: the payout state of one payment (cash-out tracking), read with the association's token. */
    fun getPayment(accessToken: String, paymentId: Long): HelloAssoPaymentDetailJson
}
