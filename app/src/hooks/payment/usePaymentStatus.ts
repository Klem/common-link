'use client';

import { useState, useEffect, useCallback } from 'react';
import { getPaymentStatus } from '@/lib/api/paymentProvider';
import type { PaymentProviderId } from '@/types/paymentProvider';
import type { BankSetupStatus } from '@/lib/bankSetupStatus';

/** Options of [UsePaymentStatusReturn.refresh]. */
export interface PaymentStatusRefreshOptions {
  /** Automatic re-read the user did not trigger: `isLoading` is not flipped and a failure shows no toast. */
  silent?: boolean;
}

export interface UsePaymentStatusReturn {
  /** Provider of the association, null until loaded. */
  provider: PaymentProviderId | null;
  /** Bank-setup status computed by the provider, null until loaded. */
  status: BankSetupStatus | null;
  /** Link to the action the provider expects, null when none or not loaded. */
  actionUrl: string | null;
  /** Non-blocking provider warnings. */
  warnings: string[];
  isLoading: boolean;
  /** i18n key of the last error, null when the last read succeeded. */
  error: string | null;
  /** Re-reads `GET /api/association/payment/status`. */
  refresh: (options?: PaymentStatusRefreshOptions) => Promise<void>;
}

/**
 * Hook that loads the authenticated association's provider-neutral payment status.
 *
 * Fetches the status once on mount, with the same loading/error handling as `useMollieKycStatus`: `isLoading`
 * starts true and stays true until the first read settles; a failed read sets `error` to
 * `common.errors.serverError` and keeps the previous values. Owns no timer and no listener — it is mounted
 * dashboard-wide by `AssociationStatusSync`.
 */
export function usePaymentStatus(): UsePaymentStatusReturn {
  const [provider, setProvider] = useState<PaymentProviderId | null>(null);
  const [status, setStatus] = useState<BankSetupStatus | null>(null);
  const [actionUrl, setActionUrl] = useState<string | null>(null);
  const [warnings, setWarnings] = useState<string[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const fetchStatus = useCallback(async (options?: PaymentStatusRefreshOptions): Promise<void> => {
    const silent = options?.silent === true;
    if (!silent) {
      setIsLoading(true);
      setError(null);
    }
    try {
      const data = await getPaymentStatus({ silent });
      setProvider(data.provider);
      setStatus(data.status);
      setActionUrl(data.actionUrl);
      setWarnings(data.warnings ?? []);
      if (silent) setError(null);
    } catch (e) {
      if (silent) console.debug('Background payment status re-read failed; keeping the previous state.', e);
      else setError('common.errors.serverError');
    } finally {
      if (!silent) setIsLoading(false);
    }
  }, []);

  useEffect(() => {
    fetchStatus();
  }, [fetchStatus]);

  return { provider, status, actionUrl, warnings, isLoading, error, refresh: fetchStatus };
}
