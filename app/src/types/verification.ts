import type { VerificationStatus } from './association';

/** The three document types required for KYC verification. */
export const VerificationDocType = {
  VERIF_STATUTS: 'VERIF_STATUTS',
  VERIF_RNA_RECEIPT: 'VERIF_RNA_RECEIPT',
  VERIF_REPRESENTATIVE_ID: 'VERIF_REPRESENTATIVE_ID',
} as const;
export type VerificationDocType = typeof VerificationDocType[keyof typeof VerificationDocType];

/** Metadata for one required verification document slot. */
export interface DocumentSlotDto {
  docType: VerificationDocType;
  uploaded: boolean;
  id?: string;
  fileName?: string;
  sizeBytes?: number;
  uploadedAt?: string;
}

/** Full KYC verification state returned by GET /api/association/verification. */
export interface VerificationStateDto {
  status: VerificationStatus;
  rejectionReason?: string | null;
  submittedAt?: string | null;
  verifiedAt?: string | null;
  requiredDocuments: DocumentSlotDto[];
}

/** Categories for supplementary optional documents. */
export const OptionalDocCategory = {
  financier: 'financier',
  rapport: 'rapport',
  justificatif: 'justificatif',
  autre: 'autre',
} as const;
export type OptionalDocCategory = typeof OptionalDocCategory[keyof typeof OptionalDocCategory];

/** Metadata for a supplementary optional document. */
export interface OptionalDocumentDto {
  id: string;
  fileName: string;
  category?: string | null;
  contentType: string;
  sizeBytes: number;
  uploadedAt: string;
}
