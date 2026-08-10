package org.commonlink.service

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.commonlink.config.SanctionsProperties
import org.commonlink.entity.SanctionedEntity
import org.commonlink.entity.SanctionedNature
import org.commonlink.repository.SanctionedEntityRepository
import org.commonlink.util.NameNormalizer
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import java.time.Instant
import java.time.LocalDate

/**
 * Tests for [SanctionScreeningService] and [SanctionIngestionService].
 *
 * All persons and entities used in this test class are entirely fictional.
 * No correspondence with real persons or entities is intended.
 *
 * Test coverage:
 *  - Screening: exact match, normalization variants (accents, case, spacing, reversed order,
 *    punctuation, missing particle, alias), near-miss non-matches, nature filter.
 *  - Ingestion: correct count from fixture, idempotency on re-ingestion, lifted measure deletion.
 */
class SanctionScreeningServiceTest {

    private val repository = mockk<SanctionedEntityRepository>()
    private val props = SanctionsProperties(scoreThreshold = 0.85, useTestData = false)
    private val screeningService = SanctionScreeningService(repository, props)

    // ── Fictional entities ───────────────────────────────────────────────────────────────────

    private val fictivusAlexius = SanctionedEntity(
        idRegistre = 1,
        nature = SanctionedNature.PHYSICAL_PERSON,
        nom = "FICTIVUS Alexius",
        normalizedNames = listOf(
            NameNormalizer.normalize("FICTIVUS Alexius"),    // "FICTIVUS ALEXIUS"
            NameNormalizer.normalize("Alexius FICTIVUS"),    // "ALEXIUS FICTIVUS"
            NameNormalizer.normalize("FELIX Alexis"),        // alias forward
            NameNormalizer.normalize("Alexis FELIX"),        // alias reversed
        ),
        dateOfBirth = "01/01/1970",
        publicationDate = LocalDate.of(2024, 1, 15),
        ingestedAt = Instant.now(),
    )

    private val zorbalIndustries = SanctionedEntity(
        idRegistre = 2,
        nature = SanctionedNature.LEGAL_ENTITY,
        nom = "ZORBAL INDUSTRIES SA",
        normalizedNames = listOf(NameNormalizer.normalize("ZORBAL INDUSTRIES SA")),
        publicationDate = LocalDate.of(2024, 1, 15),
        ingestedAt = Instant.now(),
    )

    private val deExemplaJanus = SanctionedEntity(
        idRegistre = 3,
        nature = SanctionedNature.PHYSICAL_PERSON,
        nom = "DE EXEMPLA Janus",
        normalizedNames = listOf(
            NameNormalizer.normalize("DE EXEMPLA Janus"),  // "DE EXEMPLA JANUS"
            NameNormalizer.normalize("Janus DE EXEMPLA"),  // "JANUS DE EXEMPLA"
        ),
        dateOfBirth = "06/1985",
        publicationDate = LocalDate.of(2024, 1, 15),
        ingestedAt = Instant.now(),
    )

    @BeforeEach
    fun setup() {
        every { repository.findAll() } returns listOf(fictivusAlexius, zorbalIndustries, deExemplaJanus)
        every { repository.findByNature(SanctionedNature.PHYSICAL_PERSON.name) } returns listOf(fictivusAlexius, deExemplaJanus)
        every { repository.findByNature(SanctionedNature.LEGAL_ENTITY.name) } returns listOf(zorbalIndustries)
    }

    // ── Screening: exact and basic match ─────────────────────────────────────────────────────

    @Test
    fun `exact name matches listed physical person`() {
        val matches = screeningService.screen("FICTIVUS Alexius")
        assertEquals(1, matches.size)
        assertEquals(1, matches[0].idRegistre)
        assertEquals(1.0, matches[0].score, 0.0001)
    }

    @Test
    fun `exact name matches listed legal entity`() {
        val matches = screeningService.screen("ZORBAL INDUSTRIES SA")
        assertTrue(matches.any { it.idRegistre == 2 })
    }

    // ── Screening: normalization variants — all must return a match ───────────────────────────

    @ParameterizedTest(name = "variant: {0}")
    @ValueSource(strings = [
        "fictivus alexius",                  // lowercase
        "FICTIVUS  ALEXIUS",                 // multiple spaces
        "FICTÏVUS ÀLÈXÏUS",                  // accents on both parts
        "FICTIVUS-ALEXIUS",                  // hyphen separator
        "Fictivus Alexius",                  // mixed case
        "  FICTIVUS   ALEXIUS  ",            // leading/trailing spaces
    ])
    fun `normalization variant of FICTIVUS Alexius matches`(variant: String) {
        val matches = screeningService.screen(variant)
        assertTrue(matches.any { it.idRegistre == 1 },
            "Expected match for variant '$variant' but got none")
    }

    @Test
    fun `inverted order PRENOM NOM matches via stored reversed variant`() {
        val matches = screeningService.screen("Alexius FICTIVUS")
        assertTrue(matches.any { it.idRegistre == 1 })
    }

    @Test
    fun `alias FELIX Alexis matches via stored alias variant`() {
        val matches = screeningService.screen("FELIX Alexis")
        assertTrue(matches.any { it.idRegistre == 1 })
    }

    @Test
    fun `alias in reversed order also matches`() {
        val matches = screeningService.screen("Alexis FELIX")
        assertTrue(matches.any { it.idRegistre == 1 })
    }

    @Test
    fun `particle absent in query still matches entry with particle in name`() {
        // "EXEMPLA JANUS" vs stored "DE EXEMPLA JANUS" — JaroWinkler tolerates the short prefix difference
        val matches = screeningService.screen("EXEMPLA Janus")
        assertTrue(matches.any { it.idRegistre == 3 },
            "Expected match for 'EXEMPLA Janus' against 'DE EXEMPLA Janus' but got none")
    }

    // ── Screening: non-matches — clearly distinct names must not trigger ──────────────────────

    @Test
    fun `common unrelated name does not match`() {
        val matches = screeningService.screen("DUPONT Jean")
        assertTrue(matches.none { it.idRegistre == 1 || it.idRegistre == 3 })
    }

    @Test
    fun `another unrelated name does not match`() {
        val matches = screeningService.screen("MARTIN Sophie")
        assertTrue(matches.isEmpty())
    }

    @Test
    fun `legal entity name does not match physical person entries`() {
        val matches = screeningService.screen("ZORBAL INDUSTRIES SA",
            nature = SanctionedNature.PHYSICAL_PERSON)
        assertTrue(matches.none { it.idRegistre == 2 })
    }

    // ── Screening: nature filter ───────────────────────────────────────────────────────────────

    @Test
    fun `nature filter restricts to physical persons only`() {
        val matches = screeningService.screen("FICTIVUS Alexius", nature = SanctionedNature.PHYSICAL_PERSON)
        assertTrue(matches.all { it.nature == SanctionedNature.PHYSICAL_PERSON })
        verify { repository.findByNature(SanctionedNature.PHYSICAL_PERSON.name) }
    }

    @Test
    fun `null nature searches full register`() {
        screeningService.screen("FICTIVUS Alexius", nature = null)
        verify { repository.findAll() }
    }

    // ── Screening: results are ordered by descending score ────────────────────────────────────

    @Test
    fun `results are ordered by descending score`() {
        val matches = screeningService.screen("FICTIVUS Alexius")
        val scores = matches.map { it.score }
        assertEquals(scores.sortedDescending(), scores)
    }

    // ── Normalization: single-point invariant ─────────────────────────────────────────────────

    @Test
    fun `NameNormalizer strips accents`() {
        assertEquals("FICTIVUS ALEXIUS", NameNormalizer.normalize("FICTÏVUS ÀLÈXÏUS"))
    }

    @Test
    fun `NameNormalizer uppercases`() {
        assertEquals("FICTIVUS ALEXIUS", NameNormalizer.normalize("fictivus alexius"))
    }

    @Test
    fun `NameNormalizer collapses multiple spaces`() {
        assertEquals("FICTIVUS ALEXIUS", NameNormalizer.normalize("FICTIVUS  ALEXIUS"))
    }

    @Test
    fun `NameNormalizer converts hyphens to spaces`() {
        assertEquals("FICTIVUS ALEXIUS", NameNormalizer.normalize("FICTIVUS-ALEXIUS"))
    }

    // ── Ingestion: fixture parsing and idempotency ────────────────────────────────────────────

    @Test
    fun `ingestion from fixture populates expected entry count`() {
        val ingestionProps = SanctionsProperties(useTestData = true)
        val ingestionRepo = mockk<SanctionedEntityRepository>()
        val ingestionService = SanctionIngestionService(ingestionRepo, ingestionProps)

        val savedEntities = mutableListOf<SanctionedEntity>()
        every { ingestionRepo.findByIdRegistre(any()) } returns null
        every { ingestionRepo.save(any<SanctionedEntity>()) } answers {
            val entity = firstArg<SanctionedEntity>()
            savedEntities.add(entity)
            entity
        }
        every { ingestionRepo.findByIdRegistreNotIn(any()) } returns emptyList()

        ingestionService.ingest()

        // Fixture contains 3 PublicationDetail entries
        assertEquals(3, savedEntities.size, "Expected 3 entries from the test fixture")
    }

    @Test
    fun `re-ingesting same fixture does not create duplicates`() {
        val ingestionProps = SanctionsProperties(useTestData = true)
        val ingestionRepo = mockk<SanctionedEntityRepository>()
        val ingestionService = SanctionIngestionService(ingestionRepo, ingestionProps)

        // Simulate all entries already exist
        val existingEntry = SanctionedEntity(
            idRegistre = 1,
            nature = SanctionedNature.PHYSICAL_PERSON,
            nom = "FICTIVUS Alexius",
            normalizedNames = listOf("FICTIVUS ALEXIUS"),
            publicationDate = LocalDate.of(2024, 1, 1),
            ingestedAt = Instant.now(),
        )
        every { ingestionRepo.findByIdRegistre(1) } returns existingEntry
        every { ingestionRepo.findByIdRegistre(2) } returns SanctionedEntity(
            idRegistre = 2,
            nature = SanctionedNature.LEGAL_ENTITY,
            nom = "ZORBAL INDUSTRIES SA",
            normalizedNames = listOf("ZORBAL INDUSTRIES SA"),
            publicationDate = LocalDate.of(2024, 1, 1),
            ingestedAt = Instant.now(),
        )
        every { ingestionRepo.findByIdRegistre(3) } returns SanctionedEntity(
            idRegistre = 3,
            nature = SanctionedNature.PHYSICAL_PERSON,
            nom = "DE EXEMPLA Janus",
            normalizedNames = listOf("DE EXEMPLA JANUS"),
            publicationDate = LocalDate.of(2024, 1, 1),
            ingestedAt = Instant.now(),
        )
        every { ingestionRepo.save(any<SanctionedEntity>()) } answers { firstArg() }
        every { ingestionRepo.findByIdRegistreNotIn(any()) } returns emptyList()

        ingestionService.ingest()

        // save() called 3 times (update path) — findByIdRegistre returned existing entries for all
        verify(exactly = 3) { ingestionRepo.save(any<SanctionedEntity>()) }
    }

    @Test
    fun `ingestion deletes entries absent from new publication`() {
        val ingestionProps = SanctionsProperties(useTestData = true)
        val ingestionRepo = mockk<SanctionedEntityRepository>()
        val ingestionService = SanctionIngestionService(ingestionRepo, ingestionProps)

        every { ingestionRepo.findByIdRegistre(any()) } returns null
        every { ingestionRepo.save(any<SanctionedEntity>()) } answers { firstArg() }

        // Simulate a stale entry (idRegistre=99) that is no longer in the published register
        val staleEntry = SanctionedEntity(
            idRegistre = 99,
            nature = SanctionedNature.PHYSICAL_PERSON,
            nom = "STALE Fictif",
            normalizedNames = listOf("STALE FICTIF"),
            publicationDate = LocalDate.of(2023, 1, 1),
            ingestedAt = Instant.now(),
        )
        every { ingestionRepo.findByIdRegistreNotIn(any()) } returns listOf(staleEntry)
        every { ingestionRepo.deleteAll(listOf(staleEntry)) } returns Unit

        ingestionService.ingest()

        verify { ingestionRepo.deleteAll(listOf(staleEntry)) }
    }

    @Test
    fun `buildNormalizedNames produces both orderings for physical person`() {
        val ingestionService = SanctionIngestionService(repository, props)
        val names = ingestionService.buildNormalizedNames("FICTIVUS", "Alexius", emptyList())
        assertTrue(names.contains("FICTIVUS ALEXIUS"), "Expected NOM PRENOM ordering")
        assertTrue(names.contains("ALEXIUS FICTIVUS"), "Expected PRENOM NOM ordering")
    }

    @Test
    fun `buildNormalizedNames includes alias and its reversed form`() {
        val ingestionService = SanctionIngestionService(repository, props)
        val names = ingestionService.buildNormalizedNames("FICTIVUS", "Alexius", listOf("FELIX Alexis"))
        assertTrue(names.contains("FELIX ALEXIS"))
        assertTrue(names.contains("ALEXIS FELIX"))
    }
}
