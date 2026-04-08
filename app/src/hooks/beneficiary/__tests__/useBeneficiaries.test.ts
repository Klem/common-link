import { describe, it, expect, vi, beforeEach } from 'vitest';
import { renderHook, act, waitFor } from '@testing-library/react';
import { useBeneficiaries } from '../useBeneficiaries';

vi.mock('@/lib/api/beneficiary', () => ({
  getBeneficiaries: vi.fn(),
  addIban: vi.fn(),
  deleteIban: vi.fn(),
  deleteBeneficiary: vi.fn(),
}));

import {
  getBeneficiaries,
  addIban,
  deleteIban,
  deleteBeneficiary,
} from '@/lib/api/beneficiary';

const mockGetBeneficiaries = getBeneficiaries as ReturnType<typeof vi.fn>;
const mockAddIban = addIban as ReturnType<typeof vi.fn>;
const mockDeleteIban = deleteIban as ReturnType<typeof vi.fn>;
const mockDeleteBeneficiary = deleteBeneficiary as ReturnType<typeof vi.fn>;

const sampleIban = {
  id: 'iban-uuid-1',
  iban: 'DE89370400440532013000',
  status: 'FORMAT_VALID' as const,
  vopResult: null,
  vopSuggestedName: null,
  verifiedAt: null,
};

const sampleBeneficiary = {
  id: 'beneficiary-uuid-1',
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

describe('useBeneficiaries', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockGetBeneficiaries.mockResolvedValue([]);
  });

  // ── fetchBeneficiaries ────────────────────────────────────────────────────

  it('fetches beneficiaries on mount', async () => {
    mockGetBeneficiaries.mockResolvedValue([sampleBeneficiary]);

    const { result } = renderHook(() => useBeneficiaries());

    await waitFor(() => {
      expect(result.current.isLoading).toBe(false);
    });

    expect(mockGetBeneficiaries).toHaveBeenCalledTimes(1);
    expect(result.current.beneficiaries).toEqual([sampleBeneficiary]);
  });

  it('fetchBeneficiaries updates state with API response', async () => {
    mockGetBeneficiaries.mockResolvedValueOnce([]).mockResolvedValueOnce([sampleBeneficiary]);

    const { result } = renderHook(() => useBeneficiaries());

    await waitFor(() => expect(result.current.isLoading).toBe(false));

    await act(async () => {
      await result.current.fetchBeneficiaries();
    });

    expect(mockGetBeneficiaries).toHaveBeenCalledTimes(2);
    expect(result.current.beneficiaries).toEqual([sampleBeneficiary]);
  });

  it('sets error on fetchBeneficiaries failure', async () => {
    mockGetBeneficiaries
      .mockResolvedValueOnce([])       // initial mount
      .mockRejectedValueOnce(new Error('Network error'));

    const { result } = renderHook(() => useBeneficiaries());
    await waitFor(() => expect(result.current.isLoading).toBe(false));

    await act(async () => {
      await result.current.fetchBeneficiaries();
    });

    expect(result.current.error).toBe('common.errors.serverError');
  });

  // ── addBeneficiaryIban ────────────────────────────────────────────────────

  it('addBeneficiaryIban calls addIban API and refreshes list', async () => {
    mockGetBeneficiaries.mockResolvedValue([sampleBeneficiary]);
    mockAddIban.mockResolvedValue(sampleBeneficiary);

    const { result } = renderHook(() => useBeneficiaries());
    await waitFor(() => expect(result.current.isLoading).toBe(false));

    await act(async () => {
      await result.current.addBeneficiaryIban('beneficiary-uuid-1', 'DE89370400440532013000');
    });

    expect(mockAddIban).toHaveBeenCalledWith('beneficiary-uuid-1', {
      iban: 'DE89370400440532013000',
    });
    // fetchBeneficiaries is called after mutation (once on mount + once after add)
    expect(mockGetBeneficiaries).toHaveBeenCalledTimes(2);
  });

  // ── removeBeneficiaryIban ─────────────────────────────────────────────────

  it('removeBeneficiaryIban calls deleteIban API and refreshes list', async () => {
    mockGetBeneficiaries.mockResolvedValue([sampleBeneficiary]);
    mockDeleteIban.mockResolvedValue(undefined);

    const { result } = renderHook(() => useBeneficiaries());
    await waitFor(() => expect(result.current.isLoading).toBe(false));

    await act(async () => {
      await result.current.removeBeneficiaryIban('beneficiary-uuid-1', 'iban-uuid-1');
    });

    expect(mockDeleteIban).toHaveBeenCalledWith('beneficiary-uuid-1', 'iban-uuid-1');
    expect(mockGetBeneficiaries).toHaveBeenCalledTimes(2);
  });

  // ── removeBeneficiary ─────────────────────────────────────────────────────

  it('removeBeneficiary calls deleteBeneficiary API and refreshes list', async () => {
    mockGetBeneficiaries
      .mockResolvedValueOnce([sampleBeneficiary])
      .mockResolvedValueOnce([]);
    mockDeleteBeneficiary.mockResolvedValue(undefined);

    const { result } = renderHook(() => useBeneficiaries());
    await waitFor(() => expect(result.current.isLoading).toBe(false));

    await act(async () => {
      await result.current.removeBeneficiary('beneficiary-uuid-1');
    });

    expect(mockDeleteBeneficiary).toHaveBeenCalledWith('beneficiary-uuid-1');
    expect(result.current.beneficiaries).toEqual([]);
  });
});
