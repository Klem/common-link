'use client';

import { useState, useEffect, useCallback } from 'react';
import {
  createPayment,
  confirmPayment,
  listPayments,
  getPaymentSummary,
} from '@/lib/api/payment';
import { isPayoutInFlight } from '@/types/payment';
import type { CreatePayoutRequest, PayoutDto, PayoutSummaryDto } from '@/types/payment';

const PAGE_SIZE = 20;

/**
 * How often the list is refreshed while a bank transfer is still in flight.
 *
 * A SEPA transfer settles over hours or days and Bridge publishes no payout webhook, so the
 * backend reconciles by polling and the UI has to follow to reflect the change without a reload.
 */
const IN_FLIGHT_POLL_MS = 30_000;

export interface UsePaymentsReturn {
  payouts: PayoutDto[];
  summary: PayoutSummaryDto | null;
  isLoading: boolean;
  isSaving: boolean;
  error: string | null;
  page: number;
  totalPages: number;
  setPage: (page: number) => void;
  /**
   * Creates a PENDING payout then confirms it, which orders the real SEPA transfer.
   *
   * The returned DTO is CONFIRMED as soon as the bank accepted the order — not once the
   * beneficiary is credited. Check `bridgeStatus` (or `isPayoutInFlight`) before telling the user
   * the money has arrived.
   */
  submit: (req: CreatePayoutRequest) => Promise<PayoutDto>;
  refetch: () => Promise<void>;
}

/**
 * Manages payout state for a campaign's Payments tab.
 *
 * Fetches summary KPIs and paginated list on mount/page change.
 * `submit` creates a PENDING payout then confirms it in one user action.
 *
 * While any payout's bank transfer is still in flight the list is refreshed silently every
 * {@link IN_FLIGHT_POLL_MS}, so settlement shows up without the user reloading the page.
 */
export function usePayments(campaignId: string): UsePaymentsReturn {
  const [payouts, setPayouts] = useState<PayoutDto[]>([]);
  const [summary, setSummary] = useState<PayoutSummaryDto | null>(null);
  const [isLoading, setIsLoading] = useState(false);
  const [isSaving, setIsSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [page, setPage] = useState(0);
  const [totalPages, setTotalPages] = useState(0);

  /**
   * Fetches the list and summary.
   *
   * @param silent When true, leaves `isLoading` untouched — used by the in-flight poll so the
   *   history does not flash a spinner every 30 seconds.
   */
  const fetchAll = useCallback(async (silent = false): Promise<void> => {
    if (!silent) setIsLoading(true);
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
      if (!silent) setIsLoading(false);
    }
  }, [campaignId, page]);

  useEffect(() => {
    fetchAll();
  }, [fetchAll]);

  const hasInFlightPayout = payouts.some(isPayoutInFlight);

  useEffect(() => {
    if (!hasInFlightPayout) return;
    const timer = setInterval(() => { fetchAll(true); }, IN_FLIGHT_POLL_MS);
    return () => clearInterval(timer);
  }, [hasInFlightPayout, fetchAll]);

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
