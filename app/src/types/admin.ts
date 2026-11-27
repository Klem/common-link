import type { PaymentProviderId, ProviderReadinessStatus } from '@/types/paymentProvider';
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

export const BeneficialOwnerType = {
  BENEFICIAL_OWNER: 'BENEFICIAL_OWNER',
  REPRESENTATIVE: 'REPRESENTATIVE',
} as const;
export type BeneficialOwnerType = typeof BeneficialOwnerType[keyof typeof BeneficialOwnerType];

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

/** A beneficial owner or legal representative record returned by the API. */
export interface BeneficialOwnerDto {
  id: string;
  name: string;
  role: string | null;
  dateOfBirth: string | null;
  origin: BeneficialOwnerOrigin;
  type: BeneficialOwnerType;
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
  /** Entries were found and a compliance officer ruled them false positives — no longer blocking. */
  HIT_CLEARED: 'HIT_CLEARED',
  UNAVAILABLE: 'UNAVAILABLE',
} as const;
export type FreezeScreenStatus = typeof FreezeScreenStatus[keyof typeof FreezeScreenStatus];

/** Response body for `GET /api/admin/verifications/{id}/freeze-screen-status`. */
export interface FreezeScreenStatusDto {
  status: FreezeScreenStatus;
  checkedAt: string | null;
}

/** Request body for adding a beneficial owner or legal representative. */
export interface AddBeneficialOwnerRequest {
  name: string;
  role?: string | null;
  dateOfBirth?: string | null;
  origin: BeneficialOwnerOrigin;
  type: BeneficialOwnerType;
}

/** Maximum curator reason length for a payment-provider change (mirror of `PaymentProviderChangeService.REASON_MAX_LENGTH`). */
export const PAYMENT_PROVIDER_REASON_MAX_LENGTH = 500;

/** One provider in a {@link PaymentProviderPreview}; `readiness` null = provider not available yet (cannot collect). */
export interface PaymentProviderOption {
  provider: PaymentProviderId;
  readiness: ProviderReadinessStatus | null;
}

/** Curator preview of an association's payment provider (`GET /api/admin/associations/{id}/payment-provider`). */
export interface PaymentProviderPreview {
  associationId: string;
  currentProvider: PaymentProviderId;
  providers: PaymentProviderOption[];
  /** Campaigns currently LIVE. */
  liveCampaigns: number;
  widgetEnabled: boolean;
  /** Donations still payable at the current provider; they stay with it after a change. */
  pendingDonations: number;
}

/** Body of `PUT /api/admin/associations/{id}/payment-provider`. */
export interface ChangePaymentProviderRequest {
  provider: PaymentProviderId;
  /** Mandatory, non-blank after trim, at most {@link PAYMENT_PROVIDER_REASON_MAX_LENGTH} characters. */
  reason: string;
}

/** Outcome of one HelloAsso key check (mirror of the backend `HelloAssoLinkAttemptOutcome`, same order). */
export const HelloAssoLinkOutcome = {
  /** The organization is the association verified on CommonLink. */
  MATCHED: 'MATCHED',
  /** No RNA on one side or both, and the names differ. */
  NAME_MISMATCH: 'NAME_MISMATCH',
  /** Both sides carry an RNA and they differ. */
  RNA_MISMATCH: 'RNA_MISMATCH',
  /** The organization is already linked to another CommonLink association. */
  ORGANIZATION_ALREADY_LINKED: 'ORGANIZATION_ALREADY_LINKED',
  /** HelloAsso refused the keys. */
  INVALID_KEYS: 'INVALID_KEYS',
  /** The keys reach no single organization. */
  ORGANIZATION_UNRESOLVED: 'ORGANIZATION_UNRESOLVED',
  /** HelloAsso did not answer. */
  HELLOASSO_UNAVAILABLE: 'HELLOASSO_UNAVAILABLE',
  /** HelloAsso rate limit reached. */
  HELLOASSO_RATE_LIMITED: 'HELLOASSO_RATE_LIMITED',
} as const;
export type HelloAssoLinkOutcome = typeof HelloAssoLinkOutcome[keyof typeof HelloAssoLinkOutcome];

/** Number of key checks that ended with `outcome`. */
export interface HelloAssoLinkOutcomeCount {
  outcome: HelloAssoLinkOutcome;
  count: number;
}

/** `GET /api/admin/helloasso/link-attempts/stats`: key checks over `[from, to)`, every outcome listed (0 included). */
export interface HelloAssoLinkAttemptStats {
  /** ISO-8601 instant, inclusive. */
  from: string;
  /** ISO-8601 instant, exclusive. */
  to: string;
  total: number;
  /** Distinct associations with at least one attempt. */
  associations: number;
  byOutcome: HelloAssoLinkOutcomeCount[];
}

/** One HelloAsso key check, as shown to curators. No secret: the client id is masked. */
export interface HelloAssoLinkAttemptRow {
  id: string;
  /** ISO-8601 instant. */
  attemptedAt: string;
  associationId: string;
  associationName: string;
  outcome: HelloAssoLinkOutcome;
  clientIdMasked: string | null;
  organizationsCount: number | null;
  organizationSlug: string | null;
  organizationName: string | null;
  organizationType: string | null;
  /** HelloAsso `rnaNumber`, null when blank. */
  organizationRna: string | null;
  /** CommonLink's RNA of the association at the time of the attempt. */
  associationRna: string | null;
}

/**
 * Page of `GET /api/admin/helloasso/link-attempts`, newest first. Not the Spring `Page` shape of `@/types/payment`:
 * `page` is the 0-based index, and there is no `first` / `last`.
 */
export interface HelloAssoLinkAttemptPage {
  content: HelloAssoLinkAttemptRow[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

/** Query of the HelloAsso link-attempt endpoints; the period is `[from, to)`, ISO-8601 instants. */
export interface HelloAssoLinkAttemptQuery {
  from: string;
  to: string;
  /** Only this outcome; every outcome when absent. */
  outcome?: HelloAssoLinkOutcome | null;
  /** 0-based page index. */
  page?: number;
  /** Page size, at most 100 server-side. */
  size?: number;
}
