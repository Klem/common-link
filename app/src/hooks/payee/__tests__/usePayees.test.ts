import { describe, it, expect, vi, beforeEach } from 'vitest';
import { renderHook, act, waitFor } from '@testing-library/react';
import { usePayees } from '../usePayees';

vi.mock('@/lib/api/payee', () => ({
  getPayees: vi.fn(),
  addIban: vi.fn(),
  deleteIban: vi.fn(),
  deletePayee: vi.fn(),
}));

import {
  getPayees,
  addIban,
  deleteIban,
  deletePayee,
} from '@/lib/api/payee';

const mockGetPayees = getPayees as ReturnType<typeof vi.fn>;
const mockAddIban = addIban as ReturnType<typeof vi.fn>;
const mockDeleteIban = deleteIban as ReturnType<typeof vi.fn>;
const mockDeletePayee = deletePayee as ReturnType<typeof vi.fn>;

const sampleIban = {
  id: 'iban-uuid-1',
  iban: 'DE89370400440532013000',
  status: 'FORMAT_VALID' as const,
  vopResult: null,
  vopSuggestedName: null,
  verifiedAt: null,
};

const samplePayee = {
  id: 'payee-uuid-1',
  name: 'Les Restos du Coeur',
  identifier1: '775671356',
  identifier2: null,
  activityCode: null,
  category: null,
  city: 'Paris',
  postalCode: '75001',
  active: true,
  ibans: [sampleIban],
  createdAt: new Date().toISOString(),
};

describe('usePayees', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockGetPayees.mockResolvedValue([]);
  });

  // ── fetchPayees ───────────────────────────────────────────────────────────

  it('fetches payees on mount', async () => {
    mockGetPayees.mockResolvedValue([samplePayee]);

    const { result } = renderHook(() => usePayees());

    await waitFor(() => {
      expect(result.current.isLoading).toBe(false);
    });

    expect(mockGetPayees).toHaveBeenCalledTimes(1);
    expect(result.current.payees).toEqual([samplePayee]);
  });

  it('fetchPayees updates state with API response', async () => {
    mockGetPayees.mockResolvedValueOnce([]).mockResolvedValueOnce([samplePayee]);

    const { result } = renderHook(() => usePayees());

    await waitFor(() => expect(result.current.isLoading).toBe(false));

    await act(async () => {
      await result.current.fetchPayees();
    });

    expect(mockGetPayees).toHaveBeenCalledTimes(2);
    expect(result.current.payees).toEqual([samplePayee]);
  });

  it('sets error on fetchPayees failure', async () => {
    mockGetPayees
      .mockResolvedValueOnce([])       // initial mount
      .mockRejectedValueOnce(new Error('Network error'));

    const { result } = renderHook(() => usePayees());
    await waitFor(() => expect(result.current.isLoading).toBe(false));

    await act(async () => {
      await result.current.fetchPayees();
    });

    expect(result.current.error).toBe('common.errors.serverError');
  });

  // ── addPayeeIban ──────────────────────────────────────────────────────────

  it('addPayeeIban calls addIban API and refreshes list', async () => {
    mockGetPayees.mockResolvedValue([samplePayee]);
    mockAddIban.mockResolvedValue(samplePayee);

    const { result } = renderHook(() => usePayees());
    await waitFor(() => expect(result.current.isLoading).toBe(false));

    await act(async () => {
      await result.current.addPayeeIban('payee-uuid-1', 'DE89370400440532013000');
    });

    expect(mockAddIban).toHaveBeenCalledWith('payee-uuid-1', {
      iban: 'DE89370400440532013000',
    });
    // fetchPayees is called after mutation (once on mount + once after add)
    expect(mockGetPayees).toHaveBeenCalledTimes(2);
  });

  // ── removePayeeIban ───────────────────────────────────────────────────────

  it('removePayeeIban calls deleteIban API and refreshes list', async () => {
    mockGetPayees.mockResolvedValue([samplePayee]);
    mockDeleteIban.mockResolvedValue(undefined);

    const { result } = renderHook(() => usePayees());
    await waitFor(() => expect(result.current.isLoading).toBe(false));

    await act(async () => {
      await result.current.removePayeeIban('payee-uuid-1', 'iban-uuid-1');
    });

    expect(mockDeleteIban).toHaveBeenCalledWith('payee-uuid-1', 'iban-uuid-1');
    expect(mockGetPayees).toHaveBeenCalledTimes(2);
  });

  // ── removePayee ───────────────────────────────────────────────────────────

  it('removePayee calls deletePayee API and refreshes list', async () => {
    mockGetPayees
      .mockResolvedValueOnce([samplePayee])
      .mockResolvedValueOnce([]);
    mockDeletePayee.mockResolvedValue(undefined);

    const { result } = renderHook(() => usePayees());
    await waitFor(() => expect(result.current.isLoading).toBe(false));

    await act(async () => {
      await result.current.removePayee('payee-uuid-1');
    });

    expect(mockDeletePayee).toHaveBeenCalledWith('payee-uuid-1');
    expect(result.current.payees).toEqual([]);
  });
});
