package org.commonlink.bootstrap

import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.commonlink.entity.LegalContentFormat
import org.commonlink.entity.LegalDocument
import org.commonlink.entity.LegalDocumentType
import org.commonlink.exception.LegalTextSourceException
import org.commonlink.repository.LegalDocumentRepository
import org.commonlink.service.LandingLegalTextSource
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.boot.DefaultApplicationArguments
import org.springframework.dao.DataIntegrityViolationException

class LegalDocumentSeederTest {

    private val source: LandingLegalTextSource = mockk()
    private val repository: LegalDocumentRepository = mockk()
    private val seeder = LegalDocumentSeeder(source, repository)

    private val snapshot = LandingLegalTextSource.Snapshot(
        documentType = LegalDocumentType.CGU,
        version = "2026-09-09-0123456789ab",
        content = """{"title":"T"}""",
        sha256 = "0123456789ab" + "f".repeat(52),
    )

    private fun stored(sha256: String) = LegalDocument(
        documentType = snapshot.documentType,
        version = snapshot.version,
        content = snapshot.content,
        contentFormat = LegalContentFormat.JSON_V1,
        contentSha256 = sha256,
    )

    @Test
    fun `inserts an unknown version as structured content`() {
        every { repository.findByDocumentTypeAndVersion(snapshot.documentType, snapshot.version) } returns null
        val saved = slot<LegalDocument>()
        every { repository.saveAndFlush(capture(saved)) } answers { saved.captured }

        seeder.seed(snapshot)

        assertEquals(snapshot.version, saved.captured.version)
        assertEquals(snapshot.content, saved.captured.content)
        assertEquals(LegalContentFormat.JSON_V1, saved.captured.contentFormat)
        assertEquals(snapshot.sha256, saved.captured.contentSha256)
    }

    @Test
    fun `does nothing when the same text is already stored`() {
        every { repository.findByDocumentTypeAndVersion(snapshot.documentType, snapshot.version) } returns stored(snapshot.sha256)
        every { repository.findTopByDocumentTypeOrderByPublishedAtDesc(snapshot.documentType) } returns stored(snapshot.sha256)

        seeder.seed(snapshot)

        verify(exactly = 0) { repository.saveAndFlush(any<LegalDocument>()) }
    }

    @Test
    fun `a text reverted to an older published version is not re-published`() {
        // A, then B, then A again: A's version exists, B stays in force (a WARN is logged).
        val newer = LegalDocument(
            documentType = snapshot.documentType, version = "2026-09-09-bbbbbbbbbbbb", content = "{}",
            contentFormat = LegalContentFormat.JSON_V1, contentSha256 = "b".repeat(64),
        )
        every { repository.findByDocumentTypeAndVersion(snapshot.documentType, snapshot.version) } returns stored(snapshot.sha256)
        every { repository.findTopByDocumentTypeOrderByPublishedAtDesc(snapshot.documentType) } returns newer

        seeder.seed(snapshot)

        verify(exactly = 0) { repository.saveAndFlush(any<LegalDocument>()) }
    }

    @Test
    fun `refuses to start when the version is stored with another text`() {
        every { repository.findByDocumentTypeAndVersion(snapshot.documentType, snapshot.version) } returns
            stored("0123456789ab" + "0".repeat(52))

        assertThrows<LegalTextSourceException> { seeder.seed(snapshot) }
        verify(exactly = 0) { repository.saveAndFlush(any<LegalDocument>()) }
    }

    @Test
    fun `losing a concurrent insert to the same text is fine`() {
        every { repository.findByDocumentTypeAndVersion(snapshot.documentType, snapshot.version) } returnsMany
            listOf(null, stored(snapshot.sha256))
        every { repository.saveAndFlush(any<LegalDocument>()) } throws DataIntegrityViolationException("duplicate key")

        seeder.seed(snapshot)
    }

    @Test
    fun `losing a concurrent insert to another text fails`() {
        every { repository.findByDocumentTypeAndVersion(snapshot.documentType, snapshot.version) } returnsMany
            listOf(null, stored("0".repeat(64)))
        every { repository.saveAndFlush(any<LegalDocument>()) } throws DataIntegrityViolationException("duplicate key")

        assertThrows<LegalTextSourceException> { seeder.seed(snapshot) }
    }

    @Test
    fun `run seeds every document read from the source`() {
        val cgv = snapshot.copy(documentType = LegalDocumentType.CGV, version = "2026-10-05-0123456789ab")
        every { source.load() } returns listOf(snapshot, cgv)
        every { repository.findByDocumentTypeAndVersion(any(), any()) } returns null
        every { repository.saveAndFlush(any<LegalDocument>()) } answers { firstArg() }

        seeder.run(DefaultApplicationArguments())

        verify(exactly = 2) { repository.saveAndFlush(any<LegalDocument>()) }
    }
}
