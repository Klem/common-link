package org.commonlink.service

import org.commonlink.config.MoneriumConfig
import org.commonlink.dto.monerium.LinkAddressRequest
import org.commonlink.dto.monerium.MoneriumAddressDto
import org.commonlink.dto.monerium.MoneriumIbanDto
import org.commonlink.dto.monerium.MoneriumProfileDto
import org.commonlink.dto.monerium.MoneriumProfileListDto
import org.commonlink.dto.monerium.RequestIbanRequest
import org.commonlink.exception.MoneriumReauthRequiredException
import org.slf4j.LoggerFactory
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.stereotype.Service
import org.springframework.web.client.HttpStatusCodeException
import org.springframework.web.client.RestTemplate
import java.util.UUID

/**
 * Thin typed wrapper over the Monerium API v2 endpoints CommonLink uses for association
 * onboarding (list/get profile, link address, request IBAN).
 *
 * Callers identify the association by id; this class delegates token acquisition to
 * [MoneriumService.getValidAccessToken] which transparently refreshes when stale. Every
 * request carries `Accept: application/vnd.monerium.api-v2+json` (the v2 content-negotiation
 * header — without it Monerium returns 404). On 401 the access token is refreshed once and
 * the call retried; a second 401 escalates to [MoneriumReauthRequiredException].
 */
@Service
class MoneriumApiClient(
    private val moneriumService: MoneriumService,
    private val config: MoneriumConfig,
    private val restTemplate: RestTemplate,
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    /** List all profiles the association's token can act on. Returns empty list if none. */
    fun listProfiles(associationId: UUID): List<MoneriumProfileDto> {
        val response = exchange<MoneriumProfileListDto>(
            associationId, HttpMethod.GET, "/profiles", null,
        )
        return response.profiles
    }

    /** Get a single profile by id. */
    fun getProfile(associationId: UUID, profileId: String): MoneriumProfileDto =
        exchange(associationId, HttpMethod.GET, "/profiles/$profileId", null)

    /**
     * Link a blockchain address to a profile. The signature in [request] must be produced
     * by the wallet's private key on the frontend; the backend never holds it.
     */
    fun linkAddress(associationId: UUID, request: LinkAddressRequest): MoneriumAddressDto =
        exchange(associationId, HttpMethod.POST, "/addresses", request)

    /** Request a new IBAN for an already-linked address. */
    fun requestIban(associationId: UUID, request: RequestIbanRequest): MoneriumIbanDto =
        exchange(associationId, HttpMethod.POST, "/ibans", request)

    /**
     * Sends an authed request. On 401 forces a refresh and retries once with the new token;
     * if the retry also 401s the original HTTP exception propagates.
     * [MoneriumReauthRequiredException] bubbles up if [MoneriumService.forceRefreshAccessToken]
     * itself decides the connection is broken.
     */
    private inline fun <reified T : Any> exchange(
        associationId: UUID,
        method: HttpMethod,
        path: String,
        body: Any?,
    ): T {
        val firstToken = moneriumService.getValidAccessToken(associationId)
        val response = try {
            doExchangeWithToken(firstToken, method, path, body, T::class.java)
        } catch (ex: HttpStatusCodeException) {
            if (ex.statusCode != HttpStatus.UNAUTHORIZED) throw ex
            logger.info("Monerium 401 on {} {} — forcing refresh + retry", method, path)
            val freshToken = moneriumService.forceRefreshAccessToken(associationId)
            doExchangeWithToken(freshToken, method, path, body, T::class.java)
        }
        return response.body ?: throw IllegalStateException("Empty Monerium response from $method $path")
    }

    private fun <T : Any> doExchangeWithToken(
        accessToken: String,
        method: HttpMethod,
        path: String,
        body: Any?,
        responseType: Class<T>,
    ): ResponseEntity<T> {
        val headers = HttpHeaders().apply {
            setBearerAuth(accessToken)
            accept = listOf(MediaType.parseMediaType(API_ACCEPT))
            if (body != null) contentType = MediaType.APPLICATION_JSON
        }
        return restTemplate.exchange(
            "${config.baseUrl}$path",
            method,
            HttpEntity(body, headers),
            responseType,
        )
    }

    companion object {
        /** Monerium v2 content-negotiation header — required on every data endpoint. */
        const val API_ACCEPT = "application/vnd.monerium.api-v2+json"
    }
}
