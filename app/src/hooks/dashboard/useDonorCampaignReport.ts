'use client';

import { useState, useEffect } from 'react';
import { getCampaignReport } from '@/lib/api/donor';
import type { DonorCampaignReportDto } from '@/types/donor';

interface UseDonorCampaignReportReturn {
  report: DonorCampaignReportDto | null;
  isLoading: boolean;
  error: string | null;
}

/**
 * Fetches the donor-facing "bilan de campagne" for one campaign.
 * Requires the donor to have at least one confirmed donation on the campaign (403 otherwise).
 */
export function useDonorCampaignReport(campaignId: string): UseDonorCampaignReportReturn {
  const [report, setReport] = useState<DonorCampaignReportDto | null>(null);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    let cancelled = false;
    setIsLoading(true);
    setError(null);
    getCampaignReport(campaignId)
      .then((data) => {
        if (!cancelled) setReport(data);
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

  return { report, isLoading, error };
}
