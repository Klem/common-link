import { create } from 'zustand';

interface AccStatusState {
  /** Number of completed account setup steps (0–total). */
  done: number;
  /** Total number of account setup steps (currently 2: KYC + bank). */
  total: number;
  /** True when the association's KYC dossier is verified. */
  verified: boolean;
  /** True when a Monerium bank account is connected (required for publishing). */
  bank: boolean;
  /** Called by the association dashboard page when both status fetches resolve. */
  setAccStatus: (done: number, total: number, verified: boolean, bank: boolean) => void;
}

/**
 * Zustand store that tracks the association's account completion score.
 * Written by the association dashboard page; read by the Sidebar and publish
 * modals without prop-drilling through DashboardShell.
 */
export const useAccStatusStore = create<AccStatusState>((set) => ({
  done: 0,
  total: 2,
  verified: false,
  bank: false,
  setAccStatus: (done, total, verified, bank) => set({ done, total, verified, bank }),
}));
