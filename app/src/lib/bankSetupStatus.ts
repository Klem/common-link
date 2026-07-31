import { MollieOnboardingStatus } from '@/types/mollie-connect';

/**
 * Bank-setup lifecycle as displayed to an association, derived from the Mollie KYC status DTO.
 *
 * `MollieOnboardingStatus` only covers the three states Mollie itself reports once a merchant
 * account exists. Two more states are meaningful to the association and come from other fields
 * of the DTO: never connected at all, and a connection that broke (revoked token, deleted org).
 */
export const BankSetupStatus = {
  /** No Mollie account linked yet (or Mollie status not loaded). */
  NOT_CONNECTED: 'NOT_CONNECTED',
  /** Linked, but Mollie requires more onboarding data before review. */
  NEEDS_DATA: 'NEEDS_DATA',
  /** Linked, data submitted, Mollie is reviewing. */
  IN_REVIEW: 'IN_REVIEW',
  /** KYC complete — the association can receive payments. */
  COMPLETED: 'COMPLETED',
  /** The link exists but is unusable (revoked authorisation, unreachable merchant). */
  BROKEN: 'BROKEN',
} as const;
export type BankSetupStatus = typeof BankSetupStatus[keyof typeof BankSetupStatus];

/** Subset of `MollieKycStatus` needed to derive the display status. Fields are nullable while loading. */
export interface BankSetupSource {
  connected: boolean | null;
  broken: boolean | null;
  onboardingStatus: MollieOnboardingStatus | null;
}

/**
 * Collapses the Mollie KYC DTO into a single displayable bank-setup status.
 *
 * Evaluation order matters: a broken link wins over everything (it needs user action even if a
 * stale `onboardingStatus` says COMPLETED), then absence of a connection, then Mollie's own status.
 *
 * @param source the `connected` / `broken` / `onboardingStatus` triple from `useMollieKycStatus`
 * @return the status to render; `NOT_CONNECTED` while the Mollie status is still loading
 */
export function deriveBankSetupStatus(source: BankSetupSource): BankSetupStatus {
  if (source.broken === true) return BankSetupStatus.BROKEN;
  if (source.connected !== true || source.onboardingStatus === null) return BankSetupStatus.NOT_CONNECTED;
  switch (source.onboardingStatus) {
    case MollieOnboardingStatus.NEEDS_DATA:
      return BankSetupStatus.NEEDS_DATA;
    case MollieOnboardingStatus.IN_REVIEW:
      return BankSetupStatus.IN_REVIEW;
    case MollieOnboardingStatus.COMPLETED:
      return BankSetupStatus.COMPLETED;
  }
}
