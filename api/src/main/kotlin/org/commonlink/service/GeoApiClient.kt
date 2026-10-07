package org.commonlink.service

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import org.commonlink.exception.BadGatewayException
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.client.JdkClientHttpRequestFactory
import org.springframework.stereotype.Service
import org.springframework.web.client.RestClient
import org.springframework.web.client.RestClientResponseException
import java.net.http.HttpClient
import java.time.Duration

/**
 * A French commune as returned by geo.api.gouv.fr.
 *
 * @property code INSEE commune code.
 * @property name Official commune name.
 * @property departmentCode Code of the department the commune belongs to.
 * @property latitude WGS84 latitude of the commune centre, null when the API returns none.
 * @property longitude WGS84 longitude of the commune centre, null when the API returns none.
 */
data class GeoCommune(
    val code: String,
    val name: String,
    val departmentCode: String?,
    val latitude: Double?,
    val longitude: Double?,
)

/**
 * A French department as returned by geo.api.gouv.fr.
 *
 * @property code Department code (`06`, `2A`, `974`…).
 * @property name Official department name.
 */
data class GeoDepartment(val code: String, val name: String)

/**
 * Thin client over the public, key-less "API Découpage administratif" (geo.api.gouv.fr).
 *
 * Used to validate and resolve a campaign's place of action server-side: the label and the
 * coordinates are always taken from this API, never from the client payload.
 *
 * Lookups by code answer `null` on 404 (unknown code). Any other failure (timeout, 5xx, network)
 * is raised as [BadGatewayException] so the caller can tell "invalid input" from "upstream down".
 *
 * @property baseUrl API base URL, injected from `app.geo.base-url`.
 * @property objectMapper Jackson mapper used to parse responses.
 */
@Service
class GeoApiClient(
    @Value("\${app.geo.base-url:https://geo.api.gouv.fr}") private val baseUrl: String,
    private val objectMapper: ObjectMapper,
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    private val restClient: RestClient = RestClient.builder()
        .requestFactory(
            JdkClientHttpRequestFactory(
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build()
            ).apply { setReadTimeout(Duration.ofSeconds(5)) }
        )
        .build()

    /**
     * Looks up a commune by INSEE code.
     *
     * @return the commune, or null when the code is unknown.
     * @throws BadGatewayException if geo.api.gouv.fr is unreachable or fails.
     */
    fun findCommune(code: String): GeoCommune? =
        fetch("/communes/$code?fields=nom,code,centre,codeDepartement", ::toCommune)

    /**
     * Looks up a department by code.
     *
     * @return the department, or null when the code is unknown.
     * @throws BadGatewayException if geo.api.gouv.fr is unreachable or fails.
     */
    fun findDepartment(code: String): GeoDepartment? =
        fetch("/departements/$code?fields=nom,code") { GeoDepartment(it.textField("code"), it.textField("nom")) }

    /**
     * Finds the communes served by a postal code (several communes can share one).
     *
     * @return matching communes, empty when none.
     * @throws BadGatewayException if geo.api.gouv.fr is unreachable or fails.
     */
    fun findCommunesByPostalCode(postalCode: String): List<GeoCommune> =
        fetch("/communes?codePostal=$postalCode&fields=nom,code,centre,codeDepartement") { it.map(::toCommune) }
            .orEmpty()

    /**
     * GETs [path] and maps the JSON body with [map]. A 404 yields null; any transport error,
     * non-404 error status or unexpected payload (unparseable JSON, missing field) is raised as
     * [BadGatewayException] -- never as a raw parsing exception.
     */
    private fun <T> fetch(path: String, map: (JsonNode) -> T): T? {
        try {
            val body = restClient.get().uri("$baseUrl$path").retrieve().body(String::class.java) ?: return null
            return map(objectMapper.readTree(body))
        } catch (e: RestClientResponseException) {
            if (e.statusCode.value() == 404) return null
            logger.warn("geo.api.gouv.fr answered {} for {}", e.statusCode.value(), path)
            throw BadGatewayException("Geographic reference service unavailable")
        } catch (e: Exception) {
            logger.warn("geo.api.gouv.fr unreachable or unexpected payload for {}: {}", path, e.message)
            throw BadGatewayException("Geographic reference service unavailable")
        }
    }

    private fun JsonNode.textField(field: String): String =
        get(field)?.takeIf { it.isTextual }?.asText()
            ?: throw IllegalStateException("geo.api.gouv.fr payload without '$field'")

    private fun toCommune(node: JsonNode): GeoCommune {
        // GeoJSON order: [longitude, latitude].
        val coordinates = node["centre"]?.get("coordinates")
        return GeoCommune(
            code = node.textField("code"),
            name = node.textField("nom"),
            departmentCode = node["codeDepartement"]?.asText(),
            latitude = coordinates?.get(1)?.asDouble(),
            longitude = coordinates?.get(0)?.asDouble(),
        )
    }
}
