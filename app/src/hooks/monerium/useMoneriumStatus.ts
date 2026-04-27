'use client';

import { useState, useEffect, useCallback } from 'react';
import { getMoneriumStatus } from '@/lib/api/monerium';

/** Return type of {@link useMoneriumStatus}. */
export interface UseMoneriumStatusReturn {
  /** True if the association has an active Monerium connection, false if not, null while loading. */
  connected: boolean | null;
  /** True while the status fetch is in-flight. */
  isLoading: boolean;
  /** i18n error key if the fetch failed, or null. */
  error: string | null;
  /**
   * Re-fetches the connection status from the API.
   * Call this after a successful OAuth2 callback to reflect the new connection state.
   */
  refresh: () => Promise<void>;
}

/**
 * Hook that loads and tracks the Monerium connection status for the authenticated association.
 *
 * Fetches the status once on mount. The `refresh` function allows callers to
 * invalidate and re-fetch after the OAuth2 popup completes successfully.
 *
 * @returns Connection status, loading/error state, and a `refresh` action.
 */
export function useMoneriumStatus(): UseMoneriumStatusReturn {
  const [connected, setConnected] = useState<boolean | null>(null);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const fetchStatus = useCallback(async (): Promise<void> => {
    setIsLoading(true);
    setError(null);
    try {
      const data = await getMoneriumStatus();
      setConnected(data.connected);
    } catch {
      setError('common.errors.serverError');
    } finally {
      setIsLoading(false);
    }
  }, []);

  useEffect(() => {
    fetchStatus();
  }, [fetchStatus]);

  return { connected, isLoading, error, refresh: fetchStatus };
}
