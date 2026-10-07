'use client';

import { useState, useEffect } from 'react';
import { getDonorRecommendations } from '@/lib/api/donor';
import type { DonorRecommendationDto } from '@/types/donor';

interface UseDonorRecommendationsReturn {
  recommendations: DonorRecommendationDto[];
  isLoading: boolean;
  error: string | null;
}

/** Fetches the donor's recommended projects once on mount (D8, option A). */
export function useDonorRecommendations(): UseDonorRecommendationsReturn {
  const [recommendations, setRecommendations] = useState<DonorRecommendationDto[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    let cancelled = false;
    getDonorRecommendations()
      .then((data) => {
        if (!cancelled) setRecommendations(data);
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

  return { recommendations, isLoading, error };
}
