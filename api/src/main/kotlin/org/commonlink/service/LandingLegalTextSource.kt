package org.commonlink.service

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.commonlink.dto.LegalArticleV1
import org.commonlink.dto.LegalSegmentV1
import org.commonlink.dto.LegalSubsectionV1
import org.commonlink.dto.LegalTableV1
import org.commonlink.dto.LegalTextV1
import org.commonlink.entity.LegalDocumentType
import org.commonlink.exception.LegalTextSourceException
import org.springframework.beans.factory.annotation.Value
import org.springframework.core.io.Resource
import org.springframework.stereotype.Component
import java.security.MessageDigest
import java.time.LocalDate

/**
 * Reads the legal texts (CGU, CGV, privacy policy) from the landing site's `messages/fr.json` — the single source of truth, copied
 * into the jar at build time by `processResources` (see `api/build.gradle.kts`) — and turns each one into a
 * versioned, hashable [LegalTextV1].
 *
 * - `legal.cguDonors` → [LegalDocumentType.CGU], `legal.cguAssociations` → [LegalDocumentType.CGV],
 *   `legal.politiqueConfidentialite` → [LegalDocumentType.PRIVACY].
 * - Every text has `title`, `lastUpdated`, an optional `intro` and `articles[]`; an article has a `title`, an
 *   optional `callout`, and at least one of `table` (`head[]` + `rows[][]`, plain cells), `paragraphs[]`,
 *   `subsections[]`.
 * - Links are written `<privacy>text</privacy>` in the landing texts; the tag name is resolved through
 *   `legal.linkTargets` (site-relative path) and prefixed with [siteBaseUrl], so the stored text carries
 *   absolute URLs and no markup. `<b>text</b>` is the one formatting tag: it gives a bold segment, so `b`
 *   can never be a link tag.
 * - The version is `<effective date>-<first 12 hex of the SHA-256>`: the date is read from `lastUpdated`
 *   ("Version en vigueur au 9 septembre 2026"), the hash covers the serialized [LegalTextV1]. Two changes
 *   on the same day therefore give two versions.
 *
 * Any inconsistency throws [LegalTextSourceException]: this runs at startup and must stop the boot.
 */
@Component
class LandingLegalTextSource(
    @Value("\${app.legal.source}") private val source: Resource,
    @Value("\${app.legal.site-base-url}") siteBaseUrl: String,
) {
    private val siteBaseUrl = siteBaseUrl.trimEnd('/')

    /**
     * One text ready to be stored.
     *
     * @property content serialized [LegalTextV1] — exactly the bytes hashed into [sha256].
     * @property sha256 lowercase hex SHA-256 of [content] (UTF-8).
     */
    data class Snapshot(
        val documentType: LegalDocumentType,
        val version: String,
        val content: String,
        val sha256: String,
    )

    /** Reads and converts every [LegalDocumentType]. */
    fun load(): List<Snapshot> {
        val root = source.inputStream.use { MAPPER.readTree(it) }
        val legal = root.path("legal")
        val linkTargets = legal.path("linkTargets")
        if (!linkTargets.isObject) throw LegalTextSourceException("legal.linkTargets missing in ${source.description}")
        return SOURCE_KEYS.map { (type, key) ->
            val node = legal.path(key)
            if (!node.isObject) throw LegalTextSourceException("legal.$key missing in ${source.description}")
            snapshot(type, convert(key, node, linkTargets))
        }
    }

    /** Builds the [Snapshot] of an already converted [text] — public for tests. */
    fun snapshot(type: LegalDocumentType, text: LegalTextV1): Snapshot {
        val content = MAPPER.writeValueAsString(text)
        val sha256 = MessageDigest.getInstance("SHA-256").digest(content.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
        val version = "${effectiveDate(type, text.lastUpdated)}-${sha256.take(VERSION_HASH_LENGTH)}"
        return Snapshot(type, version, content, sha256)
    }

    private fun convert(key: String, node: JsonNode, linkTargets: JsonNode): LegalTextV1 {
        val articles = node.path("articles")
        if (!articles.isArray || articles.isEmpty) throw LegalTextSourceException("legal.$key.articles is empty")
        return LegalTextV1(
            title = plain(node, "title", "legal.$key"),
            lastUpdated = plain(node, "lastUpdated", "legal.$key"),
            intro = if (node.has("intro")) plain(node, "intro", "legal.$key") else null,
            articles = articles.mapIndexed { i, article ->
                val where = "legal.$key.articles[$i]"
                val converted = LegalArticleV1(
                    title = plain(article, "title", where),
                    callout = if (article.has("callout")) plain(article, "callout", where) else null,
                    paragraphs = paragraphs(article.path("paragraphs"), linkTargets, "$where.paragraphs"),
                    subsections = article.path("subsections").mapIndexed { j, sub ->
                        LegalSubsectionV1(
                            title = plain(sub, "title", "$where.subsections[$j]"),
                            paragraphs = paragraphs(sub.path("paragraphs"), linkTargets, "$where.subsections[$j].paragraphs"),
                        )
                    },
                    table = if (article.has("table")) table(article.path("table"), "$where.table") else null,
                )
                if (converted.paragraphs.isEmpty() && converted.subsections.isEmpty() && converted.table == null) {
                    throw LegalTextSourceException("$where has neither table, paragraphs nor subsections")
                }
                converted
            },
        )
    }

    /** A mandatory, markup-free string field. */
    private fun plain(node: JsonNode, field: String, where: String): String {
        val value = node.path(field)
        if (!value.isTextual || value.asText().isBlank()) throw LegalTextSourceException("$where.$field is missing or blank")
        if (MARKUP.containsMatchIn(value.asText())) throw LegalTextSourceException("$where.$field contains markup")
        return value.asText()
    }

    /** A table of markup-free cells: a non-empty header, then at least one row of the same width. */
    private fun table(node: JsonNode, where: String): LegalTableV1 {
        fun cells(row: JsonNode, rowWhere: String): List<String> {
            if (!row.isArray || row.isEmpty) throw LegalTextSourceException("$rowWhere is empty")
            return row.mapIndexed { i, cell ->
                val value = cell.asText()
                if (!cell.isTextual || value.isBlank()) throw LegalTextSourceException("$rowWhere[$i] is missing or blank")
                if (MARKUP.containsMatchIn(value)) throw LegalTextSourceException("$rowWhere[$i] contains markup")
                value
            }
        }
        val head = cells(node.path("head"), "$where.head")
        val rows = node.path("rows")
        if (!rows.isArray || rows.isEmpty) throw LegalTextSourceException("$where.rows is empty")
        return LegalTableV1(
            head = head,
            rows = rows.mapIndexed { i, row ->
                cells(row, "$where.rows[$i]").also {
                    if (it.size != head.size) throw LegalTextSourceException("$where.rows[$i] has ${it.size} cells, head has ${head.size}")
                }
            },
        )
    }

    private fun paragraphs(node: JsonNode, linkTargets: JsonNode, where: String): List<List<LegalSegmentV1>> =
        node.mapIndexed { i, p ->
            if (!p.isTextual || p.asText().isBlank()) throw LegalTextSourceException("$where[$i] is missing or blank")
            segments(p.asText(), linkTargets, "$where[$i]")
        }

    /** Splits `a <privacy>b</privacy> c <b>d</b>` into text / link / text / bold segments. */
    private fun segments(text: String, linkTargets: JsonNode, where: String): List<LegalSegmentV1> {
        val result = mutableListOf<LegalSegmentV1>()
        var cursor = 0
        for (match in LINK.findAll(text)) {
            if (match.range.first > cursor) result += LegalSegmentV1(text.substring(cursor, match.range.first))
            val (tag, label) = match.destructured
            cursor = match.range.last + 1
            if (tag == BOLD_TAG) {
                result += LegalSegmentV1(label, bold = true)
                continue
            }
            val path = linkTargets.path(tag)
            if (!path.isTextual || !path.asText().startsWith("/")) {
                throw LegalTextSourceException("$where uses link tag <$tag> with no site-relative target in legal.linkTargets")
            }
            result += LegalSegmentV1(label, "$siteBaseUrl${path.asText()}")
        }
        if (cursor < text.length) result += LegalSegmentV1(text.substring(cursor))
        if (result.any { MARKUP.containsMatchIn(it.text) }) throw LegalTextSourceException("$where contains unsupported markup")
        return result
    }

    /** Reads the effective date out of "Version en vigueur au 9 septembre 2026" (also "1er"). */
    private fun effectiveDate(type: LegalDocumentType, lastUpdated: String): LocalDate {
        val match = FRENCH_DATE.find(lastUpdated)
            ?: throw LegalTextSourceException("$type: no French date found in lastUpdated \"$lastUpdated\"")
        val (day, month, year) = match.destructured
        return LocalDate.of(year.toInt(), FRENCH_MONTHS.indexOf(month.lowercase()) + 1, day.toInt())
    }

    companion object {
        /** Landing message key of each document. */
        val SOURCE_KEYS = listOf(
            LegalDocumentType.CGU to "cguDonors",
            LegalDocumentType.CGV to "cguAssociations",
            LegalDocumentType.PRIVACY to "politiqueConfidentialite",
        )

        /** The formatting tag turned into a bold segment — never resolved as a link. */
        private const val BOLD_TAG = "b"

        /** "2026-09-09-" + 12 hex = 23 characters, within `legal_document.version` VARCHAR(32). */
        const val VERSION_HASH_LENGTH = 12

        /**
         * Dedicated mapper, independent from the application's `ObjectMapper` bean: a change of global
         * serialization settings must never alter the bytes hashed into a version.
         */
        private val MAPPER = jacksonObjectMapper()

        private val LINK = Regex("<([a-zA-Z]+)>([^<]+)</\\1>")
        private val MARKUP = Regex("</?[a-zA-Z][^>]*>")
        private val FRENCH_MONTHS = listOf(
            "janvier", "février", "mars", "avril", "mai", "juin",
            "juillet", "août", "septembre", "octobre", "novembre", "décembre",
        )
        private val FRENCH_DATE =
            Regex("(\\d{1,2})(?:er)?\\s+(${FRENCH_MONTHS.joinToString("|")})\\s+(\\d{4})", RegexOption.IGNORE_CASE)
    }
}
