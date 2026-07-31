import { create } from 'zustand';
import { VerificationStatus } from '@/types/association';
import { BankSetupStatus } from '@/lib/bankSetupStatus';

/** Raw statuses pushed into the store by `AssociationStatusSync`. */
export interface AccStatusPayload {
  /** KYC verification lifecycle state of the association profile. */
  verificationStatus: VerificationStatus;
  /** Bank-setup state derived from the Mollie KYC DTO. */
  bankStatus: BankSetupStatus;
  /** Reason given by the back-office when `verificationStatus` is REJECTED. */
  rejectionReason: string | null;
  /** Deep link to the Mollie hosted onboarding wizard, null once onboarding is complete. */
  mollieDashboardUrl: string | null;
  /**
   * False while the Mollie KYC request is still in flight. `bankStatus` is meaningless until it
   * flips: `deriveBankSetupStatus` returns `NOT_CONNECTED` both for "never connected" and for
   * "not loaded yet", so consumers must not claim the bank account is missing before the request
   * settles. A failed request counts as resolved — the status is then genuinely unknown.
   */
  mollieResolved: boolean;
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
  /** True when Mollie KYC is complete and the association can receive payments (required for publishing). */
  bank: boolean;
  /** Called by `AssociationStatusSync` whenever the profile or the Mollie status changes. */
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
  mollieDashboardUrl: null,
  mollieResolved: false,
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
      // Sticky: a manual `refresh()` puts the Mollie hook back into loading, and the bank state
      // must not fall back to "unknown" once it has been resolved at least once.
      mollieResolved: state.mollieResolved || payload.mollieResolved,
      verified,
      bank,
      done: (verified ? 1 : 0) + (bank ? 1 : 0),
      total: 2,
    }));
  },
}));
