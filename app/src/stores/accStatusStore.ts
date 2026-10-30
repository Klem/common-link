import { create } from 'zustand';
import { VerificationStatus } from '@/types/association';
import { BankSetupStatus } from '@/lib/bankSetupStatus';
import type { PaymentProviderId } from '@/types/paymentProvider';

/** Raw statuses pushed into the store by `AssociationStatusSync`. */
export interface AccStatusPayload {
  /** KYC verification lifecycle state of the association profile. */
  verificationStatus: VerificationStatus;
  /** Bank-setup state computed server-side by the association's payment provider. */
  bankStatus: BankSetupStatus;
  /** Reason given by the back-office when `verificationStatus` is REJECTED. */
  rejectionReason: string | null;
  /**
   * Link to the action the payment provider expects from the association (today the Mollie hosted
   * onboarding wizard), null when no action is pending.
   */
  bankActionUrl: string | null;
  /**
   * False while the payment-status request is still in flight. `bankStatus` is meaningless until it
   * flips: it defaults to `NOT_CONNECTED` both for "never connected" and for "not loaded yet", so
   * consumers must not claim the bank account is missing before the request settles. A failed
   * request counts as resolved — the status is then genuinely unknown.
   */
  bankResolved: boolean;
  /** Payment provider of the association, null until the payment status has been read. */
  paymentProvider: PaymentProviderId | null;
}

interface AccStatusState extends AccStatusPayload {
  /**
   * False until the association profile has actually been fetched. Consumers must not render
   * completion state before this flips, otherwise an already-onboarded association briefly sees
   * the default "nothing done" values on every page load.
   */
  hydrated: boolean;
  /** Number of completed account setup steps (0–total). Derived from the statuses. */
  done: number;
  /** Total number of account setup steps (currently 2: KYC + bank). */
  total: number;
  /** True when the association's KYC dossier is verified. Derived from `verificationStatus`. */
  verified: boolean;
  /** True when the provider reports the bank setup as complete (required for publishing). */
  bank: boolean;
  /** Called by `AssociationStatusSync` whenever the profile or the payment status changes. */
  setAccStatus: (payload: AccStatusPayload) => void;
}

/**
 * Zustand store that tracks the association's account completion state.
 *
 * Written by `AssociationStatusSync`; read by the Sidebar pill, the dashboard home card and the
 * publish modals without prop-drilling through DashboardShell. `verified` / `bank` / `done` are
 * kept as derived mirrors of the statuses so existing consumers stay untouched.
 */
export const useAccStatusStore = create<AccStatusState>((set) => ({
  verificationStatus: VerificationStatus.UNVERIFIED,
  bankStatus: BankSetupStatus.NOT_CONNECTED,
  rejectionReason: null,
  bankActionUrl: null,
  bankResolved: false,
  paymentProvider: null,
  hydrated: false,
  done: 0,
  total: 2,
  verified: false,
  bank: false,
  setAccStatus: (payload) => {
    const verified = payload.verificationStatus === VerificationStatus.VERIFIED;
    const bank = payload.bankStatus === BankSetupStatus.COMPLETED;
    set((state) => ({
      ...payload,
      hydrated: true,
      // Sticky: a manual `refresh()` puts the status hook back into loading, and the bank state
      // must not fall back to "unknown" once it has been resolved at least once.
      bankResolved: state.bankResolved || payload.bankResolved,
      verified,
      bank,
      done: (verified ? 1 : 0) + (bank ? 1 : 0),
      total: 2,
    }));
  },
}));
