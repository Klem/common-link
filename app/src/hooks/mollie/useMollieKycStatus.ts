'use client';

import { useState, useEffect, useCallback, useRef } from 'react';
import { getMollieKycStatus, refreshMollieKycStatus } from '@/lib/api/mollie-connect';
import type { MollieKycStatus, MollieOnboardingStatus } from '@/types/mollie-connect';

/** Options of [UseMollieKycStatusReturn.refresh]. */
export interface MollieStatusRefreshOptions {
  /**
   * True for an automatic re-read the user did not trigger (background re-reads of the bank tab):
   * `isLoading` is not flipped (the tab keeps its current screen), and a failure shows no toast,
   * keeps the previous state and `error` untouched (decision R8).
   */
  silent?: boolean;
}

export interface UseMollieKycStatusReturn {
  connected: boolean | null;
  pending: boolean | null;
  broken: boolean | null;
  onboardingStatus: MollieOnboardingStatus | null;
  canReceivePayments: boolean | null;
  dashboardUrl: string | null;
  canForceComplete: boolean | null;
  /** NEEDS_DATA after an observed IN_REVIEW: Mollie asked for a complement. Null until loaded. */
  complementRequested: boolean | null;
  /** When the status was last successfully read from our API (any path), null before the first read. */
  lastUpdatedAt: Date | null;
  isLoading: boolean;
  error: string | null;
  /** Re-reads `GET /api/mollie/connect/status` (our database; the server may sync with Mollie if stale). */
  refresh: (options?: MollieStatusRefreshOptions) => Promise<void>;
  /**
   * Asks the server to re-read the status from Mollie (`POST /api/mollie/connect/status/refresh`,
   * rate-limited server-side) and applies the returned status, without the loading state.
   * Concurrent calls share the same request. A failure keeps the current state. `silent` (automatic
   * triggers: return to the tab, popup closed) suppresses the error toast; an explicit click on
   * "refresh" keeps it.
   */
  forceRefresh: (options?: MollieStatusRefreshOptions) => Promise<void>;
}

/**
 * Hook that loads and tracks the Mollie KYC connection status for the authenticated association.
 *
 * Fetches the status once on mount. Call `refresh` after the OAuth2 popup completes
 * to reflect the latest state without a page reload, or `forceRefresh` to have the server re-read
 * Mollie first.
 *
 * Also mounted dashboard-wide by `AssociationStatusSync`: this hook therefore owns no timer and no
 * listener — periodic and on-return refreshes live in the bank tab component that needs them.
 */
export function useMollieKycStatus(): UseMollieKycStatusReturn {
  const [connected, setConnected] = useState<boolean | null>(null);
  const [pending, setPending] = useState<boolean | null>(null);
  const [broken, setBroken] = useState<boolean | null>(null);
  const [onboardingStatus, setOnboardingStatus] = useState<MollieOnboardingStatus | null>(null);
  const [canReceivePayments, setCanReceivePayments] = useState<boolean | null>(null);
  const [dashboardUrl, setDashboardUrl] = useState<string | null>(null);
  const [canForceComplete, setCanForceComplete] = useState<boolean | null>(null);
  const [complementRequested, setComplementRequested] = useState<boolean | null>(null);
  const [lastUpdatedAt, setLastUpdatedAt] = useState<Date | null>(null);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const forceInFlight = useRef<Promise<void> | null>(null);

  /** Copies a status DTO into the hook state. */
  const apply = useCallback((data: MollieKycStatus): void => {
    setConnected(data.connected);
    setPending(data.pending);
    setBroken(data.broken);
    setOnboardingStatus(data.onboardingStatus);
    setCanReceivePayments(data.canReceivePayments);
    setDashboardUrl(data.dashboardUrl);
    setCanForceComplete(data.canForceComplete);
    setComplementRequested(data.complementRequested ?? false);
    setLastUpdatedAt(new Date());
  }, []);

  const fetchStatus = useCallback(async (options?: MollieStatusRefreshOptions): Promise<void> => {
    const silent = options?.silent === true;
    if (!silent) {
      setIsLoading(true);
      setError(null);
    }
    try {
      apply(await getMollieKycStatus({ silent }));
      if (silent) setError(null);
    } catch (e) {
      if (silent) console.debug('Background Mollie status re-read failed; keeping the previous state.', e);
      else setError('common.errors.serverError');
    } finally {
      if (!silent) setIsLoading(false);
    }
  }, [apply]);

  const forceRefresh = useCallback((options?: MollieStatusRefreshOptions): Promise<void> => {
    if (forceInFlight.current) return forceInFlight.current;
    const silent = options?.silent === true;
    const request = (async () => {
      try {
        apply(await refreshMollieKycStatus({ silent }));
      } catch (e) {
        // Keep the current state: the next re-read will catch up.
        if (silent) console.debug('Background Mollie status refresh failed; keeping the previous state.', e);
      } finally {
        forceInFlight.current = null;
      }
    })();
    forceInFlight.current = request;
    return request;
  }, [apply]);

  useEffect(() => {
    fetchStatus();
  }, [fetchStatus]);

  return {
    connected,
    pending,
    broken,
    onboardingStatus,
    canReceivePayments,
    dashboardUrl,
    canForceComplete,
    complementRequested,
    lastUpdatedAt,
    isLoading,
    error,
    refresh: fetchStatus,
    forceRefresh,
  };
}
