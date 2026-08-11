import type { VerificationStatus } from '@/types/association';
import type { DocumentSlotDto, OptionalDocumentDto } from '@/types/verification';

export type { Page } from '@/types/payment';

export const RiskLevel = {
  LOW: 'LOW',
  STANDARD: 'STANDARD',
  HIGH: 'HIGH',
} as const;
export type RiskLevel = typeof RiskLevel[keyof typeof RiskLevel];

export const ScopeVerdict = {
  IN_SCOPE: 'IN_SCOPE',
  OUT_OF_SCOPE: 'OUT_OF_SCOPE',
  UNDETERMINED: 'UNDETERMINED',
} as const;
export type ScopeVerdict = typeof ScopeVerdict[keyof typeof ScopeVerdict];

export const BeneficialOwnerOrigin = {
  DECLARED: 'DECLARED',
  REGISTRY: 'REGISTRY',
  STATUTS: 'STATUTS',
} as const;
export type BeneficialOwnerOrigin = typeof BeneficialOwnerOrigin[keyof typeof BeneficialOwnerOrigin];

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

/** A persisted registry pre-check scan. Informational only. */
export interface RegistryPreCheckDto {
  /** Identifier of the persisted scan row. */
  id: string;
  associationExists: boolean | null;
  siren: string | null;
  rna: string | null;
  legalCategory: string | null;
  /** Loi 1901 scope verdict: IN_SCOPE, OUT_OF_SCOPE, or UNDETERMINED. */
  scopeVerdict: ScopeVerdict;
  /** 'A' = active, 'C' = ceased. Null if no SIREN or INSEE unavailable. */
  etatAdministratif: string | null;
  joafeDeclarationFound: boolean | null;
  dissolutionDetected: boolean | null;
  bodaccProcedureFound: boolean | null;
  checkedAt: string;
  warnings: string[];
  /** Legal representatives collected from the registry scan. */
  officers: string[];
  /** RNA administrative status. Null if not available. */
  rnaActive: boolean | null;
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
  riskLevel: RiskLevel;
  requiredDocuments: DocumentSlotDto[];
  optionalDocuments: OptionalDocumentDto[];
}

/** Vigilance measures returned by `GET /api/admin/verifications/{associationId}/vigilance`. */
export interface VigilanceMeasuresDto {
  riskLevel: RiskLevel;
  classificationVersion: string;
  description: string;
  reviewFrequency: string;
  requiredDocuments: string[];
}

/** A beneficial owner record returned by the beneficial owners API. */
export interface BeneficialOwnerDto {
  id: string;
  name: string;
  role: string | null;
  dateOfBirth: string | null;
  origin: BeneficialOwnerOrigin;
  collectedAt: string;
  confirmedBy: string;
  discarded: boolean;
  discardedBy: string | null;
  discardedAt: string | null;
}

/** Four-state indicator of the most recent onboarding freeze screening. */
export const FreezeScreenStatus = {
  NOT_PERFORMED: 'NOT_PERFORMED',
  PASSED: 'PASSED',
  HIT: 'HIT',
  UNAVAILABLE: 'UNAVAILABLE',
} as const;
export type FreezeScreenStatus = typeof FreezeScreenStatus[keyof typeof FreezeScreenStatus];

/** Response body for `GET /api/admin/verifications/{id}/freeze-screen-status`. */
export interface FreezeScreenStatusDto {
  status: FreezeScreenStatus;
  checkedAt: string | null;
}

/** Request body for adding a beneficial owner. */
export interface AddBeneficialOwnerRequest {
  name: string;
  role?: string | null;
  dateOfBirth?: string | null;
  origin: BeneficialOwnerOrigin;
}
