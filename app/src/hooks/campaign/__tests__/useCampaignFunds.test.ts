import { describe, it, expect, vi, beforeEach } from 'vitest';
import { renderHook, act, waitFor } from '@testing-library/react';
import { useCampaignFunds } from '../useCampaignFunds';
import type { CampaignFunds } from '@/types/campaignFunds';

vi.mock('@/lib/api/campaignFunds', () => ({
  getCampaignFunds: vi.fn(),
  refreshCampaignFunds: vi.fn(),
  confirmCashOutReceipt: vi.fn(),
}));

import { confirmCashOutReceipt, getCampaignFunds, refreshCampaignFunds } from '@/lib/api/campaignFunds';

const mockGet = getCampaignFunds as ReturnType<typeof vi.fn>;
const mockRefresh = refreshCampaignFunds as ReturnType<typeof vi.fn>;
const mockConfirm = confirmCashOutReceipt as ReturnType<typeof vi.fn>;

const funds = (over: Partial<CampaignFunds> = {}): CampaignFunds => ({
  provider: 'HELLOASSO', raised: 100, atProvider: 40, payoutPreparing: 0, inTransit: 30, available: 30, withdrawn: 0,
  paidOut: 10, pendingPayouts: 0, disbursable: 20, cashOuts: [], manualReleases: [], lastSyncAt: null,
  syncError: false, syncErrorCode: null, nextRefreshAvailableAt: null, ...over,
});

describe('useCampaignFunds', () => {
  beforeEach(() => vi.clearAllMocks());

  it('opens with a refresh request and exposes the funds', async () => {
    mockRefresh.mockResolvedValue(funds());
    const { result } = renderHook(() => useCampaignFunds('c1'));

    expect(result.current.isLoading).toBe(true);
    await waitFor(() => expect(result.current.isLoading).toBe(false));
    expect(mockRefresh).toHaveBeenCalledWith('c1');
    expect(result.current.funds?.disbursable).toBe(20);
    expect(result.current.error).toBe(false);
  });

  it('reports an error and keeps the last funds', async () => {
    mockRefresh.mockResolvedValueOnce(funds()).mockRejectedValueOnce(new Error('down'));
    const { result } = renderHook(() => useCampaignFunds('c1'));
    await waitFor(() => expect(result.current.funds).not.toBeNull());

    await act(() => result.current.refresh());

    expect(result.current.error).toBe(true);
    expect(result.current.funds?.disbursable).toBe(20);
  });

  it('a second refresh while one runs sends a single request', async () => {
    mockRefresh.mockResolvedValueOnce(funds());
    const { result } = renderHook(() => useCampaignFunds('c1'));
    await waitFor(() => expect(result.current.isLoading).toBe(false));

    let resolve!: (f: CampaignFunds) => void;
    mockRefresh.mockReturnValueOnce(new Promise<CampaignFunds>((r) => { resolve = r; }));
    let first!: Promise<void>;
    act(() => { first = result.current.refresh(); });
    await waitFor(() => expect(result.current.refreshing).toBe(true));
    await act(() => result.current.refresh());
    await act(async () => { resolve(funds()); await first; });

    expect(mockRefresh).toHaveBeenCalledTimes(2); // opening + one click
    expect(result.current.refreshing).toBe(false);
  });

  it('no request before the next refresh the server allows', async () => {
    const next = new Date(Date.now() + 10 * 60_000).toISOString();
    mockRefresh.mockResolvedValue(funds({ nextRefreshAvailableAt: next }));
    const { result } = renderHook(() => useCampaignFunds('c1'));
    await waitFor(() => expect(result.current.nextRefreshAvailableAt).toBe(next));

    await act(() => result.current.refresh());

    expect(mockRefresh).toHaveBeenCalledTimes(1);
  });

  it('a receipt confirmation re-reads the funds and the payments', async () => {
    mockRefresh.mockResolvedValue(funds());
    mockConfirm.mockResolvedValue({ idCashOut: 7, netTotal: null, donations: [] });
    mockGet.mockResolvedValue(funds({ available: 60, inTransit: 0, disbursable: 50 }));
    const onPayments = vi.fn().mockResolvedValue(undefined);
    const { result } = renderHook(() => useCampaignFunds('c1', onPayments));
    await waitFor(() => expect(result.current.isLoading).toBe(false));

    await act(() => result.current.confirmReceipt(7));

    expect(mockConfirm).toHaveBeenCalledWith(7);
    expect(onPayments).toHaveBeenCalledTimes(1);
    expect(result.current.funds?.disbursable).toBe(50);
  });

  it('a coded 409 on confirmation is rejected to the caller, nothing re-read', async () => {
    mockRefresh.mockResolvedValue(funds());
    const refusal = Object.assign(new Error('409'), { isAxiosError: true, response: { status: 409, data: { code: 'CASH_OUT_NOT_CASHED_OUT' } } });
    mockConfirm.mockRejectedValue(refusal);
    const onPayments = vi.fn();
    const { result } = renderHook(() => useCampaignFunds('c1', onPayments));
    await waitFor(() => expect(result.current.isLoading).toBe(false));

    await expect(result.current.confirmReceipt(7)).rejects.toBe(refusal);
    expect(mockGet).not.toHaveBeenCalled();
    expect(onPayments).not.toHaveBeenCalled();
  });
});
