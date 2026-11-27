package org.commonlink.dto

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonPropertyOrder

/**
 * Structured legal text (CGU, CGV, privacy policy) stored in `legal_document.content` when its format is
 * [org.commonlink.entity.LegalContentFormat.JSON_V1], and rendered as-is by the frontend's
 * `LegalDocumentRenderer`.
 *
 * Mirrors the shape of the landing site's `legal.cguDonors` / `legal.cguAssociations` /
 * `legal.politiqueConfidentialite` messages
 * (see [org.commonlink.service.LandingLegalTextSource]), with every `<tag>…</tag>` link resolved into a
 * [LegalSegmentV1] carrying an absolute URL: the stored text is self-contained — no HTML, no CSS, no
 * reference to a lookup table that could change after acceptance.
 *
 * The property order is pinned because the serialized form is hashed into the version identifier: a
 * reordering would otherwise publish an unchanged text as a new version. Fields added after the first
 * published versions (an absent [LegalTextV1.intro], [LegalArticleV1.table], [LegalSegmentV1.bold]) are left
 * out of the serialized form when unused, so the texts that do not use them keep their bytes, hash and version.
 */
@JsonPropertyOrder("title", "lastUpdated", "intro", "articles")
data class LegalTextV1(
    val title: String,
    /** Human-readable effective date as shown to the reader, e.g. "Version en vigueur au 9 septembre 2026". */
    val lastUpdated: String,
    /** Introductory paragraph, left out of the serialized form when absent (the privacy policy has none). */
    @get:JsonInclude(JsonInclude.Include.NON_NULL)
    val intro: String? = null,
    val articles: List<LegalArticleV1>,
)

/**
 * One article. [table], [paragraphs] and [subsections] may each be empty, never all three. Reading order:
 * title, [callout], [table], [paragraphs], [subsections].
 */
@JsonPropertyOrder("title", "callout", "paragraphs", "subsections", "table")
@JsonInclude(JsonInclude.Include.NON_EMPTY)
data class LegalArticleV1(
    val title: String,
    /** Highlighted note displayed under the article title. */
    val callout: String? = null,
    /** Each paragraph is a sequence of text segments, some of them links. */
    val paragraphs: List<List<LegalSegmentV1>> = emptyList(),
    /** Numbered inner sections — used by the association CGU's annexes. */
    val subsections: List<LegalSubsectionV1> = emptyList(),
    /** Table shown before the paragraphs — used by the privacy policy's retention periods. */
    val table: LegalTableV1? = null,
)

/** A table of plain-text cells: one header row, then body rows of the same width. */
@JsonPropertyOrder("head", "rows")
data class LegalTableV1(
    val head: List<String>,
    val rows: List<List<String>>,
)

/** A numbered inner section of an article. */
@JsonPropertyOrder("title", "paragraphs")
data class LegalSubsectionV1(
    val title: String,
    val paragraphs: List<List<LegalSegmentV1>>,
)

/** A run of text; a link when [href] (absolute URL) is set, emphasized when [bold] is true. */
@JsonPropertyOrder("text", "href", "bold")
@JsonInclude(JsonInclude.Include.NON_NULL)
data class LegalSegmentV1(
    val text: String,
    val href: String? = null,
    /** Set (to true) on emphasized runs only, absent otherwise. */
    val bold: Boolean? = null,
)
