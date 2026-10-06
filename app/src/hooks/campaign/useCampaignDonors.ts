'use client';

import { useState, useEffect, useCallback, useRef } from 'react';
import { listDonors, getDonorDonations } from '@/lib/api/donor-campaign';
import { DonorSort, SortDirection } from '@/types/donor-campaign';
import type { CampaignDonorDto, DonationDto, Page } from '@/types/donor-campaign';

const DONATIONS_PAGE_SIZE = 100;

interface UseCampaignDonorsReturn {
  donorsPage: Page<CampaignDonorDto> | null;
  page: number;
  search: string;
  sort: DonorSort;
  direction: SortDirection;
  isLoading: boolean;
  error: string | null;
  openDonorId: string | null;
  donorDonations: DonationDto[];
  isDonorLoading: boolean;
  setPage: (page: number) => void;
  setSearch: (search: string) => void;
  toggleSort: (sort: DonorSort) => void;
  toggleDonor: (donorId: string) => void;
}

/**
 * Loads every donation of [donorId] on [campaignId], walking the paginated endpoint until the last page,
 * so the expanded donor row never silently truncates its history.
 */
async function fetchAllDonorDonations(campaignId: string, donorId: string): Promise<DonationDto[]> {
  const all: DonationDto[] = [];
  for (let page = 0; ; page++) {
    const data = await getDonorDonations(campaignId, donorId, page, DONATIONS_PAGE_SIZE);
    all.push(...data.content);
    if (data.last || data.content.length === 0) return all;
  }
}

/**
 * Manages the donors tab state for a campaign:
 * paginated donor list with debounced search and server-side column sort (key + direction),
 * plus a single expanded donor row whose full donation history is loaded on open.
 */
export function useCampaignDonors(campaignId: string): UseCampaignDonorsReturn {
  const [donorsPage, setDonorsPage] = useState<Page<CampaignDonorDto> | null>(null);
  const [page, setPage] = useState(0);
  const [search, setSearchRaw] = useState('');
  const [debouncedSearch, setDebouncedSearch] = useState('');
  const [sort, setSort] = useState<DonorSort>(DonorSort.AMOUNT);
  const [direction, setDirection] = useState<SortDirection>(SortDirection.DESC);
  const [isLoading, setIsLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const [openDonorId, setOpenDonorId] = useState<string | null>(null);
  const [donorDonations, setDonorDonations] = useState<DonationDto[]>([]);
  const [isDonorLoading, setIsDonorLoading] = useState(false);

  const debounceRef = useRef<ReturnType<typeof setTimeout> | null>(null);

  const setSearch = useCallback((value: string) => {
    setSearchRaw(value);
    if (debounceRef.current) clearTimeout(debounceRef.current);
    debounceRef.current = setTimeout(() => {
      setDebouncedSearch(value);
      setPage(0);
    }, 300);
  }, []);

  /** Same column → flip direction; other column → sort it ascending. Always back to page 0. */
  const toggleSort = useCallback(
    (key: DonorSort) => {
      if (key === sort) {
        setDirection((d) => (d === SortDirection.ASC ? SortDirection.DESC : SortDirection.ASC));
      } else {
        setSort(key);
        setDirection(SortDirection.ASC);
      }
      setPage(0);
    },
    [sort],
  );

  /** Opens [donorId]'s row, closing any other; clicking the open row closes it. */
  const toggleDonor = useCallback((donorId: string) => {
    setOpenDonorId((current) => (current === donorId ? null : donorId));
  }, []);

  useEffect(() => {
    let cancelled = false;
    setIsLoading(true);
    setError(null);
    listDonors(campaignId, page, 12, debouncedSearch || undefined, sort, direction)
      .then((data) => {
        if (!cancelled) setDonorsPage(data);
      })
      .catch(() => {
        if (!cancelled) setError('error');
      })
      .finally(() => {
        if (!cancelled) setIsLoading(false);
      });
    return () => {
      cancelled = true;
    };
  }, [campaignId, page, debouncedSearch, sort, direction]);

  useEffect(() => {
    if (!openDonorId) {
      setDonorDonations([]);
      return;
    }
    let cancelled = false;
    setDonorDonations([]);
    setIsDonorLoading(true);
    fetchAllDonorDonations(campaignId, openDonorId)
      .then((data) => {
        if (!cancelled) setDonorDonations(data);
      })
      .catch(() => {
        if (!cancelled) setDonorDonations([]);
      })
      .finally(() => {
        if (!cancelled) setIsDonorLoading(false);
      });
    return () => {
      cancelled = true;
    };
  }, [campaignId, openDonorId]);

  return {
    donorsPage,
    page,
    search,
    sort,
    direction,
    isLoading,
    error,
    openDonorId,
    donorDonations,
    isDonorLoading,
    setPage,
    setSearch,
    toggleSort,
    toggleDonor,
  };
}
