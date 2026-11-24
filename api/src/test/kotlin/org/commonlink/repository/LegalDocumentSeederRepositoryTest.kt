package org.commonlink.repository

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import org.commonlink.bootstrap.LegalDocumentSeeder
import org.commonlink.dto.LegalTextV1
import org.commonlink.entity.LegalContentFormat
import org.commonlink.entity.LegalDocument
import org.commonlink.entity.LegalDocumentType
import org.commonlink.service.LandingLegalTextSource
import org.commonlink.service.LegalAcceptanceService
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.DefaultApplicationArguments
import org.springframework.context.annotation.Import
import java.time.Instant

/**
 * [LegalDocumentSeeder] on a real repository with the real landing texts bundled by processResources — the
 * unit test mocks the repository, and every other test context disables the seeder.
 */
@Import(LandingLegalTextSource::class)
class LegalDocumentSeederRepositoryTest : AbstractRepositoryTest() {

    @Autowired private lateinit var source: LandingLegalTextSource
    @Autowired private lateinit var legalDocumentRepository: LegalDocumentRepository
    @Autowired private lateinit var legalAcceptanceRepository: LegalAcceptanceRepository
    @Autowired private lateinit var associationProfileRepository: AssociationProfileRepository

    @Test
    fun `seeds both landing texts as current structured versions, once`() {
        // The V73 placeholder, as it stands in production.
        legalDocumentRepository.saveAndFlush(
            LegalDocument(
                documentType = LegalDocumentType.CGU, version = "2026-08-26", content = "PLACEHOLDER",
                publishedAt = Instant.parse("2026-08-27T14:20:17Z"),
            )
        )
        val seeder = LegalDocumentSeeder(source, legalDocumentRepository)
        val service = LegalAcceptanceService(legalDocumentRepository, legalAcceptanceRepository, associationProfileRepository)

        seeder.run(DefaultApplicationArguments())
        seeder.run(DefaultApplicationArguments())

        val rows = legalDocumentRepository.findAll().filter { it.contentFormat == LegalContentFormat.JSON_V1 }
        assertEquals(LegalDocumentType.entries.toSet(), rows.map { it.documentType }.toSet())
        assertEquals(3, rows.size, "a second run inserts nothing")

        // The current versions are those of the bundled texts ("<effective date>-<12 hex>"), whatever their date: the
        // texts are re-dated at every legal update, so no date is hard-coded here.
        val bundled = source.load().associate { it.documentType to it.version }
        val versionFormat = Regex("\\d{4}-\\d{2}-\\d{2}-[0-9a-f]{12}")
        val cgu = service.currentDocumentDto(LegalDocumentType.CGU)
        assertEquals(LegalContentFormat.JSON_V1, cgu.format)
        assertTrue(versionFormat.matches(cgu.version), cgu.version)
        assertEquals(bundled[LegalDocumentType.CGU], cgu.version)
        assertTrue(jacksonObjectMapper().readValue<LegalTextV1>(cgu.content).title.contains("donateurs"))
        val cgv = service.currentDocumentDto(LegalDocumentType.CGV)
        assertTrue(versionFormat.matches(cgv.version), cgv.version)
        assertEquals(bundled[LegalDocumentType.CGV], cgv.version)
        val privacy = service.currentDocumentDto(LegalDocumentType.PRIVACY)
        assertTrue(versionFormat.matches(privacy.version), privacy.version)
        assertEquals(bundled[LegalDocumentType.PRIVACY], privacy.version)
        val privacyText = jacksonObjectMapper().readValue<LegalTextV1>(privacy.content)
        assertTrue(privacyText.title.contains("confidentialité"))
        assertTrue(privacyText.articles.any { it.table != null }, "the retention periods keep their table")
    }
}
