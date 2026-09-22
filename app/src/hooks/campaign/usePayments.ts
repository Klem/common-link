'use client';

import { useState, useEffect, useCallback } from 'react';
import {
  createPayment,
  confirmPayment,
  listPayments,
  getPaymentSummary,
} from '@/lib/api/payment';
import { BridgePaymentStatus, isPayoutInFlight } from '@/types/payment';
import type { CreatePayoutRequest, PayoutDto, PayoutSummaryDto } from '@/types/payment';

const PAGE_SIZE = 20;

/**
 * How often the list is refreshed while a bank transfer is still in flight.
 *
 * A SEPA transfer settles over hours or days and Bridge publishes no payout webhook, so the
 * backend reconciles by polling and the UI has to follow to reflect the change without a reload.
 */
const IN_FLIGHT_POLL_MS = 30_000;

/**
 * Cadence used in the seconds following the association's return from its bank.
 *
 * {@link IN_FLIGHT_POLL_MS} is right for a SEPA transfer that settles over hours, and far too slow
 * for the moment the association lands back here: on 2026-09-22 a transfer went CREA → ACTC → PDNG
 * in 24 seconds and settled 20 seconds later, entirely inside one 30-second window. So the page it
 * returns to shows the state as of the instant it loaded, still offering the authorisation link for
 * a transfer already on its way.
 */
const RETURN_POLL_MS = 2_000;

/**
 * How long that fast cadence lasts.
 *
 * Sized on how fast Bridge has actually reported rather than on generosity: on 2026-09-22 and
 * 2026-09-23 the first notification landed 1 to 3 seconds after the link was created, and `ACTC`
 * within 8. The reason not to be generous is that a payout the association *abandoned* is CREA
 * too — the two are indistinguishable from here — so every second of this window is a second
 * during which someone who pressed back is told to wait for a bank it never reached, with no way
 * to resume. Once it lapses the authorisation link comes back and the ordinary in-flight poll
 * takes over.
 */
const RETURN_POLL_WINDOW_MS = 20_000;

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
  /**
   * The payout the association has just come back from its bank for, while its fate is still
   * unknown — null otherwise.
   *
   * The UI must not offer it an authorisation link: it is still CREA only because Bridge has not
   * finished notifying, and the link it would re-open is one being consumed.
   */
  awaitingReturnPayoutId: string | null;
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
export function usePayments(campaignId: string, returningPayoutId?: string | null): UsePaymentsReturn {
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

  /*
   * Opens when the tab is entered on a return from the bank, and closes on its own timer rather
   * than on a clock read taken during a render: nothing guarantees a render happens once the
   * window lapses, so a comparison against `Date.now()` can stay true indefinitely.
   */
  const [returnWindowOpen, setReturnWindowOpen] = useState(returningPayoutId != null);

  useEffect(() => {
    if (!returningPayoutId) return;
    const timer = setTimeout(() => setReturnWindowOpen(false), RETURN_POLL_WINDOW_MS);
    return () => clearTimeout(timer);
  }, [returningPayoutId]);

  // Still CREA means Bridge has not reported the authorisation yet, not that nothing was
  // authorised: the association is back here seconds before the notification lands.
  const awaitingReturn =
    returnWindowOpen &&
    returningPayoutId != null &&
    payouts.some((p) => p.id === returningPayoutId && p.bridgeStatus === BridgePaymentStatus.CREA);

  useEffect(() => {
    if (!awaitingReturn && !hasInFlightPayout) return;
    const everyMs = awaitingReturn ? RETURN_POLL_MS : IN_FLIGHT_POLL_MS;
    const timer = setInterval(() => { fetchAll(true); }, everyMs);
    return () => clearInterval(timer);
  }, [awaitingReturn, hasInFlightPayout, fetchAll]);

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
    awaitingReturnPayoutId: awaitingReturn ? returningPayoutId : null,
  };
}
