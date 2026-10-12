import api from '@/lib/api';
import type { MollieKycStatus, MollieOnboardingContext } from '@/types/mollie-connect';

interface MollieAuthUrlDto {
  authUrl: string;
}

export const getMollieAuthUrl = (): Promise<MollieAuthUrlDto> =>
  api.get<MollieAuthUrlDto>('/api/mollie/connect/auth-url').then((r) => r.data);

export const getMollieKycStatus = (): Promise<MollieKycStatus> =>
  api.get<MollieKycStatus>('/api/mollie/connect/status').then((r) => r.data);

/** DEV/STAGING only — simulates a Mollie KYC validation (403 when disabled server-side). */
export const forceCompleteMollieOnboarding = (): Promise<MollieKycStatus> =>
  api.post<MollieKycStatus>('/api/mollie/connect/dev/complete').then((r) => r.data);

/**
 * Reads the KYB-derived context the "Bank account" tab needs to guide the association through its
 * own Mollie onboarding (structure variant, beneficial owners, documents already provided, SIREN).
 * Read-only: it never contacts Mollie and never unlocks anything.
 */
export const getMollieOnboardingContext = (): Promise<MollieOnboardingContext> =>
  api.get<MollieOnboardingContext>('/api/mollie/connect/onboarding-context').then((r) => r.data);
