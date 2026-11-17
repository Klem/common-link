package org.commonlink.dto

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonPropertyOrder

/**
 * Structured CGU/CGV text stored in `legal_document.content` when its format is
 * [org.commonlink.entity.LegalContentFormat.JSON_V1], and rendered as-is by the frontend's
 * `LegalDocumentRenderer`.
 *
 * Mirrors the shape of the landing site's `legal.cguDonors` / `legal.cguAssociations` messages
 * (see [org.commonlink.service.LandingLegalTextSource]), with every `<tag>…</tag>` link resolved into a
 * [LegalSegmentV1] carrying an absolute URL: the stored text is self-contained — no HTML, no CSS, no
 * reference to a lookup table that could change after acceptance.
 *
 * The property order is pinned because the serialized form is hashed into the version identifier: a
 * reordering would otherwise publish an unchanged text as a new version.
 */
@JsonPropertyOrder("title", "lastUpdated", "intro", "articles")
data class LegalTextV1(
    val title: String,
    /** Human-readable effective date as shown to the reader, e.g. "Version en vigueur au 9 septembre 2026". */
    val lastUpdated: String,
    val intro: String,
    val articles: List<LegalArticleV1>,
)

/** One article. [paragraphs] and [subsections] may each be empty, never both. */
@JsonPropertyOrder("title", "callout", "paragraphs", "subsections")
@JsonInclude(JsonInclude.Include.NON_EMPTY)
data class LegalArticleV1(
    val title: String,
    /** Highlighted note displayed under the article title. */
    val callout: String? = null,
    /** Each paragraph is a sequence of text segments, some of them links. */
    val paragraphs: List<List<LegalSegmentV1>> = emptyList(),
    /** Numbered inner sections — used by the association CGU's annexes. */
    val subsections: List<LegalSubsectionV1> = emptyList(),
)

/** A numbered inner section of an article. */
@JsonPropertyOrder("title", "paragraphs")
data class LegalSubsectionV1(
    val title: String,
    val paragraphs: List<List<LegalSegmentV1>>,
)

/** A run of text; a link when [href] (absolute URL) is set. */
@JsonPropertyOrder("text", "href")
@JsonInclude(JsonInclude.Include.NON_NULL)
data class LegalSegmentV1(
    val text: String,
    val href: String? = null,
)
