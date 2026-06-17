'use client';

import { useState, useEffect, useCallback, useRef } from 'react';
import { listDonors, getDonorDonations } from '@/lib/api/donor-campaign';
import { DonorSort } from '@/types/donor-campaign';
import type { CampaignDonorDto, DonationDto, Page } from '@/types/donor-campaign';

interface UseCampaignDonorsReturn {
  donorsPage: Page<CampaignDonorDto> | null;
  page: number;
  search: string;
  sort: string;
  isLoading: boolean;
  error: string | null;
  selectedDonor: CampaignDonorDto | null;
  donorDonations: DonationDto[];
  isDonorLoading: boolean;
  selectedDonation: DonationDto | null;
  setPage: (page: number) => void;
  setSearch: (search: string) => void;
  setSort: (sort: string) => void;
  selectDonor: (donor: CampaignDonorDto) => void;
  closeDonor: () => void;
  selectDonation: (donation: DonationDto) => void;
  closeDonation: () => void;
}

/**
 * Manages the donors tab state for a campaign:
 * paginated donor list with debounced search, donor detail with donation history.
 */
export function useCampaignDonors(campaignId: string): UseCampaignDonorsReturn {
  const [donorsPage, setDonorsPage] = useState<Page<CampaignDonorDto> | null>(null);
  const [page, setPage] = useState(0);
  const [search, setSearchRaw] = useState('');
  const [debouncedSearch, setDebouncedSearch] = useState('');
  const [sort, setSort] = useState<string>(DonorSort.AMOUNT);
  const [isLoading, setIsLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const [selectedDonor, setSelectedDonor] = useState<CampaignDonorDto | null>(null);
  const [donorDonations, setDonorDonations] = useState<DonationDto[]>([]);
  const [isDonorLoading, setIsDonorLoading] = useState(false);
  const [selectedDonation, setSelectedDonation] = useState<DonationDto | null>(null);

  const debounceRef = useRef<ReturnType<typeof setTimeout> | null>(null);

  const setSearch = useCallback((value: string) => {
    setSearchRaw(value);
    if (debounceRef.current) clearTimeout(debounceRef.current);
    debounceRef.current = setTimeout(() => {
      setDebouncedSearch(value);
      setPage(0);
    }, 300);
  }, []);

  useEffect(() => {
    let cancelled = false;
    setIsLoading(true);
    setError(null);
    listDonors(campaignId, page, 12, debouncedSearch || undefined, sort)
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
  }, [campaignId, page, debouncedSearch, sort]);

  useEffect(() => {
    if (!selectedDonor) {
      setDonorDonations([]);
      return;
    }
    let cancelled = false;
    setIsDonorLoading(true);
    getDonorDonations(campaignId, selectedDonor.donorId)
      .then((data) => {
        if (!cancelled) setDonorDonations(data.content);
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
  }, [campaignId, selectedDonor]);

  const selectDonor = useCallback((donor: CampaignDonorDto) => {
    setSelectedDonor(donor);
    setSelectedDonation(null);
  }, []);

  const closeDonor = useCallback(() => {
    setSelectedDonor(null);
    setSelectedDonation(null);
  }, []);

  const selectDonation = useCallback((donation: DonationDto) => {
    setSelectedDonation(donation);
  }, []);

  const closeDonation = useCallback(() => {
    setSelectedDonation(null);
  }, []);

  return {
    donorsPage,
    page,
    search,
    sort,
    isLoading,
    error,
    selectedDonor,
    donorDonations,
    isDonorLoading,
    selectedDonation,
    setPage,
    setSearch,
    setSort,
    selectDonor,
    closeDonor,
    selectDonation,
    closeDonation,
  };
}
