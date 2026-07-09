/** Eligibility category declared by the association when signing the fiscal mandate. */
export const MandateEligibility = {
  OIG_66: 'OIG_66',
  OIG_75_COLUCHE: 'OIG_75_COLUCHE',
  PUBLIC_UTILITY_66: 'PUBLIC_UTILITY_66',
} as const;
export type MandateEligibility = (typeof MandateEligibility)[keyof typeof MandateEligibility];

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
}

/** Request body for POST /api/association/mandate/sign. */
export interface SignMandateRequest {
  eligibility: MandateEligibility;
  accepted: boolean;
}
