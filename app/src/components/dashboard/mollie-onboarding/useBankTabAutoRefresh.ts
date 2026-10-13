'use client';

import { useEffect } from 'react';
import type { MollieStatusRefreshOptions } from '@/hooks/mollie/useMollieKycStatus';
import { STATUS_POLL_INTERVAL_MS } from './onboardingFlow';

/**
 * Keeps the bank tab's Mollie status fresh, only while the tab is mounted.
 *
 * - On return to the tab (`visibilitychange` to visible, window `focus`): `forceRefresh`, i.e.
 *   `POST /api/mollie/connect/status/refresh` (rate-limited server-side; concurrent calls share one
 *   request). The hosted onboarding is opened with `noopener`, so there is no window handle to watch
 *   (decision R6): coming back to the tab is the "window closed" signal.
 * - Every [STATUS_POLL_INTERVAL_MS]: silent re-read of `GET /api/mollie/connect/status` (our
 *   database), skipped while the document is hidden (the return to the tab refreshes anyway).
 *
 * Every automatic re-read is silent (decision R8): no error toast, previous state kept.
 *
 * Deliberately not part of `useMollieKycStatus`, which `AssociationStatusSync` also mounts on the
 * whole dashboard. Everything is removed on unmount.
 *
 * @param refresh the hook's stable `refresh`.
 * @param forceRefresh the hook's stable `forceRefresh`.
 */
export function useBankTabAutoRefresh(
  refresh: (options?: MollieStatusRefreshOptions) => Promise<void>,
  forceRefresh: (options?: MollieStatusRefreshOptions) => Promise<void>,
): void {
  useEffect(() => {
    const onVisibilityChange = () => {
      if (document.visibilityState === 'visible') void forceRefresh({ silent: true });
    };
    const onFocus = () => {
      void forceRefresh({ silent: true });
    };
    document.addEventListener('visibilitychange', onVisibilityChange);
    window.addEventListener('focus', onFocus);
    const timer = window.setInterval(() => {
      if (document.visibilityState !== 'hidden') void refresh({ silent: true });
    }, STATUS_POLL_INTERVAL_MS);

    return () => {
      document.removeEventListener('visibilitychange', onVisibilityChange);
      window.removeEventListener('focus', onFocus);
      window.clearInterval(timer);
    };
  }, [refresh, forceRefresh]);
}
