import type { BankSetupStatus } from '@/lib/bankSetupStatus';

/** Payment service provider an association collects donations through (mirror of the backend `PaymentProviderId`). */
export const PaymentProviderId = {
  MOLLIE: 'MOLLIE',
  HELLOASSO: 'HELLOASSO',
} as const;
export type PaymentProviderId = typeof PaymentProviderId[keyof typeof PaymentProviderId];

/**
 * Provider-neutral payment status of the authenticated association (`GET /api/association/payment/status`),
 * computed server-side by the association's provider.
 */
export interface PaymentStatusResponse {
  /** Provider the association currently collects through. */
  provider: PaymentProviderId;
  /** Bank-setup state, same lifecycle the dashboard has always displayed. */
  status: BankSetupStatus;
  /** Link to the action the provider expects from the association (e.g. Mollie's onboarding wizard), if any. */
  actionUrl: string | null;
  /** Non-blocking provider warnings (codes), empty when none. */
  warnings: string[];
  /** HelloAsso-specific link details; null or absent for other providers. Typed with the HelloAsso journey. */
  helloasso?: null;
}

/** Whether an association can collect through a provider (mirror of the backend `ProviderReadinessStatus`). */
export const ProviderReadinessStatus = {
  NOT_CONNECTED: 'NOT_CONNECTED',
  BROKEN: 'BROKEN',
  PENDING: 'PENDING',
  READY: 'READY',
} as const;
export type ProviderReadinessStatus = typeof ProviderReadinessStatus[keyof typeof ProviderReadinessStatus];
