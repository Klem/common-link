import api from '@/lib/api';
import type { MollieKycStatus, MollieOnboardingContext, MollieSelfCompletionAttestation } from '@/types/mollie-connect';

interface MollieAuthUrlDto {
  authUrl: string;
}

export const getMollieAuthUrl = (): Promise<MollieAuthUrlDto> =>
  api.get<MollieAuthUrlDto>('/api/mollie/connect/auth-url').then((r) => r.data);

/** Options of the status calls. */
export interface MollieStatusRequestOptions {
  /** Background call the user did not trigger: no global error toast (see `silentErrors` in `lib/api.ts`). */
  silent?: boolean;
}

/** Reads the association's Mollie connection status (`GET /api/mollie/connect/status`). */
export const getMollieKycStatus = (options?: MollieStatusRequestOptions): Promise<MollieKycStatus> =>
  api
    .get<MollieKycStatus>('/api/mollie/connect/status', { silentErrors: options?.silent === true })
    .then((r) => r.data);

/**
 * Forces the backend to re-read the onboarding status from Mollie (bypassing its 5-minute read
 * throttle) and returns the same DTO as [getMollieKycStatus]. The server limits Mollie calls to
 * one every 30 s per association and always answers 200 (stored status when rate-limited, skipped
 * or failed). The browser never talks to Mollie itself.
 *
 * @param options `silent` for automatic refreshes (no error toast).
 */
export const refreshMollieKycStatus = (options?: MollieStatusRequestOptions): Promise<MollieKycStatus> =>
  api
    .post<MollieKycStatus>('/api/mollie/connect/status/refresh', undefined, { silentErrors: options?.silent === true })
    .then((r) => r.data);

/** DEV/STAGING only — simulates a Mollie KYC validation (403 when disabled server-side). */
export const forceCompleteMollieOnboarding = (): Promise<MollieKycStatus> =>
  api.post<MollieKycStatus>('/api/mollie/connect/dev/complete').then((r) => r.data);

/**
 * Reads the KYB-derived context the "Bank account" tab needs to guide the association through its
 * own Mollie onboarding (structure variant, beneficial owners, documents already provided, SIREN,
 * self-completion attestation date). Read-only: it never contacts Mollie and never unlocks anything.
 */
export const getMollieOnboardingContext = (): Promise<MollieOnboardingContext> =>
  api.get<MollieOnboardingContext>('/api/mollie/connect/onboarding-context').then((r) => r.data);

/**
 * Records, in the association's compliance journal, that it filled its Mollie dossier itself.
 * Idempotent (a second call returns the first attestation). Unlocks nothing.
 */
export const attestMollieSelfCompletion = (): Promise<MollieSelfCompletionAttestation> =>
  api.post<MollieSelfCompletionAttestation>('/api/mollie/connect/self-completion-attestation').then((r) => r.data);
