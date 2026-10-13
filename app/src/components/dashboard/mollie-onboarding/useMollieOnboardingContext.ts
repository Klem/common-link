'use client';

import { useCallback, useEffect, useState } from 'react';
import { getMollieOnboardingContext } from '@/lib/api/mollie-connect';
import type { MollieOnboardingContext } from '@/types/mollie-connect';

export interface UseMollieOnboardingContextReturn {
  context: MollieOnboardingContext | null;
  isLoading: boolean;
  /** True when the last load failed; the flow then renders without KYB data. */
  error: boolean;
  reload: () => Promise<void>;
}

/**
 * Loads the KYB-derived onboarding context once, when the guided flow mounts.
 *
 * Deliberately co-located with the bank tab's flow and not added to `useMollieKycStatus`: that hook
 * is also mounted dashboard-wide by `AssociationStatusSync`, which must not fetch this context.
 * No timer, no refetch on its own — `reload` is only called on an explicit retry.
 *
 * @param enabled false to skip loading (the flow is not showing a screen that needs the context).
 */
export function useMollieOnboardingContext(enabled: boolean): UseMollieOnboardingContextReturn {
  const [context, setContext] = useState<MollieOnboardingContext | null>(null);
  const [isLoading, setIsLoading] = useState(false);
  const [error, setError] = useState(false);
  const [requested, setRequested] = useState(false);

  const reload = useCallback(async (): Promise<void> => {
    setIsLoading(true);
    setError(false);
    try {
      setContext(await getMollieOnboardingContext());
    } catch {
      setError(true);
    } finally {
      setIsLoading(false);
    }
  }, []);

  useEffect(() => {
    if (!enabled || requested) return;
    setRequested(true);
    void reload();
  }, [enabled, requested, reload]);

  return { context, isLoading, error, reload };
}
