'use client';

import { useState, useEffect } from 'react';
import { getDonationJourney } from '@/lib/api/donor';
import type { DonorDonationJourneyDto } from '@/types/donor';

interface UseDonorDonationJourneyReturn {
  journey: DonorDonationJourneyDto | null;
  isLoading: boolean;
  error: string | null;
}

/**
 * Fetches the 4-step journey of a single donation whenever `donationId` changes.
 * Pass `null` to skip fetching (e.g. while the donor has no donations yet).
 */
export function useDonorDonationJourney(donationId: string | null): UseDonorDonationJourneyReturn {
  const [journey, setJourney] = useState<DonorDonationJourneyDto | null>(null);
  const [isLoading, setIsLoading] = useState(donationId !== null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (donationId === null) {
      setJourney(null);
      setIsLoading(false);
      return;
    }
    let cancelled = false;
    setIsLoading(true);
    setError(null);
    getDonationJourney(donationId)
      .then((data) => {
        if (!cancelled) setJourney(data);
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
  }, [donationId]);

  return { journey, isLoading, error };
}
