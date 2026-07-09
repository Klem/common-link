import type { VerificationStatus } from '@/types/association';
import type { DocumentSlotDto, OptionalDocumentDto } from '@/types/verification';

export type { Page } from '@/types/payment';

/** Full set of document types handled by the admin verification console. */
export const AssociationDocumentType = {
  VERIF_STATUTS: 'VERIF_STATUTS',
  VERIF_RNA_RECEIPT: 'VERIF_RNA_RECEIPT',
  VERIF_REPRESENTATIVE_ID: 'VERIF_REPRESENTATIVE_ID',
  MANDATE_STATUTS: 'MANDATE_STATUTS',
  MANDATE_RESCRIT: 'MANDATE_RESCRIT',
  OPTIONAL: 'OPTIONAL',
} as const;
export type AssociationDocumentType = typeof AssociationDocumentType[keyof typeof AssociationDocumentType];

/** Row summary returned by `GET /api/admin/verifications`. */
export interface AdminVerificationSummaryDto {
  associationId: string;
  name: string;
  /** SIREN or RNA W-number. */
  identifier: string;
  status: VerificationStatus;
  submittedAt: string | null;
  docCount: number;
}

/** Result of `GET /api/admin/verifications/{associationId}/registry-precheck`. Informational only. */
export interface RegistryPreCheckDto {
  associationExists: boolean | null;
  siren: string | null;
  rna: string | null;
  /** 'A' = active, 'C' = ceased. Null if no SIREN or INSEE unavailable. */
  etatAdministratif: string | null;
  joafeDeclarationFound: boolean | null;
  dissolutionDetected: boolean | null;
  bodaccProcedureFound: boolean | null;
  checkedAt: string;
  warnings: string[];
}

/** Full dossier returned by `GET /api/admin/verifications/{associationId}`. */
export interface AdminVerificationDetailDto {
  associationId: string;
  name: string;
  identifier: string;
  status: VerificationStatus;
  rejectionReason: string | null;
  submittedAt: string | null;
  verifiedAt: string | null;
  docCount: number;
  requiredDocuments: DocumentSlotDto[];
  optionalDocuments: OptionalDocumentDto[];
}
