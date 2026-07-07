'use client';

import { useState, useEffect, useCallback } from 'react';
import {
  createPayment,
  confirmPayment,
  listPayments,
  getPaymentSummary,
} from '@/lib/api/payment';
import type { CreatePayoutRequest, PayoutDto, PayoutSummaryDto } from '@/types/payment';

const PAGE_SIZE = 20;

export interface UsePaymentsReturn {
  payouts: PayoutDto[];
  summary: PayoutSummaryDto | null;
  isLoading: boolean;
  isSaving: boolean;
  error: string | null;
  page: number;
  totalPages: number;
  setPage: (page: number) => void;
  /** Creates a PENDING payout then immediately confirms it. Returns the confirmed DTO. */
  submit: (req: CreatePayoutRequest) => Promise<PayoutDto>;
  refetch: () => Promise<void>;
}

/**
 * Manages payout state for a campaign's Payments tab.
 *
 * Fetches summary KPIs and paginated list on mount/page change.
 * `submit` creates a PENDING payout then confirms it in one user action.
 */
export function usePayments(campaignId: string): UsePaymentsReturn {
  const [payouts, setPayouts] = useState<PayoutDto[]>([]);
  const [summary, setSummary] = useState<PayoutSummaryDto | null>(null);
  const [isLoading, setIsLoading] = useState(false);
  const [isSaving, setIsSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [page, setPage] = useState(0);
  const [totalPages, setTotalPages] = useState(0);

  const fetchAll = useCallback(async (): Promise<void> => {
    setIsLoading(true);
    setError(null);
    try {
      const [pageResult, sum] = await Promise.all([
        listPayments(campaignId, page, PAGE_SIZE),
        getPaymentSummary(campaignId),
      ]);
      setPayouts(pageResult.content);
      setTotalPages(pageResult.totalPages);
      setSummary(sum);
    } catch {
      setError('common.errors.serverError');
    } finally {
      setIsLoading(false);
    }
  }, [campaignId, page]);

  useEffect(() => {
    fetchAll();
  }, [fetchAll]);

  const submit = useCallback(
    async (req: CreatePayoutRequest): Promise<PayoutDto> => {
      setIsSaving(true);
      try {
        const created = await createPayment(campaignId, req);
        const confirmed = await confirmPayment(campaignId, created.id);
        await fetchAll();
        return confirmed;
      } finally {
        setIsSaving(false);
      }
    },
    [campaignId, fetchAll],
  );

  return {
    payouts,
    summary,
    isLoading,
    isSaving,
    error,
    page,
    totalPages,
    setPage,
    submit,
    refetch: fetchAll,
  };
}
