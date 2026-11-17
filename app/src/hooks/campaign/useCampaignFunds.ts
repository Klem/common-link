'use client';

import { useCallback, useEffect, useRef, useState } from 'react';
import { confirmCashOutReceipt, getCampaignFunds, refreshCampaignFunds } from '@/lib/api/campaignFunds';
import type { CampaignFunds } from '@/types/campaignFunds';

export interface UseCampaignFundsReturn {
  /** Last funds received; kept while a refresh runs or after it fails. */
  funds: CampaignFunds | null;
  /** True until the first answer (success or failure). */
  isLoading: boolean;
  /** The last read failed (the previous funds, if any, are still shown). */
  error: boolean;
  /** A refresh request is running: the "Refresh" button must not be offered. */
  refreshing: boolean;
  /** From when a refresh will run a new HelloAsso pass; null when possible now. */
  nextRefreshAvailableAt: string | null;
  /**
   * Asks HelloAsso for news, then shows the funds. Called on tab opening and by "Refresh". Ignored while a request
   * runs or before `nextRefreshAvailableAt` (mirror of the server limit, which stays the only real protection).
   */
  refresh: () => Promise<void>;
  /** Re-reads the funds without asking HelloAsso (after a payment, a confirmation). */
  reload: () => Promise<void>;
  /**
   * "Transfer received" for a payout, then re-reads the funds and the payments. Rejects with the server error (read
   * its `code` with `fundsErrorCode`); nothing is toasted here.
   */
  confirmReceipt: (idCashOut: number) => Promise<void>;
}

/**
 * Funds of a campaign for the Payments tab ("Où en sont vos dons"): first read through a refresh request (opening the
 * tab asks HelloAsso for news, within the server's limit), then on demand.
 *
 * @param campaignId the campaign.
 * @param onPaymentsChanged re-reads the payments (summary, journal) after a receipt confirmation.
 */
export function useCampaignFunds(campaignId: string, onPaymentsChanged?: () => Promise<void> | void): UseCampaignFundsReturn {
  const [funds, setFunds] = useState<CampaignFunds | null>(null);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState(false);
  const [refreshing, setRefreshing] = useState(false);
  const inFlight = useRef(false);
  const nextRef = useRef<string | null>(null);

  const apply = useCallback((next: CampaignFunds) => {
    setFunds(next);
    nextRef.current = next.nextRefreshAvailableAt;
    setError(false);
  }, []);

  const refresh = useCallback(async () => {
    if (inFlight.current) return;
    if (nextRef.current && Date.parse(nextRef.current) > Date.now()) return;
    inFlight.current = true;
    setRefreshing(true);
    try {
      apply(await refreshCampaignFunds(campaignId));
    } catch {
      setError(true);
    } finally {
      inFlight.current = false;
      setRefreshing(false);
      setIsLoading(false);
    }
  }, [campaignId, apply]);

  const reload = useCallback(async () => {
    try {
      apply(await getCampaignFunds(campaignId));
    } catch {
      setError(true);
    } finally {
      setIsLoading(false);
    }
  }, [campaignId, apply]);

  const confirmReceipt = useCallback(async (idCashOut: number) => {
    await confirmCashOutReceipt(idCashOut);
    await Promise.all([reload(), onPaymentsChanged?.()]);
  }, [reload, onPaymentsChanged]);

  useEffect(() => {
    nextRef.current = null;
    setIsLoading(true);
    void refresh();
  }, [refresh]);

  return {
    funds,
    isLoading,
    error,
    refreshing,
    nextRefreshAvailableAt: funds?.nextRefreshAvailableAt ?? null,
    refresh,
    reload,
    confirmReceipt,
  };
}
