/**
 * A versioned legal document whose acceptance is recorded (ACPR notice; art. 1740 A CGI proof of acceptance):
 * `CGU` donor terms, `CGV` association terms, `PRIVACY` privacy policy.
 */
export const LegalDocumentType = {
  CGU: 'CGU',
  CGV: 'CGV',
  PRIVACY: 'PRIVACY',
} as const;
export type LegalDocumentType = typeof LegalDocumentType[keyof typeof LegalDocumentType];

/**
 * What an association accepts (campaign publication, post-login gate), in this order: the `CGV` document, which
 * holds the beneficiary-organisation terms, then the privacy policy. Mirrors
 * `LegalAcceptanceService.ASSOCIATION_DOCUMENTS`.
 */
export const ASSOCIATION_LEGAL_DOCUMENTS: readonly LegalDocumentType[] = [
  LegalDocumentType.CGV,
  LegalDocumentType.PRIVACY,
];

/** One publish-dialog checkbox: ticked or not, and the version displayed. Mirrors `PublishTermsAcceptance`. */
export interface PublishTermsAcceptance {
  accepted: boolean;
  version: string | null;
}

/** The publish dialog's checkboxes, per association document (`ASSOCIATION_LEGAL_DOCUMENTS`). */
export type PublishTermsAcceptances = Partial<Record<LegalDocumentType, PublishTermsAcceptance>>;

/** Who accepted a document — donor at donation time, association at campaign publish time. */
export const LegalAcceptanceSubjectType = {
  DONOR: 'DONOR',
  ASSOCIATION: 'ASSOCIATION',
} as const;
export type LegalAcceptanceSubjectType =
  typeof LegalAcceptanceSubjectType[keyof typeof LegalAcceptanceSubjectType];

/**
 * How `LegalDocumentDto.content` must be read: plain text (the first placeholder versions) or a
 * serialized `LegalTextV1` (texts injected from the landing site since V97).
 */
export const LegalContentFormat = {
  TEXT: 'TEXT',
  JSON_V1: 'JSON_V1',
} as const;
export type LegalContentFormat = typeof LegalContentFormat[keyof typeof LegalContentFormat];

/** A run of text; a link (absolute URL, opened in a new tab) when `href` is set, bold when `bold` is true. */
export interface LegalSegment {
  text: string;
  href?: string;
  bold?: boolean;
}

/** One paragraph = a sequence of segments. */
export type LegalParagraph = LegalSegment[];

export interface LegalSubsection {
  title: string;
  paragraphs: LegalParagraph[];
}

/** Plain-text table: one header row, then body rows of the same width. */
export interface LegalTable {
  head: string[];
  rows: string[][];
}

/** One article, read in this order: title, callout, table, paragraphs, subsections. */
export interface LegalArticle {
  title: string;
  callout?: string;
  table?: LegalTable;
  paragraphs?: LegalParagraph[];
  subsections?: LegalSubsection[];
}

/** Structured legal text (CGU, CGV, privacy policy) — mirrors `LegalTextV1` on the backend. No HTML, no CSS. */
export interface LegalTextV1 {
  title: string;
  /** Effective date as shown to the reader, e.g. "Version en vigueur au 9 septembre 2026". */
  lastUpdated: string;
  /** Absent from the privacy policy. */
  intro?: string;
  articles: LegalArticle[];
}

/** Current published text of a legal document. */
export interface LegalDocumentDto {
  documentType: LegalDocumentType;
  /** Opaque identifier (date + hash) to send back when accepting this exact text. */
  version: string;
  format: LegalContentFormat;
  content: string;
  publishedAt: string;
}

/**
 * `code` of the 409 returned when the accepted version is no longer the current one: a new text
 * was published while the form was open. The client reloads the text and asks again.
 */
export const LEGAL_VERSION_OUTDATED = 'LEGAL_VERSION_OUTDATED';

/** Whether the authenticated association already has a standing acceptance of the current version. */
export interface LegalAcceptanceStateDto {
  documentType: LegalDocumentType;
  currentVersion: string;
  accepted: boolean;
}

/** One proof-of-acceptance row — compliance restitution only. */
export interface LegalAcceptanceDto {
  id: string;
  subjectType: LegalAcceptanceSubjectType;
  subjectId: string;
  documentType: LegalDocumentType;
  documentVersion: string;
  acceptedAt: string;
  signerName: string | null;
  signerEmail: string | null;
  donationId: string | null;
  campaignId: string | null;
}
