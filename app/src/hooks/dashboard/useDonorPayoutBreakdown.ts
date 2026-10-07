'use client';

import { useState, useEffect } from 'react';
import { getPayoutBreakdown } from '@/lib/api/donor';
import type { PayoutFundingBreakdownDto } from '@/types/donor';

interface UseDonorPayoutBreakdownReturn {
  breakdown: PayoutFundingBreakdownDto | null;
  isLoading: boolean;
  error: string | null;
}

/**
 * Fetches which donations funded one payout ("Voir la répartition") whenever `payoutId` changes.
 * Pass `null` for either id to skip fetching (e.g. while the modal is closed).
 */
export function useDonorPayoutBreakdown(
  campaignId: string | null,
  payoutId: string | null,
): UseDonorPayoutBreakdownReturn {
  const [breakdown, setBreakdown] = useState<PayoutFundingBreakdownDto | null>(null);
  const [isLoading, setIsLoading] = useState(campaignId !== null && payoutId !== null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (campaignId === null || payoutId === null) {
      setBreakdown(null);
      setIsLoading(false);
      return;
    }
    let cancelled = false;
    setIsLoading(true);
    setError(null);
    getPayoutBreakdown(campaignId, payoutId)
      .then((data) => {
        if (!cancelled) setBreakdown(data);
      })
      .catch(() => {
        if (!cancelled) setError('common.errors.serverError');
      })
      .finally(() => {
        if (!cancelled) setIsLoading(false);
      });
    return () => {
      cancelled = true;
    };
  }, [campaignId, payoutId]);

  return { breakdown, isLoading, error };
}
