import { useState, useEffect, useCallback } from 'react';
import { getReporting } from '@/lib/api/reporting';
import type { BudgetVariance } from '@/types/reporting';

interface UseReportingResult {
  data: BudgetVariance | null;
  isLoading: boolean;
  error: string | null;
  refetch: () => void;
}

/**
 * Fetches the budget variance report for a campaign.
 * Returns loading/error states and a refetch function.
 */
export function useReporting(campaignId: string): UseReportingResult {
  const [data, setData] = useState<BudgetVariance | null>(null);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const fetch = useCallback(async () => {
    setIsLoading(true);
    setError(null);
    try {
      const result = await getReporting(campaignId);
      setData(result);
    } catch {
      setError('reporting.tab.error');
    } finally {
      setIsLoading(false);
    }
  }, [campaignId]);

  useEffect(() => {
    fetch();
  }, [fetch]);

  return { data, isLoading, error, refetch: fetch };
}
