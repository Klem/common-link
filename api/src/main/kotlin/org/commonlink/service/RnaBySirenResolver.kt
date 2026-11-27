package org.commonlink.service

import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.client.SimpleClientHttpRequestFactory
import org.springframework.stereotype.Service
import org.springframework.web.client.RestTemplate
import org.springframework.web.util.UriComponentsBuilder
import java.time.Duration

/**
 * Finds the RNA the Recherche d'entreprises registry associates with a SIREN.
 *
 * Used when an association signs up with its SIREN: the RNA, when the registry knows one, becomes its primary
 * identifier, so later checks that rely on the RNA (HelloAsso link, JOAFE step of the curator scan) can run.
 *
 * **Best effort, never blocking**: any failure (timeout, HTTP error, unparsable body, no exact SIREN match, value not
 * shaped like a RNA) returns null and the sign-up keeps the SIREN. Called inside the sign-up transaction, hence its
 * own short timeouts ([TIMEOUT]) instead of the shared, timeout-less RestTemplate.
 *
 * Server-side on purpose (project rule 8): the legal identifier is resolved by CommonLink, never taken from the
 * browser. One call per profile creation, so the shared server IP stays far below the registry quota.
 */
@Service
class RnaBySirenResolver(
    private val objectMapper: ObjectMapper,
    @Value("\${app.recherche-entreprises.base-url}") private val baseUrl: String,
) {
    private val log = LoggerFactory.getLogger(RnaBySirenResolver::class.java)

    companion object {
        /** Connect and read timeout of the registry call. */
        val TIMEOUT: Duration = Duration.ofSeconds(2)
    }

    /** Dedicated client with short timeouts; replaced in tests. */
    internal var restTemplate: RestTemplate = RestTemplate(
        SimpleClientHttpRequestFactory().apply {
            setConnectTimeout(TIMEOUT)
            setReadTimeout(TIMEOUT)
        }
    )

    /**
     * @param siren a 9-digit SIREN.
     * @return the RNA of the registry entry whose SIREN is exactly [siren], or null when there is none or the
     *   registry cannot be reached.
     */
    fun resolve(siren: String): String? {
        return try {
            val uri = UriComponentsBuilder.fromUriString("$baseUrl/search")
                .queryParam("q", siren)
                .queryParam("per_page", 10)
                .build().encode().toUri()
            val body = restTemplate.getForObject(uri, String::class.java) ?: return null
            val entry = RechercheEntreprisesResults.findBySiren(
                RechercheEntreprisesResults.entries(objectMapper.readTree(body)), siren,
            ) ?: return null
            RechercheEntreprisesResults.rnaOf(entry)?.uppercase()
                ?.takeIf { RechercheEntreprisesResults.RNA_PATTERN.matches(it) }
        } catch (ex: Exception) {
            log.warn("RNA lookup by SIREN failed: {}", ex.javaClass.simpleName)
            null
        }
    }
}
