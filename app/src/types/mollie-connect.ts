import type { BeneficialOwnerType } from './admin';
import type { VerificationDocType } from './verification';

export const MollieOnboardingStatus = {
  NEEDS_DATA: 'NEEDS_DATA',
  IN_REVIEW: 'IN_REVIEW',
  COMPLETED: 'COMPLETED',
} as const;
export type MollieOnboardingStatus = typeof MollieOnboardingStatus[keyof typeof MollieOnboardingStatus];

export const MolliePopupMessage = {
  CONNECTED: 'MOLLIE_KYC_CONNECTED',
  ERROR: 'MOLLIE_KYC_ERROR',
} as const;
export type MolliePopupMessage = typeof MolliePopupMessage[keyof typeof MolliePopupMessage];

export interface MollieKycStatus {
  connected: boolean;
  pending: boolean;
  broken: boolean;
  onboardingStatus: MollieOnboardingStatus | null;
  canReceivePayments: boolean | null;
  /** Deep link to the Mollie hosted onboarding wizard; null once onboarding is complete. */
  dashboardUrl: string | null;
  /** True when the dev/staging "simulate KYC validation" endpoint is enabled. Always false in prod. */
  canForceComplete: boolean;
}

/** Legal structure variant derived server-side from the KYB INSEE legal category (`structureVariantOf`). */
export const MollieStructureVariant = {
  DECLARED: 'DECLARED',
  ARUP: 'ARUP',
  ALSACE_MOSELLE: 'ALSACE_MOSELLE',
  UNKNOWN: 'UNKNOWN',
} as const;
export type MollieStructureVariant = typeof MollieStructureVariant[keyof typeof MollieStructureVariant];

/** A confirmed, non-discarded person from the KYB dossier — name and role only, never a birth date. */
export interface OnboardingBeneficialOwner {
  name: string;
  role: string | null;
  type: BeneficialOwnerType;
}

/** A KYB document already provided to CommonLink, re-downloadable via `GET /api/association/documents/{id}/content`. */
export interface OnboardingProvidedDocument {
  id: string;
  type: VerificationDocType;
}

/** `GET /api/mollie/connect/onboarding-context` — read-only projection of CommonLink's KYB data. Unlocks nothing. */
export interface MollieOnboardingContext {
  structureVariant: MollieStructureVariant;
  /** ISO-8601 instant of the KYB approval, null if not verified. */
  verifiedAt: string | null;
  beneficialOwners: OnboardingBeneficialOwner[];
  providedDocuments: OnboardingProvidedDocument[];
  siren: string | null;
  /** ISO-8601 instant of the self-completion attestation, null if never attested. */
  selfCompletionAttestedAt: string | null;
}
