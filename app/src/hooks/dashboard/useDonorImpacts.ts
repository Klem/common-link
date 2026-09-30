'use client';

import { useState, useEffect } from 'react';
import { getDonorImpacts } from '@/lib/api/donor';
import type { DonorImpactDto } from '@/types/donor';

interface UseDonorImpactsReturn {
  impacts: DonorImpactDto[];
  isLoading: boolean;
  error: string | null;
}

/**
 * Fetches the donor's impact gallery once on mount -- one card per funded campaign.
 */
export function useDonorImpacts(): UseDonorImpactsReturn {
  const [impacts, setImpacts] = useState<DonorImpactDto[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    let cancelled = false;
    getDonorImpacts()
      .then((data) => {
        if (!cancelled) setImpacts(data);
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

  return { impacts, isLoading, error };
}
