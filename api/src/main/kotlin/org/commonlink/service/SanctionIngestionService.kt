package org.commonlink.service

import org.commonlink.config.SanctionsProperties
import org.commonlink.entity.SanctionedEntity
import org.commonlink.entity.SanctionedNature
import org.commonlink.repository.SanctionedEntityRepository
import org.commonlink.util.NameNormalizer
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.w3c.dom.Element
import java.io.InputStream
import java.net.URI
import java.time.Instant
import java.time.LocalDate
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Ingests the national asset-freeze register published by the Direction générale du Trésor.
 *
 * The register is fetched as XML from the DG Trésor public API and parsed into [SanctionedEntity]
 * rows. Each ingestion is a full re-synchronisation:
 *  - Existing entries are **upserted** (updated in place if the content changed).
 *  - Entries absent from the new publication are **deleted** (the measure has been lifted).
 *
 * **Log hygiene** — this service never logs individual subject names or the content of
 * matched entries. Only aggregate counts and the publication date are emitted. Per-screening
 * traces belong in the compliance audit log (prompt 12), not in application logs.
 */
@Service
class SanctionIngestionService(
    private val repository: SanctionedEntityRepository,
    private val props: SanctionsProperties,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Transactional
    fun ingest() {
        val (publicationDate, entries) = loadAndParse()

        if (entries.isEmpty()) {
            log.warn("LCB-FT sanctions: ingestion produced 0 entries — aborting to prevent full register purge")
            return
        }

        val incomingIds = entries.map { it.idRegistre }.toSet()
        var created = 0
        var updated = 0

        for (entry in entries) {
            val existing = repository.findByIdRegistre(entry.idRegistre)
            if (existing != null) {
                existing.nature = entry.nature
                existing.nom = entry.nom
                existing.normalizedNames = entry.normalizedNames
                existing.dateOfBirth = entry.dateOfBirth
                existing.legalReference = entry.legalReference
                existing.publicationDate = entry.publicationDate
                existing.ingestedAt = entry.ingestedAt
                repository.save(existing)
                updated++
            } else {
                repository.save(entry)
                created++
            }
        }

        val lifted = repository.findByIdRegistreNotIn(incomingIds)
        if (lifted.isNotEmpty()) {
            repository.deleteAll(lifted)
        }

        log.info(
            "LCB-FT sanctions: ingestion complete — publication {}, {} active measures ({} created, {} updated, {} lifted)",
            publicationDate,
            entries.size,
            created,
            updated,
            lifted.size,
        )
    }

    private fun loadAndParse(): ParsedRegister {
        val stream: InputStream = if (props.useTestData) {
            javaClass.getResourceAsStream("/fixtures/sanctions-test.xml")
                ?: error("Test fixture not found at classpath:/fixtures/sanctions-test.xml")
        } else {
            URI.create(props.registryUrl).toURL().openStream()
        }
        return stream.use { parseXml(it) }
    }

    private fun parseXml(input: InputStream): ParsedRegister {
        val factory = DocumentBuilderFactory.newInstance()
        factory.isNamespaceAware = false
        val doc = factory.newDocumentBuilder().parse(input)

        val dateStr = doc.getElementsByTagName("DatePublication").item(0)?.textContent?.trim()
        val publicationDate = dateStr?.let {
            try { LocalDate.parse(it.substring(0, 10)) } catch (_: Exception) { LocalDate.now() }
        } ?: LocalDate.now()

        val detailNodes = doc.getElementsByTagName("PublicationDetail")
        val entries = (0 until detailNodes.length).mapNotNull { i ->
            try {
                parseDetail(detailNodes.item(i) as Element, publicationDate)
            } catch (e: Exception) {
                // Never echo the raw record — log only the position and error type
                log.warn("LCB-FT sanctions: skipped malformed entry at index {} ({})", i, e.javaClass.simpleName)
                null
            }
        }

        return ParsedRegister(publicationDate, entries)
    }

    private fun parseDetail(detail: Element, publicationDate: LocalDate): SanctionedEntity {
        val idRegistre = detail.getElementsByTagName("IdRegistre").item(0)!!.textContent.trim().toInt()
        val natureStr = detail.getElementsByTagName("Nature").item(0)!!.textContent.trim()
        val nature = SanctionedNature.fromDgtresor(natureStr)
        val nom = detail.getElementsByTagName("Nom").item(0)!!.textContent.trim()

        var prenom: String? = null
        var dateOfBirth: String? = null
        var legalReference: String? = null
        val aliases = mutableListOf<String>()

        val rdNodes = detail.getElementsByTagName("RegistreDetail")
        for (j in 0 until rdNodes.length) {
            val rd = rdNodes.item(j) as Element
            val typeChamp = rd.getElementsByTagName("TypeChamp").item(0)?.textContent?.trim() ?: continue
            val valeurs = rd.getElementsByTagName("Valeur")

            when (typeChamp) {
                "PRENOM" -> {
                    for (k in 0 until valeurs.length) {
                        (valeurs.item(k) as Element)
                            .getElementsByTagName("Prenom").item(0)
                            ?.textContent?.trim()?.takeIf { it.isNotBlank() }
                            ?.let { prenom = it }
                    }
                }
                "ALIAS" -> {
                    for (k in 0 until valeurs.length) {
                        (valeurs.item(k) as Element)
                            .getElementsByTagName("Alias").item(0)
                            ?.textContent?.trim()?.takeIf { it.isNotBlank() }
                            ?.let { aliases.add(it) }
                    }
                }
                "DATE_DE_NAISSANCE" -> {
                    for (k in 0 until valeurs.length) {
                        val v = valeurs.item(k) as Element
                        val annee = v.getElementsByTagName("Annee").item(0)?.textContent?.trim()
                        if (!annee.isNullOrBlank()) {
                            val mois = v.getElementsByTagName("Mois").item(0)?.textContent?.trim()
                            val jour = v.getElementsByTagName("Jour").item(0)?.textContent?.trim()
                            dateOfBirth = buildDateString(jour, mois, annee)
                        }
                    }
                }
                "FONDEMENT_JURIDIQUE" -> {
                    for (k in 0 until valeurs.length) {
                        (valeurs.item(k) as Element)
                            .getElementsByTagName("FondementJuridiqueLabel").item(0)
                            ?.textContent?.trim()?.takeIf { it.isNotBlank() }
                            ?.let { legalReference = it }
                    }
                }
                "REFERENCE_UE" -> {
                    for (k in 0 until valeurs.length) {
                        (valeurs.item(k) as Element)
                            .getElementsByTagName("ReferenceUe").item(0)
                            ?.textContent?.trim()?.takeIf { it.isNotBlank() }
                            ?.let { legalReference = it }
                    }
                }
                "REFERENCE_ONU" -> {
                    for (k in 0 until valeurs.length) {
                        (valeurs.item(k) as Element)
                            .getElementsByTagName("ReferenceOnu").item(0)
                            ?.textContent?.trim()?.takeIf { it.isNotBlank() }
                            ?.let { legalReference = it }
                    }
                }
            }
        }

        val fullName = if (prenom != null) "$nom $prenom" else nom
        return SanctionedEntity(
            idRegistre = idRegistre,
            nature = nature,
            nom = fullName,
            normalizedNames = buildNormalizedNames(nom, prenom, aliases),
            dateOfBirth = dateOfBirth,
            legalReference = legalReference,
            publicationDate = publicationDate,
            ingestedAt = Instant.now(),
        )
    }

    private fun buildDateString(jour: String?, mois: String?, annee: String): String {
        val parts = buildList {
            jour?.padStart(2, '0')?.takeIf { it.isNotBlank() }?.let { add(it) }
            mois?.padStart(2, '0')?.takeIf { it.isNotBlank() }?.let { add(it) }
            add(annee)
        }
        return parts.joinToString("/")
    }

    /**
     * Builds all normalized name variants for a register entry.
     *
     * For a physical person (NOM + prenom): stores NOM PRENOM and PRENOM NOM.
     * For each alias (full alias string): stores the alias as-is and with the first
     * and second word swapped (covers both NOM PRENOM and PRENOM NOM alias orderings).
     *
     * Uses [NameNormalizer] exclusively — the only normalization path in this service.
     */
    internal fun buildNormalizedNames(nom: String, prenom: String?, aliases: List<String>): List<String> {
        val result = linkedSetOf<String>()

        val mainForward = NameNormalizer.normalize(if (prenom != null) "$nom $prenom" else nom)
        if (mainForward.isNotBlank()) result.add(mainForward)

        if (prenom != null) {
            val mainReversed = NameNormalizer.normalize("$prenom $nom")
            if (mainReversed.isNotBlank()) result.add(mainReversed)
        }

        for (alias in aliases) {
            val normalized = NameNormalizer.normalize(alias)
            if (normalized.isNotBlank()) result.add(normalized)
            val parts = alias.trim().split(Regex("\\s+"), limit = 2)
            if (parts.size == 2) {
                val reversed = NameNormalizer.normalize("${parts[1]} ${parts[0]}")
                if (reversed.isNotBlank()) result.add(reversed)
            }
        }

        return result.toList()
    }

    private data class ParsedRegister(val publicationDate: LocalDate, val entries: List<SanctionedEntity>)
}
