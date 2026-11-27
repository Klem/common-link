/** Eligibility category declared by the association when signing the fiscal mandate. */
export const MandateEligibility = {
  OIG_66: 'OIG_66',
  OIG_75_COLUCHE: 'OIG_75_COLUCHE',
} as const;
export type MandateEligibility = (typeof MandateEligibility)[keyof typeof MandateEligibility];

/**
 * Information a fiscal receipt (Cerfa 11580) cannot be issued without. While any is missing, the
 * mandate cannot be signed and, once signed, donation collection is suspended (backend
 * `MandateService` / collection guard).
 */
export const ReceiptPrerequisite = {
  /** No receipt signature saved for the authorised signer. */
  SIGNATURE: 'SIGNATURE',
  /** Profile `legalObject` (objet social) is blank. */
  LEGAL_OBJECT: 'LEGAL_OBJECT',
  /** Any of the profile `addressLine1`, `postalCode`, `city` is blank. */
  HEAD_OFFICE_ADDRESS: 'HEAD_OFFICE_ADDRESS',
} as const;
export type ReceiptPrerequisite = (typeof ReceiptPrerequisite)[keyof typeof ReceiptPrerequisite];

/** The two document types required for the fiscal mandate. */
export const MandateDocType = {
  MANDATE_STATUTS: 'MANDATE_STATUTS',
  MANDATE_RESCRIT: 'MANDATE_RESCRIT',
} as const;
export type MandateDocType = (typeof MandateDocType)[keyof typeof MandateDocType];

/** Metadata for one required mandate document slot. */
export interface MandateDocumentSlotDto {
  docType: MandateDocType;
  uploaded: boolean;
  id?: string;
  fileName?: string;
  sizeBytes?: number;
  uploadedAt?: string;
}

/** Full fiscal mandate state returned by GET /api/association/mandate. */
export interface MandateStateDto {
  signed: boolean;
  reference?: string | null;
  signedAt?: string | null;
  eligibility?: MandateEligibility | null;
  revokedAt?: string | null;
  mandateDocs: MandateDocumentSlotDto[];
  /** True when association is not yet VERIFIED — signing is gated. */
  blocked: boolean;
  /** Receipt prerequisites still missing; always present, empty when receipts can be issued. */
  receiptPrerequisitesMissing: ReceiptPrerequisite[];
}

/** Request body for POST /api/association/mandate/sign. */
export interface SignMandateRequest {
  eligibility: MandateEligibility;
  accepted: boolean;
}
