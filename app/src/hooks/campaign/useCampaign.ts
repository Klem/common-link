'use client';

import { useState, useEffect, useCallback } from 'react';
import { getCampaign, updateCampaign } from '@/lib/api/campaign';
import { useToastStore } from '@/stores/toastStore';
import type { CampaignDto, UpdateCampaignRequest } from '@/types/campaign';

/** Return type of {@link useCampaign}. */
export interface UseCampaignReturn {
  /** Full campaign data, or null while loading / not found. */
  campaign: CampaignDto | null;
  /** True while the initial fetch is in-flight. */
  isLoading: boolean;
  /** Error message key, or null. */
  error: string | null;
  /** True while an update request is in-flight. */
  isSaving: boolean;
  /** Re-fetches campaign data from the API. */
  fetchCampaign: () => Promise<void>;
  /**
   * Persists partial campaign changes to the API, updates local state, and shows a toast.
   * @param data - Fields to update (all optional).
   */
  updateCampaignInfo: (data: UpdateCampaignRequest) => Promise<void>;
  /** Directly overwrite local campaign state (for optimistic updates). */
  setCampaign: React.Dispatch<React.SetStateAction<CampaignDto | null>>;
}

/**
 * Hook managing a single campaign's detail view.
 *
 * Fetches the campaign on mount and exposes actions
 * that mutate remote state and keep local state in sync.
 *
 * @param campaignId - UUID of the campaign to load.
 */
export function useCampaign(campaignId: string): UseCampaignReturn {
  const [campaign, setCampaign] = useState<CampaignDto | null>(null);
  const [isLoading, setIsLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [isSaving, setIsSaving] = useState(false);
  const { addToast } = useToastStore();

  /** Fetches campaign data from the API. */
  const fetchCampaign = useCallback(async (): Promise<void> => {
    setIsLoading(true);
    setError(null);
    try {
      const data = await getCampaign(campaignId);
      setCampaign(data);
    } catch {
      setError('campaigns.editor.notFound');
    } finally {
      setIsLoading(false);
    }
  }, [campaignId]);

  /**
   * Persists partial campaign changes to the API.
   * Updates local state on success and shows a success toast.
   * Shows an error toast on failure.
   */
  const updateCampaignInfo = useCallback(
    async (data: UpdateCampaignRequest): Promise<void> => {
      setIsSaving(true);
      try {
        const updated = await updateCampaign(campaignId, data);
        setCampaign(updated);
        addToast('success', 'campaignUpdated');
      } catch {
        addToast('error', 'errors.serverError');
      } finally {
        setIsSaving(false);
      }
    },
    [campaignId, addToast],
  );

  useEffect(() => {
    fetchCampaign();
  }, [fetchCampaign]);

  return {
    campaign,
    isLoading,
    error,
    isSaving,
    fetchCampaign,
    updateCampaignInfo,
    setCampaign,
  };
}
