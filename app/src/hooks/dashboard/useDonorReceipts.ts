'use client';

import { useState, useEffect } from 'react';
import { getDonorReceiptYears } from '@/lib/api/donor';
import type { DonorReceiptYearDto } from '@/types/donor';

interface UseDonorReceiptsReturn {
  years: DonorReceiptYearDto[];
  isLoading: boolean;
  error: string | null;
}

/**
 * Fetches the donor's annual fiscal recap summaries once on mount.
 */
export function useDonorReceipts(): UseDonorReceiptsReturn {
  const [years, setYears] = useState<DonorReceiptYearDto[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    let cancelled = false;
    getDonorReceiptYears()
      .then((data) => {
        if (!cancelled) setYears(data);
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
  }, []);

  return { years, isLoading, error };
}
