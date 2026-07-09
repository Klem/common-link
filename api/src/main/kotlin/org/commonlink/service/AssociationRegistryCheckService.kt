package org.commonlink.service

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import org.commonlink.dto.RegistryPreCheckDto
import org.commonlink.repository.AssociationProfileRepository
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.web.client.RestTemplate
import org.springframework.web.server.ResponseStatusException
import java.net.URLEncoder
import java.time.Instant
import java.util.UUID

/**
 * Performs a best-effort legal-existence pre-check for an association by querying
 * French public registries (Recherche d'entreprises, INSEE Sirene, JOAFE, BODACC).
 *
 * All external calls degrade gracefully: a failure adds a warning to the result
 * but never blocks or influences the manual KYC review.
 */
@Service
class AssociationRegistryCheckService(
    private val associationProfileRepository: AssociationProfileRepository,
    private val restTemplate: RestTemplate,
    private val objectMapper: ObjectMapper,
    @Value("\${app.insee.api-key}") private val inseeApiKey: String,
    @Value("\${app.insee.base-url}") private val inseeBaseUrl: String,
    @Value("\${app.joafe.base-url}") private val joafeBaseUrl: String,
    @Value("\${app.bodacc.base-url}") private val bodaccBaseUrl: String,
) {

    private val log = LoggerFactory.getLogger(AssociationRegistryCheckService::class.java)

    fun check(associationId: UUID): RegistryPreCheckDto {
        val profile = associationProfileRepository.findById(associationId)
            .orElseThrow { ResponseStatusException(HttpStatus.NOT_FOUND, "Association not found: $associationId") }

        val warnings = mutableListOf<String>()

        // Prefer the full RNA number if available; fall back to the 9-char identifier.
        val lookupKey = profile.rna?.takeIf { it.isNotBlank() } ?: profile.identifier

        // ── Step 1: Recherche d'entreprises ──────────────────────────────────────
        var associationExists: Boolean? = null
        var sirenFound: String? = null
        var rnaFound: String? = null

        try {
            val url = "https://recherche-entreprises.api.gouv.fr/search?q=${enc(lookupKey)}&limit=1"
            restTemplate.getForObject(url, String::class.java)?.let { body ->
                val results: JsonNode = objectMapper.readTree(body).path("results")
                if (results.isArray && results.size() > 0) {
                    val first: JsonNode = results[0]
                    val estAssociation = first.path("complements").path("est_association").asBoolean(false)
                    val natureJuridique = first.path("nature_juridique").asText("")
                    associationExists = estAssociation || natureJuridique == "9220"
                    sirenFound = first.path("siren").asText("").takeIf { it.isNotBlank() }
                    rnaFound = first.path("identifiant_association").asText("").takeIf { it.isNotBlank() }
                } else {
                    associationExists = false
                }
            }
        } catch (ex: Exception) {
            log.warn("Recherche d'entreprises lookup failed for identifier={}: {}", lookupKey, ex.message)
            warnings.add("recherche-entreprises: ${ex.message ?: "unavailable"}")
        }

        // ── Step 2: INSEE Sirene (requires SIREN) ────────────────────────────────
        var etatAdministratif: String? = null

        sirenFound?.let { siren ->
            try {
                val url = "$inseeBaseUrl/siren/$siren"
                val headers = HttpHeaders().apply {
                    set("X-INSEE-Api-Key-Integration", inseeApiKey)
                    set("Accept", "application/json")
                }
                val response = restTemplate.exchange(url, HttpMethod.GET, HttpEntity<Unit>(headers), String::class.java)
                response.body?.let { body ->
                    val tree: JsonNode = objectMapper.readTree(body)
                    etatAdministratif = tree
                        .path("uniteLegale")
                        .path("etatAdministratifUniteLegale")
                        .asText("")
                        .takeIf { it.isNotBlank() }
                }
            } catch (ex: Exception) {
                log.warn("INSEE Sirene lookup failed for SIREN={}: {}", siren, ex.message)
                warnings.add("insee-sirene: ${ex.message ?: "unavailable"}")
            }
        }

        // ── Step 3: JOAFE (requires RNA W number) ────────────────────────────────
        var joafeDeclarationFound: Boolean? = null
        var dissolutionDetected: Boolean? = null

        val rnaForJoafe = rnaFound ?: profile.rna?.takeIf { it.isNotBlank() }
        rnaForJoafe?.let { rna ->
            try {
                val url = "$joafeBaseUrl/catalog/datasets/jo_associations/records?q=${enc(rna)}&limit=10"
                restTemplate.getForObject(url, String::class.java)?.let { body ->
                    val records: JsonNode = objectMapper.readTree(body).path("results")
                    val matching = if (records.isArray) (0 until records.size()).filter { i ->
                        records[i].path("numero_rna").asText("").equals(rna, ignoreCase = true)
                    } else emptyList()
                    if (matching.isNotEmpty()) {
                        joafeDeclarationFound = true
                        dissolutionDetected = matching.any { i ->
                            records[i].path("typeavis").asText("").contains("dissolution", ignoreCase = true)
                        }
                    } else {
                        joafeDeclarationFound = false
                        dissolutionDetected = false
                    }
                }
            } catch (ex: Exception) {
                log.warn("JOAFE lookup failed for RNA={}: {}", rna, ex.message)
                warnings.add("joafe: ${ex.message ?: "unavailable"}")
            }
        }

        // ── Step 4: BODACC (requires SIREN, insolvency filter) ───────────────────
        var bodaccProcedureFound: Boolean? = null

        sirenFound?.let { siren ->
            try {
                val url = "$bodaccBaseUrl/catalog/datasets/annonces-commerciales/records?q=${enc(siren)}&limit=10"
                restTemplate.getForObject(url, String::class.java)?.let { body ->
                    val records: JsonNode = objectMapper.readTree(body).path("results")
                    bodaccProcedureFound = records.isArray && (0 until records.size()).any { i ->
                        records[i].path("familleavis").asText("").equals("pc", ignoreCase = true)
                    }
                }
            } catch (ex: Exception) {
                log.warn("BODACC lookup failed for SIREN={}: {}", siren, ex.message)
                warnings.add("bodacc: ${ex.message ?: "unavailable"}")
            }
        }

        return RegistryPreCheckDto(
            associationExists = associationExists,
            siren = sirenFound,
            rna = rnaFound ?: rnaForJoafe,
            etatAdministratif = etatAdministratif,
            joafeDeclarationFound = joafeDeclarationFound,
            dissolutionDetected = dissolutionDetected,
            bodaccProcedureFound = bodaccProcedureFound,
            checkedAt = Instant.now(),
            warnings = warnings,
        )
    }

    private fun enc(value: String): String =
        URLEncoder.encode(value, Charsets.UTF_8).replace("+", "%20")
}
