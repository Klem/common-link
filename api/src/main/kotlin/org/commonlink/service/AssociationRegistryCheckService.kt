package org.commonlink.service

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import org.commonlink.dto.RegistryPreCheckDto
import org.commonlink.entity.AssociationProfile
import org.commonlink.entity.AssociationRegistryCheck
import org.commonlink.repository.AssociationProfileRepository
import org.commonlink.repository.AssociationRegistryCheckRepository
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.web.client.RestTemplate
import org.springframework.web.server.ResponseStatusException
import org.springframework.web.util.UriComponentsBuilder
import java.net.URLEncoder
import java.util.UUID

/**
 * Performs a best-effort legal-existence pre-check for an association by querying
 * French public registries (Recherche d'entreprises, INSEE Sirene, JOAFE, BODACC).
 *
 * All external calls degrade gracefully: a failure adds a warning to the result
 * but never blocks or influences the manual KYC review.
 *
 * Scans are persisted append-only ([AssociationRegistryCheckRepository]) to keep an
 * LCB-FT audit trail. [scan] runs a live check and stores a new row; [latest] reads the
 * most recent stored scan without contacting any external registry.
 */
@Service
class AssociationRegistryCheckService(
    private val associationProfileRepository: AssociationProfileRepository,
    private val registryCheckRepository: AssociationRegistryCheckRepository,
    private val restTemplate: RestTemplate,
    private val objectMapper: ObjectMapper,
    @Value("\${app.insee.api-key}") private val inseeApiKey: String,
    @Value("\${app.insee.base-url}") private val inseeBaseUrl: String,
    @Value("\${app.joafe.base-url}") private val joafeBaseUrl: String,
    @Value("\${app.bodacc.base-url}") private val bodaccBaseUrl: String,
    @Value("\${app.recherche-entreprises.base-url}") private val rechercheEntreprisesBaseUrl: String,
) {

    private val log = LoggerFactory.getLogger(AssociationRegistryCheckService::class.java)

    /**
     * Runs a live registry scan, persists it as a new immutable row, and returns the result.
     * @param checkedBy UUID of the curator who triggered the scan (for the audit trail).
     */
    fun scan(associationId: UUID, checkedBy: UUID?): RegistryPreCheckDto {
        val profile = associationProfileRepository.findById(associationId)
            .orElseThrow { ResponseStatusException(HttpStatus.NOT_FOUND, "Association not found: $associationId") }

        val check = runLiveCheck(profile, checkedBy)
        val saved = registryCheckRepository.save(check)
        log.info("Registry pre-check scan persisted id={} association={} by={}", saved.id, associationId, checkedBy)
        return saved.toDto()
    }

    /** Returns the most recent persisted scan for the association, or null if none exists. No external calls. */
    fun latest(associationId: UUID): RegistryPreCheckDto? =
        registryCheckRepository.findTopByAssociationIdOrderByCheckedAtDesc(associationId)?.toDto()

    /**
     * Queries every registry and assembles an unsaved [AssociationRegistryCheck] row.
     *
     * [AssociationProfile.identifier] holds the RNA for JOAFE registrations or the SIREN for legacy rows.
     * [AssociationProfile.siren] holds the SIREN when both identifiers are known. The SIREN-based checks
     * (INSEE, BODACC) and the RNA-based check (JOAFE) are run independently when available — neither is skipped in favor
     * of the other.
     */
    private fun runLiveCheck(profile: AssociationProfile, checkedBy: UUID?): AssociationRegistryCheck {
        val warnings = mutableListOf<String>()

        // identifier holds RNA for JOAFE registrations (starts with "W"), SIREN for legacy rows
        val siren = profile.siren ?: profile.identifier.takeUnless { it.startsWith("W") }
        val rnaFromProfile = profile.identifier.takeIf { it.startsWith("W") }

        // ── Step 1: Recherche d'entreprises (searched by SIREN, the mandatory identifier) ─────
        var associationExists: Boolean? = null
        var rnaFromSearch: String? = null

        if (siren != null) {
            try {
                val url = "$rechercheEntreprisesBaseUrl/search?q=${enc(siren)}&limit=1"
                restTemplate.getForObject(url, String::class.java)?.let { body ->
                    val results: JsonNode = objectMapper.readTree(body).path("results")
                    if (results.isArray && results.size() > 0) {
                        val first: JsonNode = results[0]
                        val estAssociation = first.path("complements").path("est_association").asBoolean(false)
                        val natureJuridique = first.path("nature_juridique").asText("")
                        associationExists = estAssociation || natureJuridique == "9220"
                        rnaFromSearch = first.path("identifiant_association").asText("").takeIf { it.isNotBlank() }
                    } else {
                        associationExists = false
                    }
                }
            } catch (ex: Exception) {
                log.warn("Recherche d'entreprises lookup failed for SIREN={}: {}", siren, ex.message)
                warnings.add("recherche-entreprises: ${ex.message ?: "unavailable"}")
            }
        }

        // ── Step 2: INSEE Sirene (SIREN always available from the profile) ──────────────────────
        var etatAdministratif: String? = null

        if (siren != null) {
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

        // ── Step 3: JOAFE (RNA — cumulated independently of the SIREN checks above) ────────────
        var joafeDeclarationFound: Boolean? = null
        var dissolutionDetected: Boolean? = null

        val rna = rnaFromProfile ?: rnaFromSearch
        rna?.let {
            try {
                val uri = UriComponentsBuilder.fromUriString("$joafeBaseUrl/catalog/datasets/jo_associations/records")
                    .queryParam("where", "numero_rna='$rna'")
                    .queryParam("limit", 10)
                    .build().encode().toUri()
                restTemplate.getForObject(uri, String::class.java)?.let { body ->
                    val records: JsonNode = objectMapper.readTree(body).path("records")
                    val fieldsList = if (records.isArray) (0 until records.size()).map { i ->
                        records[i].path("record").path("fields")
                    } else emptyList()
                    val matching = fieldsList.filter { fields ->
                        fields.path("numero_rna").asText("").equals(rna, ignoreCase = true)
                    }
                    if (matching.isNotEmpty()) {
                        joafeDeclarationFound = true
                        dissolutionDetected = matching.any { fields ->
                            fields.path("typeavis").asText("").contains("dissolution", ignoreCase = true)
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

        // ── Step 4: BODACC (SIREN, insolvency filter) ───────────────────────────────────────────
        var bodaccProcedureFound: Boolean? = null

        if (siren != null) {
            try {
                val uri = UriComponentsBuilder.fromUriString("$bodaccBaseUrl/catalog/datasets/annonces-commerciales/records")
                    .queryParam("where", "registre like '%$siren%'")
                    .queryParam("limit", 10)
                    .build().encode().toUri()
                restTemplate.getForObject(uri, String::class.java)?.let { body ->
                    val records: JsonNode = objectMapper.readTree(body).path("records")
                    val fieldsList = if (records.isArray) (0 until records.size()).map { i ->
                        records[i].path("record").path("fields")
                    } else emptyList()
                    bodaccProcedureFound = fieldsList.any { fields ->
                        fields.path("familleavis").asText("").equals("pc", ignoreCase = true)
                    }
                }
            } catch (ex: Exception) {
                log.warn("BODACC lookup failed for SIREN={}: {}", siren, ex.message)
                warnings.add("bodacc: ${ex.message ?: "unavailable"}")
            }
        }

        return AssociationRegistryCheck(
            associationId = profile.id!!,
            associationExists = associationExists,
            siren = siren,
            rna = rna,
            etatAdministratif = etatAdministratif,
            joafeDeclarationFound = joafeDeclarationFound,
            dissolutionDetected = dissolutionDetected,
            bodaccProcedureFound = bodaccProcedureFound,
            warnings = warnings.toList(),
            checkedBy = checkedBy,
        )
    }

    private fun AssociationRegistryCheck.toDto() = RegistryPreCheckDto(
        id = id!!,
        associationExists = associationExists,
        siren = siren,
        rna = rna,
        etatAdministratif = etatAdministratif,
        joafeDeclarationFound = joafeDeclarationFound,
        dissolutionDetected = dissolutionDetected,
        bodaccProcedureFound = bodaccProcedureFound,
        checkedAt = checkedAt,
        warnings = warnings,
    )

    private fun enc(value: String): String =
        URLEncoder.encode(value, Charsets.UTF_8).replace("+", "%20")
}
