'use client';

import { useState, useEffect, useCallback } from 'react';
import { getCampaigns, deleteCampaign } from '@/lib/api/campaign';
import type { CampaignSummaryDto } from '@/types/campaign';

/** Return type of {@link useCampaigns}. */
export interface UseCampaignsReturn {
  /** Current list of campaign summaries for the association. */
  campaigns: CampaignSummaryDto[];
  /** True while any API request is in-flight. */
  isLoading: boolean;
  /** Error message key, or null. */
  error: string | null;
  /** Re-fetches the full campaign list from the API. */
  fetchCampaigns: () => Promise<void>;
  /**
   * Deletes a campaign by ID and refreshes the list.
   * @param id - UUID of the campaign to delete.
   */
  removeCampaign: (id: string) => Promise<void>;
}

/**
 * Hook managing campaign list operations for an association.
 *
 * Fetches the campaign list on mount and exposes actions
 * that mutate the remote state and keep the local list in sync.
 */
export function useCampaigns(): UseCampaignsReturn {
  const [campaigns, setCampaigns] = useState<CampaignSummaryDto[]>([]);
  const [isLoading, setIsLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  /** Fetches the full campaign list from the API. */
  const fetchCampaigns = useCallback(async (): Promise<void> => {
    setIsLoading(true);
    setError(null);
    try {
      const data = await getCampaigns();
      setCampaigns(data);
    } catch {
      setError('common.errors.serverError');
    } finally {
      setIsLoading(false);
    }
  }, []);

  /** Deletes a campaign and refreshes the list. */
  const removeCampaign = useCallback(
    async (id: string): Promise<void> => {
      await deleteCampaign(id);
      await fetchCampaigns();
    },
    [fetchCampaigns],
  );

  useEffect(() => {
    fetchCampaigns();
  }, [fetchCampaigns]);

  return {
    campaigns,
    isLoading,
    error,
    fetchCampaigns,
    removeCampaign,
  };
}
