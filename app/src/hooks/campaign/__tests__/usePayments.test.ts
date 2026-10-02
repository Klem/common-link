import { describe, it, expect, vi, beforeEach } from 'vitest';
import { renderHook, act, waitFor } from '@testing-library/react';
import { usePayments } from '../usePayments';

vi.mock('@/lib/api/payment', () => ({
  createPayment: vi.fn(),
  confirmPayment: vi.fn(),
  listPayments: vi.fn(),
  getPaymentSummary: vi.fn(),
}));

import {
  createPayment,
  confirmPayment,
  listPayments,
  getPaymentSummary,
} from '@/lib/api/payment';

const mockCreate = createPayment as ReturnType<typeof vi.fn>;
const mockConfirm = confirmPayment as ReturnType<typeof vi.fn>;
const mockList = listPayments as ReturnType<typeof vi.fn>;
const mockSummary = getPaymentSummary as ReturnType<typeof vi.fn>;

const campaignId = 'campaign-uuid-1';

const sampleSummary = {
  confirmedAmount: 500,
  confirmedCount: 2,
  pendingAmount: 100,
  txTotal: 3,
  txConfirmed: 2,
  availableBalance: 900,
};

const samplePage = {
  content: [
    {
      id: 'payout-1',
      campaignId,
      payeeId: 'payee-1',
      payeeName: 'ACME Corp',
      payeeIbanId: 'iban-1',
      ibanValue: 'FR76 0000 0000 0000 0000',
      amount: 250,
      kind: 'EXPENSE',
      typeCode: '60-mat',
      label: 'Achat fournitures',
      status: 'CONFIRMED',
      createdAt: '2026-06-01T10:00:00Z',
      confirmedAt: '2026-06-01T10:05:00Z',
      onchainJobId: null,
    },
  ],
  totalElements: 1,
  totalPages: 1,
  number: 0,
  size: 20,
};

/** The page as it reads when the association has just returned from its bank. */
const awaitingReturnPage = {
  ...samplePage,
  content: [
    {
      ...samplePage.content[0],
      id: 'payout-9',
      status: 'PENDING',
      confirmedAt: null,
      bridgeStatus: 'CREA',
      bridgeLastErrorCode: null,
      bridgeCheckoutUrl: 'https://pay.bridgeapi.io/link/abc',
    },
  ],
};

beforeEach(() => {
  vi.clearAllMocks();
  mockList.mockResolvedValue(samplePage);
  mockSummary.mockResolvedValue(sampleSummary);
});

describe('usePayments', () => {
  it('fetches summary and list on mount', async () => {
    const { result } = renderHook(() => usePayments(campaignId));

    expect(result.current.isLoading).toBe(true);

    await waitFor(() => expect(result.current.isLoading).toBe(false));

    expect(result.current.payouts).toHaveLength(1);
    expect(result.current.summary?.txTotal).toBe(3);
    expect(result.current.summary?.availableBalance).toBe(900);
  });

  it('sets error on fetch failure', async () => {
    mockList.mockRejectedValueOnce(new Error('Network error'));
    mockSummary.mockRejectedValueOnce(new Error('Network error'));

    const { result } = renderHook(() => usePayments(campaignId));
    await waitFor(() => expect(result.current.isLoading).toBe(false));

    expect(result.current.error).toBe('common.errors.serverError');
  });

  it('submit calls create then confirm then refetches', async () => {
    const pendingPayout = { ...samplePage.content[0], id: 'payout-new', status: 'PENDING' };
    const confirmedPayout = { ...pendingPayout, status: 'CONFIRMED' };
    mockCreate.mockResolvedValue(pendingPayout);
    mockConfirm.mockResolvedValue(confirmedPayout);

    const { result } = renderHook(() => usePayments(campaignId));
    await waitFor(() => expect(result.current.isLoading).toBe(false));

    const req = {
      payeeId: 'payee-1',
      payeeIbanId: 'iban-1',
      amount: 250,
      kind: 'EXPENSE' as const,
      typeCode: '60-mat',
      label: 'Achat fournitures bureau',
    };

    let returned: unknown;
    await act(async () => {
      returned = await result.current.submit(req);
    });

    expect(mockCreate).toHaveBeenCalledWith(campaignId, req);
    expect(mockConfirm).toHaveBeenCalledWith(campaignId, 'payout-new');
    expect(returned).toEqual(confirmedPayout);
    // refetch called after submit
    expect(mockList).toHaveBeenCalledTimes(2);
  });

  it('submit sets isSaving during call', async () => {
    const pendingPayout = { ...samplePage.content[0], id: 'payout-new', status: 'PENDING' };
    mockCreate.mockResolvedValue(pendingPayout);
    mockConfirm.mockResolvedValue({ ...pendingPayout, status: 'CONFIRMED' });

    const { result } = renderHook(() => usePayments(campaignId));
    await waitFor(() => expect(result.current.isLoading).toBe(false));

    const req = {
      payeeId: 'payee-1',
      payeeIbanId: 'iban-1',
      amount: 100,
      kind: 'EXPENSE' as const,
      typeCode: '60-mat',
      label: 'Test label text',
    };

    act(() => {
      result.current.submit(req);
    });

    expect(result.current.isSaving).toBe(true);
    await waitFor(() => expect(result.current.isSaving).toBe(false));
  });

  it('polls fast for the payout the association just came back from its bank for', async () => {
    // The 30-second in-flight cadence is right for a SEPA settlement and far too slow here: on
    // 2026-09-22 a transfer went CREA -> ACTC -> PDNG in 24 seconds, entirely inside one window,
    // so the page the association returns to would keep showing the state it loaded with.
    vi.useFakeTimers();
    try {
      mockList.mockResolvedValue(awaitingReturnPage);
      const { result } = renderHook(() => usePayments(campaignId, 'payout-9'));
      await vi.waitFor(() => expect(result.current.isLoading).toBe(false));

      expect(result.current.awaitingReturnPayoutId).toBe('payout-9');
      const callsAfterMount = mockList.mock.calls.length;

      await act(async () => { await vi.advanceTimersByTimeAsync(6_000); });

      // Three two-second ticks, where the ordinary cadence would not have fired once.
      expect(mockList.mock.calls.length).toBeGreaterThanOrEqual(callsAfterMount + 3);
    } finally {
      vi.useRealTimers();
    }
  });

  it('stops watching the return once the fast window has lapsed', async () => {
    vi.useFakeTimers();
    try {
      mockList.mockResolvedValue(awaitingReturnPage);
      const { result } = renderHook(() => usePayments(campaignId, 'payout-9'));
      await vi.waitFor(() => expect(result.current.isLoading).toBe(false));

      await act(async () => { await vi.advanceTimersByTimeAsync(21_000); });

      // An abandoned payout is CREA too, and indistinguishable from here: keeping the window open
      // tells someone who pressed back to wait for a bank it never reached, with no way to resume.
      expect(result.current.awaitingReturnPayoutId).toBeNull();
    } finally {
      vi.useRealTimers();
    }
  });

  it('loads every page of the history, not just the first', async () => {
    // The journal searches, filters, counts and exports over the whole campaign, and numbers each
    // payout by rank within its day. A first page would silently under-count all four.
    mockList.mockImplementation((_id: string, page: number) =>
      Promise.resolve({
        ...samplePage,
        content: [{ ...samplePage.content[0], id: `payout-p${page}` }],
        totalElements: 3,
        totalPages: 3,
        number: page,
      }));

    const { result } = renderHook(() => usePayments(campaignId));
    await waitFor(() => expect(result.current.isLoading).toBe(false));

    expect(result.current.payouts.map((p) => p.id))
      .toEqual(['payout-p0', 'payout-p1', 'payout-p2']);
    expect(mockList).toHaveBeenCalledWith(campaignId, 0, 200);
    expect(mockList).toHaveBeenCalledWith(campaignId, 1, 200);
    expect(mockList).toHaveBeenCalledWith(campaignId, 2, 200);
  });

  it('asks for a single page when the history fits in one', async () => {
    const { result } = renderHook(() => usePayments(campaignId));
    await waitFor(() => expect(result.current.isLoading).toBe(false));

    expect(mockList).toHaveBeenCalledTimes(1);
  });
});
