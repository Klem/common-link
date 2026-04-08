package org.commonlink.service

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import org.commonlink.dto.SireneSearchResultDto
import org.commonlink.exception.BadGatewayException
import org.commonlink.exception.NotFoundException
import org.commonlink.exception.RateLimitException
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.client.JdkClientHttpRequestFactory
import org.springframework.stereotype.Service
import org.springframework.web.client.RestClient
import java.net.http.HttpClient
import java.time.Duration

/**
 * Service that proxies calls to the INSEE Sirene 3.11 API and returns a simplified DTO.
 *
 * Only the ~13 fields required by the frontend are extracted from the raw INSEE response.
 * The raw response is never forwarded to the client.
 *
 * Authentication with INSEE uses the `X-INSEE-Api-Key-Integration` header with the key
 * configured via the `app.insee.api-key` property.
 *
 * @property apiKey INSEE API key, injected from `app.insee.api-key`.
 * @property baseUrl Base URL of the Sirene 3.11 API, injected from `app.insee.base-url`.
 * @property objectMapper Jackson mapper used to parse the INSEE JSON response.
 */
@Service
class SireneSearchService(
    @Value("\${app.insee.api-key}") private val apiKey: String,
    @Value("\${app.insee.base-url:https://api.insee.fr/api-sirene/3.11}") private val baseUrl: String,
    private val objectMapper: ObjectMapper
) {

    private val restClient: RestClient = buildRestClient()


    /**
     * Searches the INSEE Sirene API by SIREN (9 digits) or SIRET (14 digits).
     *
     * Server-side validation is mandatory: the frontend strips non-digit characters and
     * enforces length, but any API call can be replayed directly, so this method applies
     * the same rules independently.
     *
     * @param query Raw input from the caller; non-digit characters are stripped before validation.
     * @return A simplified [SireneSearchResultDto] with only the key fields extracted.
     * @throws IllegalArgumentException if the sanitised query is neither 9 nor 14 digits.
     * @throws NotFoundException if the INSEE API returns 404.
     * @throws BadGatewayException if the INSEE API is unreachable or returns a 5xx / auth error.
     * @throws RateLimitException if the INSEE API returns 429.
     */
    fun search(query: String): SireneSearchResultDto {
        val digits = query.replace(Regex("[^0-9]"), "")
        if (digits.length != 9 && digits.length != 14) {
            throw IllegalArgumentException("Invalid identifier format")
        }

        val isSiren = digits.length == 9
        val endpoint = if (isSiren) "siren" else "siret"

        val responseBody = try {
            restClient.get()
                .uri("$baseUrl/$endpoint/$digits")
                .header("X-INSEE-Api-Key-Integration", apiKey)
                .retrieve()
                .onStatus({ it.value() == 404 }) { _, _ ->
                    throw NotFoundException("No establishment found for this identifier")
                }
                .onStatus({ it.value() == 401 || it.value() == 403 }) { _, _ ->
                    throw BadGatewayException("INSEE API authentication error")
                }
                .onStatus({ it.value() == 429 }) { _, _ ->
                    throw RateLimitException("INSEE API rate limit exceeded")
                }
                .onStatus({ it.is5xxServerError }) { _, _ ->
                    throw BadGatewayException("INSEE API temporarily unavailable")
                }
                .body(String::class.java)
                ?: throw BadGatewayException("Empty response from INSEE API")
        } catch (e: NotFoundException) {
            throw e
        } catch (e: BadGatewayException) {
            throw e
        } catch (e: RateLimitException) {
            throw e
        } catch (e: IllegalArgumentException) {
            throw e
        } catch (e: Exception) {
            throw BadGatewayException("Cannot reach INSEE API")
        }

        val root = objectMapper.readTree(responseBody)
        return if (isSiren) parseSiren(root) else parseSiret(root)
    }

    /**
     * Extracts simplified fields from a SIREN-type response.
     *
     * The `uniteLegale.periodesUniteLegale[0]` entry holds the most recent legal period data.
     */
    private fun parseSiren(root: JsonNode): SireneSearchResultDto {
        val unite = root["uniteLegale"]
        val period = unite["periodesUniteLegale"]?.firstOrNull()

        val denomination = period?.get("denominationUniteLegale")?.textOrNull()
        val prenom = unite["prenom1UniteLegale"]?.textOrNull()
        val nom = period?.get("nomUniteLegale")?.textOrNull()
        val name = denomination ?: listOfNotNull(prenom, nom).joinToString(" ").ifBlank { "Unknown" }

        return SireneSearchResultDto(
            siren = unite["siren"]?.asText() ?: "",
            siret = null,
            name = name,
            nafCode = period?.get("activitePrincipaleUniteLegale")?.textOrNull(),
            category = unite["categorieEntreprise"]?.textOrNull(),
            city = null,
            postalCode = null,
            address = null,
            active = period?.get("etatAdministratifUniteLegale")?.asText() == "A",
            isSiege = false,
            isEss = period?.get("economieSocialeSolidaireUniteLegale")?.asText() == "O",
            creationDate = unite["dateCreationUniteLegale"]?.textOrNull(),
            employeeRange = unite["trancheEffectifsUniteLegale"]?.textOrNull()
        )
    }

    /**
     * Extracts simplified fields from a SIRET-type response.
     *
     * Legal unit details come from `etablissement.uniteLegale`; address details come from
     * `etablissement.adresseEtablissement`.
     */
    private fun parseSiret(root: JsonNode): SireneSearchResultDto {
        val etab = root["etablissement"]
        val unite = etab["uniteLegale"]
        val period = unite["periodesUniteLegale"]?.firstOrNull()
        val periodEtab = etab["periodesEtablissement"]?.firstOrNull()
        val adresse = etab["adresseEtablissement"]

        val denomination = period?.get("denominationUniteLegale")?.textOrNull()
        val prenom = unite["prenom1UniteLegale"]?.textOrNull()
        val nom = period?.get("nomUniteLegale")?.textOrNull()
        val name = denomination ?: listOfNotNull(prenom, nom).joinToString(" ").ifBlank { "Unknown" }

        val addressParts = listOfNotNull(
            adresse?.get("numeroVoieEtablissement")?.textOrNull(),
            adresse?.get("typeVoieEtablissement")?.textOrNull(),
            adresse?.get("libelleVoieEtablissement")?.textOrNull()
        )
        val address = addressParts.joinToString(" ").trim().ifBlank { null }

        return SireneSearchResultDto(
            siren = etab["siren"]?.asText() ?: "",
            siret = etab["siret"]?.asText(),
            name = name,
            nafCode = period?.get("activitePrincipaleUniteLegale")?.textOrNull(),
            category = unite["categorieEntreprise"]?.textOrNull(),
            city = adresse?.get("libelleCommuneEtablissement")?.textOrNull(),
            postalCode = adresse?.get("codePostalEtablissement")?.textOrNull(),
            address = address,
            active = periodEtab?.get("etatAdministratifEtablissement")?.asText() == "A",
            isSiege = etab["etablissementSiege"]?.asBoolean() ?: false,
            isEss = period?.get("economieSocialeSolidaireUniteLegale")?.asText() == "O",
            creationDate = etab["dateCreationEtablissement"]?.textOrNull(),
            employeeRange = etab["trancheEffectifsEtablissement"]?.textOrNull()
        )
    }

    /**
     * Returns the text value of this [JsonNode] if it is a non-null, non-empty string; otherwise null.
     */
    private fun JsonNode.textOrNull(): String? {
        val text = this.asText(null)
        return if (text.isNullOrBlank()) null else text
    }

    /**
     * Builds the [RestClient] with explicit connect (10 s) and read (15 s) timeouts.
     */
    private fun buildRestClient(): RestClient {
        val httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build()
        val factory = JdkClientHttpRequestFactory(httpClient)
        factory.setReadTimeout(Duration.ofSeconds(15))
        return RestClient.builder()
            .requestFactory(factory)
            .build()
    }
}
