'use client';

import { useState, useEffect } from 'react';
import { getDonorAssociations } from '@/lib/api/donor';
import type { DonorAssociationDto } from '@/types/donor';

interface UseDonorAssociationsReturn {
  associations: DonorAssociationDto[];
  isLoading: boolean;
  error: string | null;
}

/**
 * Fetches the associations supported by the authenticated donor once on mount.
 */
export function useDonorAssociations(): UseDonorAssociationsReturn {
  const [associations, setAssociations] = useState<DonorAssociationDto[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    let cancelled = false;
    getDonorAssociations()
      .then((data) => {
        if (!cancelled) setAssociations(data);
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

  return { associations, isLoading, error };
}
