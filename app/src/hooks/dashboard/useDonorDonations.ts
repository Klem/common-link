'use client';

import { useState, useEffect, useCallback } from 'react';
import { getDonorDonations } from '@/lib/api/donor';
import type { DonorDonationsPage } from '@/types/donor';

interface UseDonorDonationsReturn {
  /** Current page of the donor's confirmed donations, or null while loading. */
  donationsPage: DonorDonationsPage | null;
  page: number;
  associationId: string | null;
  year: number | null;
  isLoading: boolean;
  error: string | null;
  setPage: (page: number) => void;
  setAssociationId: (associationId: string | null) => void;
  setYear: (year: number | null) => void;
}

/**
 * Manages the paginated donation history of the authenticated donor.
 * Resets to page 0 whenever a filter (association or year) changes.
 */
export function useDonorDonations(size = 20): UseDonorDonationsReturn {
  const [donationsPage, setDonationsPage] = useState<DonorDonationsPage | null>(null);
  const [page, setPage] = useState(0);
  const [associationId, setAssociationIdRaw] = useState<string | null>(null);
  const [year, setYearRaw] = useState<number | null>(null);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const setAssociationId = useCallback((value: string | null) => {
    setAssociationIdRaw(value);
    setPage(0);
  }, []);

  const setYear = useCallback((value: number | null) => {
    setYearRaw(value);
    setPage(0);
  }, []);

  useEffect(() => {
    let cancelled = false;
    setIsLoading(true);
    setError(null);
    getDonorDonations({
      page,
      size,
      associationId: associationId ?? undefined,
      year: year ?? undefined,
    })
      .then((data) => {
        if (!cancelled) setDonationsPage(data);
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
  }, [page, size, associationId, year]);

  return { donationsPage, page, associationId, year, isLoading, error, setPage, setAssociationId, setYear };
}
