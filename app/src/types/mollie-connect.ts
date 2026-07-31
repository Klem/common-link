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
