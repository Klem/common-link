'use client';

import { useState, useEffect } from 'react';
import { getDashboard } from '@/lib/api/association';
import type { DashboardStats } from '@/types/association';

interface UseAssociationDashboardReturn {
  stats: DashboardStats | null;
  isLoading: boolean;
  error: string | null;
}

/**
 * Fetches association dashboard statistics once on mount.
 * Follows the vanilla-React pattern (useState + useEffect + cancelled flag)
 * used throughout this codebase — no React Query.
 *
 * @returns Dashboard stats, loading flag, and i18n error key on failure.
 */
export function useAssociationDashboard(): UseAssociationDashboardReturn {
  const [stats, setStats] = useState<DashboardStats | null>(null);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    let cancelled = false;
    getDashboard()
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
