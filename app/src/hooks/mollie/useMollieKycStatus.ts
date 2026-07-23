'use client';

import { useState, useEffect, useCallback } from 'react';
import { getMollieKycStatus } from '@/lib/api/mollie-connect';
import type { MollieOnboardingStatus } from '@/types/mollie-connect';

export interface UseMollieKycStatusReturn {
  connected: boolean | null;
  pending: boolean | null;
  broken: boolean | null;
  onboardingStatus: MollieOnboardingStatus | null;
  canReceivePayments: boolean | null;
  dashboardUrl: string | null;
  isLoading: boolean;
  error: string | null;
  refresh: () => Promise<void>;
}

/**
 * Hook that loads and tracks the Mollie KYC connection status for the authenticated association.
 *
 * Fetches the status once on mount. Call `refresh` after the OAuth2 popup completes
 * to reflect the latest state without a page reload.
 */
export function useMollieKycStatus(): UseMollieKycStatusReturn {
  const [connected, setConnected] = useState<boolean | null>(null);
  const [pending, setPending] = useState<boolean | null>(null);
  const [broken, setBroken] = useState<boolean | null>(null);
  const [onboardingStatus, setOnboardingStatus] = useState<MollieOnboardingStatus | null>(null);
  const [canReceivePayments, setCanReceivePayments] = useState<boolean | null>(null);
  const [dashboardUrl, setDashboardUrl] = useState<string | null>(null);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const fetchStatus = useCallback(async (): Promise<void> => {
    setIsLoading(true);
    setError(null);
    try {
      const data = await getMollieKycStatus();
      setConnected(data.connected);
      setPending(data.pending);
      setBroken(data.broken);
      setOnboardingStatus(data.onboardingStatus);
      setCanReceivePayments(data.canReceivePayments);
      setDashboardUrl(data.dashboardUrl);
    } catch {
      setError('common.errors.serverError');
    } finally {
      setIsLoading(false);
    }
  }, []);

  useEffect(() => {
    fetchStatus();
  }, [fetchStatus]);

  return { connected, pending, broken, onboardingStatus, canReceivePayments, dashboardUrl, isLoading, error, refresh: fetchStatus };
}
