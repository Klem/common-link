package org.commonlink.service

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import org.commonlink.dto.RegistryPreCheckDto
import org.commonlink.entity.ACCEPTED_LEGAL_CATEGORIES
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

    private companion object {
        /** Recherche d'entreprises rejects query terms shorter than this with a 400. */
        const val MIN_SEARCH_KEY_LENGTH = 3
    }

    /**
     * Runs a live registry scan, persists it as a new immutable row, and returns the result.
     * @param checkedBy UUID of the curator who triggered the scan (for the audit trail).
     */
    @org.springframework.transaction.annotation.Transactional
    fun scan(associationId: UUID, checkedBy: UUID?): RegistryPreCheckDto {
        val profile = associationProfileRepository.findById(associationId)
            .orElseThrow { ResponseStatusException(HttpStatus.NOT_FOUND, "Association not found: $associationId") }

        val check = runLiveCheck(profile, checkedBy)  // may update profile.addressLine1 / legalObject; dirty-checking flushes on commit
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
     * [AssociationProfile.siren] holds the SIREN when both identifiers are known.
     *
     * **No registry check is gated on a declared SIREN.** Step 1 (Recherche d'entreprises) is searched by
     * SIREN when one is known and by RNA otherwise; the record it returns carries the SIREN, which then
     * unlocks the SIREN-keyed steps (INSEE, BODACC) for RNA-only associations. Step 3 (JOAFE) runs on the
     * RNA independently, and additionally determines `rnaActive` for associations that never obtained a
     * SIREN and are therefore absent from Recherche d'entreprises altogether.
     */

    private fun runLiveCheck(profile: AssociationProfile, checkedBy: UUID?): AssociationRegistryCheck {
        val warnings = mutableListOf<String>()
        val officers = mutableListOf<String>()

        // identifier holds RNA for JOAFE registrations (starts with "W"), SIREN for legacy rows.
        // Both columns are nullable *and* routinely hold an empty string when the operator left the
        // field untouched, so blank must be read as absent — an elvis on null alone would carry ""
        // into the query and get rejected by the registry.
        val identifier = profile.identifier.trim()
        val rnaFromProfile = identifier.takeIf { it.startsWith("W", ignoreCase = true) }
        val sirenFromProfile = profile.siren?.trim()?.takeIf { it.isNotEmpty() }
            ?: identifier.takeIf { it.isNotEmpty() && rnaFromProfile == null }

        // ── Step 1: Recherche d'entreprises (searched by SIREN when known, by RNA otherwise) ──
        var associationExists: Boolean? = null
        var rnaFromSearch: String? = null
        var sirenFromSearch: String? = null
        var legalCategory: String? = null
        var rnaActive: Boolean? = null
        var rechercheEntreprisesFailed = false

        val searchKey = sirenFromProfile ?: rnaFromProfile
        if (searchKey == null || searchKey.length < MIN_SEARCH_KEY_LENGTH) {
            // `identifier` is NOT NULL in the schema but nothing stops it holding "". Absent or
            // unusably short, no registry can be queried at all: record it as a source failure so the
            // scan reads as inconclusive rather than as a reassuring empty result.
            log.warn("No usable registry search key for association {}: identifier='{}'", profile.id, identifier)
            warnings.add("recherche-entreprises: no usable identifier to query the registry")
            rechercheEntreprisesFailed = true
        } else {
            try {
                val url = "$rechercheEntreprisesBaseUrl/search?q=${enc(searchKey)}&per_page=10"
                restTemplate.getForObject(url, String::class.java)?.let { body ->
                    val results: JsonNode = objectMapper.readTree(body).path("results")
                    val candidates = if (results.isArray) (0 until results.size()).map { results[it] } else emptyList()
                    // The endpoint is a full-text search: a W-number query is not guaranteed to rank the
                    // right entity first, so the RNA path only accepts a record whose RNA matches exactly.
                    // A declared SIREN is unambiguous and keeps the historical top-hit behaviour.
                    val match: JsonNode? = if (sirenFromProfile != null) {
                        candidates.firstOrNull()
                    } else {
                        candidates.firstOrNull {
                            it.path("identifiant_association").asText("").equals(rnaFromProfile, ignoreCase = true)
                        }
                    }
                    if (match != null) {
                        val estAssociation = match.path("complements").path("est_association").asBoolean(false)
                        val natureJuridique = match.path("nature_juridique").asText("").takeIf { it.isNotBlank() }
                        legalCategory = natureJuridique
                        // Deliberately broader than the perimeter: this flag answers "was the entity
                        // found, and does it present itself as an association?" and is informational.
                        // Eligibility is [ScopeVerdict] alone, derived from legalCategory — it is the
                        // only blocking signal. The two may legitimately disagree: an entity of an
                        // excluded 92xx form shows as found *and* out of scope, which is what the
                        // curator needs to see.
                        associationExists = estAssociation || natureJuridique in ACCEPTED_LEGAL_CATEGORIES
                        rnaActive = estAssociation  // RNA-native: recherche-entreprises aggregates from RNA
                        rnaFromSearch = match.path("identifiant_association").asText("").takeIf { it.isNotBlank() }
                        sirenFromSearch = match.path("siren").asText("").takeIf { it.isNotBlank() }
                        val dirigeants: JsonNode = match.path("dirigeants")
                        if (dirigeants.isArray) {
                            (0 until dirigeants.size()).forEach { i ->
                                val d = dirigeants[i]
                                val prenoms = d.path("prenoms").asText("").trim()
                                val nom = d.path("nom").asText("").trim()
                                val name = "$prenoms $nom".trim()
                                if (name.isNotBlank()) officers.add(name)
                            }
                        }
                    } else if (sirenFromProfile != null) {
                        associationExists = false
                    }
                    // RNA path with no matching record: associationExists stays null. Recherche d'entreprises
                    // only lists SIREN-bearing entities, so absence there never disproves legal existence.
                }
            } catch (ex: Exception) {
                log.warn("Recherche d'entreprises lookup failed for key={}: {}", searchKey, ex.message)
                warnings.add("recherche-entreprises: ${ex.message ?: "unavailable"}")
                rechercheEntreprisesFailed = true
            }
        }

        // SIREN resolved from the registry when the profile only carries an RNA — unlocks INSEE and BODACC.
        val siren = sirenFromProfile ?: sirenFromSearch

        // ── Step 2: INSEE Sirene (SIREN declared on the profile or resolved at step 1) ─────────
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
                // Newest-first: a dissolution notice is the last event of an association's life, so the
                // most recent window is the one that can hold it. An unordered window could miss it.
                val uri = UriComponentsBuilder.fromUriString("$joafeBaseUrl/catalog/datasets/jo_associations/records")
                    .queryParam("where", "numero_rna='$rna'")
                    .queryParam("order_by", "dateparution desc")
                    .queryParam("limit", 20)
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
                        // Prepopulate receipt fields from JOAFE when they are not yet set.
                        matching.firstOrNull()?.let { fields ->
                            val joafeAddress = fields.path("adresse_libelle").asText("").takeIf { it.isNotBlank() }
                                ?: fields.path("adresse_siege_libelle").asText("").takeIf { it.isNotBlank() }
                                ?: fields.path("adresse_gestion_libelle").asText("").takeIf { it.isNotBlank() }
                            val joafeObjet = fields.path("objet").asText("").takeIf { it.isNotBlank() }
                            if (profile.addressLine1.isNullOrBlank() && joafeAddress != null) {
                                profile.addressLine1 = joafeAddress
                                log.info("JOAFE prepopulated addressLine1 for association {}", profile.id)
                            }
                            if (profile.legalObject.isNullOrBlank() && joafeObjet != null) {
                                profile.legalObject = joafeObjet
                                log.info("JOAFE prepopulated legalObject for association {}", profile.id)
                            }
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

        // An association that never obtained a SIREN is absent from Recherche d'entreprises, leaving
        // JOAFE as the only RNA source: a publication with no dissolution notice means it is still live.
        // Two deliberate limits: JOAFE silence is not evidence of inactivity (rnaActive stays null), and
        // an outage of the primary source stays visible as "undetermined" rather than being papered over
        // by a weaker inference.
        if (!rechercheEntreprisesFailed && rnaActive == null && joafeDeclarationFound == true) {
            rnaActive = dissolutionDetected == false
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
            legalCategory = legalCategory,
            etatAdministratif = etatAdministratif,
            joafeDeclarationFound = joafeDeclarationFound,
            dissolutionDetected = dissolutionDetected,
            bodaccProcedureFound = bodaccProcedureFound,
            warnings = warnings.toList(),
            officers = officers.distinct(),
            rnaActive = rnaActive,
            checkedBy = checkedBy,
        )
    }

    private fun AssociationRegistryCheck.toDto() = RegistryPreCheckDto(
        id = id!!,
        associationExists = associationExists,
        siren = siren,
        rna = rna,
        legalCategory = legalCategory,
        scopeVerdict = scopeVerdict,
        etatAdministratif = etatAdministratif,
        joafeDeclarationFound = joafeDeclarationFound,
        dissolutionDetected = dissolutionDetected,
        bodaccProcedureFound = bodaccProcedureFound,
        checkedAt = checkedAt,
        warnings = warnings,
        officers = officers,
        rnaActive = rnaActive,
    )

    private fun enc(value: String): String =
        URLEncoder.encode(value, Charsets.UTF_8).replace("+", "%20")
}
