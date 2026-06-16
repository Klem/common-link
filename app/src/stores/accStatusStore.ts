import { create } from 'zustand';

interface AccStatusState {
  /** Number of completed account setup steps (0–total). */
  done: number;
  /** Total number of account setup steps (currently 2: KYC + bank). */
  total: number;
  /** Called by the association dashboard page when both status fetches resolve. */
  setAccStatus: (done: number, total: number) => void;
}

/**
 * Zustand store that tracks the association's account completion score.
 * Written by the association dashboard page; read by the Sidebar to render
 * the acc-mini pill without prop-drilling through DashboardShell.
 */
export const useAccStatusStore = create<AccStatusState>((set) => ({
  done: 0,
  total: 2,
  setAccStatus: (done, total) => set({ done, total }),
}));
