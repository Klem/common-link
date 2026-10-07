'use client';

import { useState, useEffect } from 'react';
import { getCampaignReport } from '@/lib/api/donor';
import type { CampaignStoryDto } from '@/types/donor';

interface UseCampaignStoryReturn {
  story: CampaignStoryDto | null;
  isLoading: boolean;
  error: string | null;
}

/**
 * Fetches a campaign's full published story (rich-text `storyText`) whenever `campaignId`
 * changes. Pass `null` to skip fetching (e.g. the "voir le récit complet" modal is closed).
 *
 * Reuses `GET /api/donor/me/campaigns/{id}/report` -- the donor's own campaign report already
 * carries the published story in full, and already enforces that the donor has funded the
 * campaign, exactly the scope the impact gallery (this hook's only caller) operates in. No
 * dedicated endpoint needed.
 */
export function useCampaignStory(campaignId: string | null): UseCampaignStoryReturn {
  const [story, setStory] = useState<CampaignStoryDto | null>(null);
  const [isLoading, setIsLoading] = useState(campaignId !== null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (campaignId === null) {
      setStory(null);
      setIsLoading(false);
      return;
    }
    let cancelled = false;
    setIsLoading(true);
    setError(null);
    getCampaignReport(campaignId)
      .then((report) => {
        if (!cancelled) setStory(report.story);
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
  }, [campaignId]);

  return { story, isLoading, error };
}
