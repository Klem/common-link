/** A versioned legal document (notice ACPR ; art. 1740 A CGI — preuve d'acceptation). */
export const LegalDocumentType = {
  CGU: 'CGU',
  CGV: 'CGV',
} as const;
export type LegalDocumentType = typeof LegalDocumentType[keyof typeof LegalDocumentType];

/** Who accepted a document — donor at donation time, association at campaign publish time. */
export const LegalAcceptanceSubjectType = {
  DONOR: 'DONOR',
  ASSOCIATION: 'ASSOCIATION',
} as const;
export type LegalAcceptanceSubjectType =
  typeof LegalAcceptanceSubjectType[keyof typeof LegalAcceptanceSubjectType];

/** Current published text of a CGU/CGV document. */
export interface LegalDocumentDto {
  documentType: LegalDocumentType;
  version: string;
  content: string;
  publishedAt: string;
}

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
