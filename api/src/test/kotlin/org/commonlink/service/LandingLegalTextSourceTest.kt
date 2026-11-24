package org.commonlink.service

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import org.commonlink.dto.LegalArticleV1
import org.commonlink.dto.LegalSegmentV1
import org.commonlink.dto.LegalSubsectionV1
import org.commonlink.dto.LegalTableV1
import org.commonlink.dto.LegalTextV1
import org.commonlink.entity.LegalDocumentType
import org.commonlink.exception.LegalTextSourceException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.core.io.ByteArrayResource
import org.springframework.core.io.ClassPathResource

class LandingLegalTextSourceTest {

    private val baseUrl = "https://www.common-link.org"
    private val mapper = jacksonObjectMapper()

    /** The real landing texts, bundled by processResources from landing-page/messages/fr.json. */
    private val realSource = LandingLegalTextSource(ClassPathResource("legal/landing-fr.json"), "$baseUrl/")

    private fun sourceOf(json: String) = LandingLegalTextSource(ByteArrayResource(json.toByteArray()), baseUrl)

    private fun minimal(
        lastUpdated: String = "Version en vigueur au 1er octobre 2026",
        paragraph: String = "Voir la <privacy>politique</privacy> du site.",
        title: String = "Article 1",
        privacyArticle: String = DEFAULT_PRIVACY_ARTICLE,
    ) = """
        {"legal": {
          "linkTargets": {"privacy": "/politique-confidentialite"},
          "cguDonors": {"title": "CGU donateurs", "lastUpdated": "$lastUpdated", "intro": "Intro",
            "articles": [{"title": "$title", "paragraphs": ["$paragraph"]}]},
          "cguAssociations": {"title": "CGU associations", "lastUpdated": "Version en vigueur au 5 octobre 2026",
            "intro": "Intro", "articles": [{"title": "Annexe", "subsections": [{"title": "1. Objet", "paragraphs": ["Texte"]}]}]},
          "politiqueConfidentialite": {"title": "Confidentialité", "lastUpdated": "Version en vigueur au 6 octobre 2026",
            "articles": [$privacyArticle]}
        }}
    """.trimIndent()

    private fun privacyOf(json: String) =
        mapper.readValue<LegalTextV1>(sourceOf(json).load().single { it.documentType == LegalDocumentType.PRIVACY }.content)

    @Test
    fun `real landing texts convert to CGU for donors and CGV for associations`() {
        val snapshots = realSource.load().associateBy { it.documentType }

        val cgu = mapper.readValue<LegalTextV1>(snapshots.getValue(LegalDocumentType.CGU).content)
        val cgv = mapper.readValue<LegalTextV1>(snapshots.getValue(LegalDocumentType.CGV).content)
        assertTrue(cgu.title.contains("donateurs"))
        assertTrue(cgv.title.contains("organismes"))
        assertTrue(cgu.articles.isNotEmpty() && cgv.articles.isNotEmpty())
        assertTrue(cgv.articles.any { it.subsections.isNotEmpty() }, "the association annexes keep their subsections")
    }

    @Test
    fun `real landing privacy policy converts with no intro, its retention table and its bold runs`() {
        val snapshot = realSource.load().single { it.documentType == LegalDocumentType.PRIVACY }
        val privacy = mapper.readValue<LegalTextV1>(snapshot.content)

        assertTrue(privacy.title.contains("confidentialité"))
        assertNull(privacy.intro)
        assertFalse(snapshot.content.contains("\"intro\""), "an absent intro is left out of the hashed form")
        val table = privacy.articles.mapNotNull { it.table }.single()
        assertEquals(2, table.head.size)
        assertTrue(table.rows.isNotEmpty() && table.rows.all { it.size == 2 })
        assertTrue(privacy.articles.flatMap { it.paragraphs }.flatten().any { it.bold == true })
        assertTrue(privacy.articles.any { it.subsections.isNotEmpty() })
    }

    @Test
    fun `real landing texts resolve every link tag to an absolute URL and keep no markup`() {
        for (snapshot in realSource.load()) {
            assertFalse(snapshot.content.contains('<'), "${snapshot.documentType} still contains markup")
            val text = mapper.readValue<LegalTextV1>(snapshot.content)
            val links = text.articles.flatMap { a -> a.paragraphs.flatten() + a.subsections.flatMap { it.paragraphs.flatten() } }
                .mapNotNull { it.href }
            assertTrue(links.isNotEmpty(), "${snapshot.documentType} has links")
            assertTrue(links.all { it.startsWith("$baseUrl/") }, "absolute links without double slash: $links")
        }
    }

    @Test
    fun `version is the effective date followed by 12 hex of the content hash`() {
        val snapshot = sourceOf(minimal()).load().first { it.documentType == LegalDocumentType.CGU }

        assertTrue(Regex("2026-10-01-[0-9a-f]{12}").matches(snapshot.version), snapshot.version)
        assertEquals(snapshot.sha256.take(12), snapshot.version.takeLast(12))
        assertEquals(64, snapshot.sha256.length)
        assertTrue(snapshot.version.length <= 32)
    }

    @Test
    fun `conversion is deterministic`() {
        assertEquals(realSource.load(), realSource.load())
    }

    @Test
    fun `two different texts with the same effective date get two versions`() {
        val first = sourceOf(minimal(title = "Article 1")).load().first { it.documentType == LegalDocumentType.CGU }
        val second = sourceOf(minimal(title = "Article 1 corrige")).load().first { it.documentType == LegalDocumentType.CGU }

        assertEquals(first.version.take(10), second.version.take(10))
        assertNotEquals(first.version, second.version)
    }

    @Test
    fun `a paragraph with a link becomes text, link and text segments`() {
        val snapshot = sourceOf(minimal()).load().first { it.documentType == LegalDocumentType.CGU }
        val paragraph = mapper.readValue<LegalTextV1>(snapshot.content).articles.single().paragraphs.single()

        assertEquals(
            listOf(
                LegalSegmentV1("Voir la "),
                LegalSegmentV1("politique", "$baseUrl/politique-confidentialite"),
                LegalSegmentV1(" du site."),
            ),
            paragraph,
        )
    }

    @Test
    fun `snapshot hashes exactly the stored content`() {
        val text = LegalTextV1("T", "Version en vigueur au 2 janvier 2027", "I", listOf(LegalArticleV1("A", paragraphs = listOf(listOf(LegalSegmentV1("p"))))))
        val snapshot = realSource.snapshot(LegalDocumentType.CGU, text)

        assertEquals(text, mapper.readValue<LegalTextV1>(snapshot.content))
        assertTrue(snapshot.version.startsWith("2027-01-02-"))
    }

    /**
     * Pins the serialized form and its hash. If this breaks after a Jackson upgrade or an annotation change,
     * every text would be re-published as a new version at the next deploy and every association asked to
     * accept again: fix the serialization, do not update the expected values.
     */
    @Test
    fun `serialized form and hash are pinned`() {
        val text = LegalTextV1(
            title = "T",
            lastUpdated = "Version en vigueur au 2 janvier 2027",
            intro = "I",
            articles = listOf(
                LegalArticleV1(
                    "A", callout = "C",
                    paragraphs = listOf(listOf(LegalSegmentV1("p "), LegalSegmentV1("lien", "https://x.org/a"))),
                ),
                LegalArticleV1("B", subsections = listOf(LegalSubsectionV1("1.", listOf(listOf(LegalSegmentV1("\u00e9")))))),
            ),
        )

        val snapshot = realSource.snapshot(LegalDocumentType.CGU, text)

        assertEquals(
            """{"title":"T","lastUpdated":"Version en vigueur au 2 janvier 2027","intro":"I","articles":[""" +
                """{"title":"A","callout":"C","paragraphs":[[{"text":"p "},{"text":"lien","href":"https://x.org/a"}]]},""" +
                """{"title":"B","subsections":[{"title":"1.","paragraphs":[[{"text":"é"}]]}]}]}""",
            snapshot.content,
        )
        assertEquals("bf465e1a6b35792fd64f76130f4fd6187ce67758206062f3e918c32bb12ffd39", snapshot.sha256)
        assertEquals("2027-01-02-bf465e1a6b35", snapshot.version)
    }

    @Test
    fun `unknown link tag fails`() {
        assertThrows<LegalTextSourceException> { sourceOf(minimal(paragraph = "Voir <unknown>ici</unknown>.")).load() }
    }

    @Test
    fun `leftover markup fails`() {
        assertThrows<LegalTextSourceException> { sourceOf(minimal(paragraph = "Texte <i>italique</i>")).load() }
        assertThrows<LegalTextSourceException> { sourceOf(minimal(title = "Article <b>1</b>")).load() }
        assertThrows<LegalTextSourceException> {
            sourceOf(minimal(privacyArticle = """{"title": "T", "table": {"head": ["<b>A</b>"], "rows": [["x"]]}}""")).load()
        }
    }

    @Test
    fun `b tags become bold segments, never links`() {
        val text = mapper.readValue<LegalTextV1>(
            sourceOf(minimal(paragraph = "<b>Gras.</b> Puis la <privacy>politique</privacy>."))
                .load().single { it.documentType == LegalDocumentType.CGU }.content,
        )

        assertEquals(
            listOf(
                LegalSegmentV1("Gras.", bold = true),
                LegalSegmentV1(" Puis la "),
                LegalSegmentV1("politique", "$baseUrl/politique-confidentialite"),
                LegalSegmentV1("."),
            ),
            text.articles.single().paragraphs.single(),
        )
    }

    @Test
    fun `a table is kept cell by cell, and may stand without paragraphs`() {
        val privacy = privacyOf(
            minimal(privacyArticle = """{"title": "Durées", "table": {"head": ["Quoi", "Combien"], "rows": [["Don", "six ans"]]}}"""),
        )

        val article = privacy.articles.single()
        assertEquals(LegalTableV1(listOf("Quoi", "Combien"), listOf(listOf("Don", "six ans"))), article.table)
        assertTrue(article.paragraphs.isEmpty())
    }

    @Test
    fun `a ragged or empty table fails`() {
        assertThrows<LegalTextSourceException> {
            sourceOf(minimal(privacyArticle = """{"title": "T", "table": {"head": ["A", "B"], "rows": [["x"]]}}""")).load()
        }
        assertThrows<LegalTextSourceException> {
            sourceOf(minimal(privacyArticle = """{"title": "T", "table": {"head": ["A"], "rows": []}}""")).load()
        }
        assertThrows<LegalTextSourceException> {
            sourceOf(minimal(privacyArticle = """{"title": "T"}""")).load()
        }
    }

    /**
     * Pins the serialized form of the fields added for the privacy policy: an absent intro is left out, a table
     * comes after the original fields, `bold` after `href`. Texts that use none of them keep the form pinned by
     * the test above — otherwise the CGU and CGV would be re-versioned and re-accepted, unchanged.
     */
    @Test
    fun `privacy policy fields are pinned too, an absent intro being left out`() {
        val text = LegalTextV1(
            title = "T", lastUpdated = "Version en vigueur au 2 janvier 2027",
            articles = listOf(
                LegalArticleV1(
                    "A",
                    paragraphs = listOf(listOf(LegalSegmentV1("g", bold = true))),
                    table = LegalTableV1(listOf("h"), listOf(listOf("c"))),
                ),
            ),
        )

        assertEquals(
            """{"title":"T","lastUpdated":"Version en vigueur au 2 janvier 2027","articles":[""" +
                """{"title":"A","paragraphs":[[{"text":"g","bold":true}]],"table":{"head":["h"],"rows":[["c"]]}}]}""",
            realSource.snapshot(LegalDocumentType.PRIVACY, text).content,
        )
    }

    @Test
    fun `unreadable effective date fails`() {
        assertThrows<LegalTextSourceException> { sourceOf(minimal(lastUpdated = "Mise a jour recente")).load() }
    }

    @Test
    fun `missing document fails`() {
        assertThrows<LegalTextSourceException> {
            sourceOf("""{"legal": {"linkTargets": {}, "cguDonors": {}}}""").load()
        }
    }

    private companion object {
        const val DEFAULT_PRIVACY_ARTICLE = """{"title": "Qui est responsable", "paragraphs": ["CommonLink"]}"""
    }
}
