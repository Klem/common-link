'use client';

import { useState, useEffect } from 'react';
import { getDonorStats } from '@/lib/api/donor';
import type { DonorStatsDto } from '@/types/donor';

interface UseDonorStatsReturn {
  stats: DonorStatsDto | null;
  isLoading: boolean;
  error: string | null;
}

/**
 * Fetches the authenticated donor's headline figures once on mount.
 */
export function useDonorStats(): UseDonorStatsReturn {
  const [stats, setStats] = useState<DonorStatsDto | null>(null);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    let cancelled = false;
    getDonorStats()
      .then((data) => {
        if (!cancelled) setStats(data);
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

  return { stats, isLoading, error };
}
