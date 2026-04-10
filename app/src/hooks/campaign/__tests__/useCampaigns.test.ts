import { describe, it, expect, vi, beforeEach } from 'vitest';
import { renderHook, act, waitFor } from '@testing-library/react';
import { useCampaigns } from '../useCampaigns';

vi.mock('@/lib/api/campaign', () => ({
  getCampaigns: vi.fn(),
  deleteCampaign: vi.fn(),
}));

import { getCampaigns, deleteCampaign } from '@/lib/api/campaign';

const mockGetCampaigns = getCampaigns as ReturnType<typeof vi.fn>;
const mockDeleteCampaign = deleteCampaign as ReturnType<typeof vi.fn>;

const sampleCampaign = {
  id: 'campaign-uuid-1',
  name: 'Hiver Solidaire 2025',
  emoji: '🌍',
  description: 'Aide hivernale',
  goal: 40000,
  raised: 5000,
  status: 'DRAFT' as const,
  startDate: null,
  endDate: null,
  milestoneCount: 2,
  createdAt: new Date().toISOString(),
};

describe('useCampaigns', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockGetCampaigns.mockResolvedValue([]);
  });

  // ── fetchCampaigns ────────────────────────────────────────────────────────

  it('fetches campaigns on mount', async () => {
    mockGetCampaigns.mockResolvedValue([sampleCampaign]);

    const { result } = renderHook(() => useCampaigns());

    await waitFor(() => {
      expect(result.current.isLoading).toBe(false);
    });

    expect(mockGetCampaigns).toHaveBeenCalledTimes(1);
    expect(result.current.campaigns).toEqual([sampleCampaign]);
  });

  it('isLoading is true during fetch then false after', async () => {
    let resolve: (value: typeof sampleCampaign[]) => void;
    mockGetCampaigns.mockReturnValue(
      new Promise<typeof sampleCampaign[]>((res) => { resolve = res; }),
    );

    const { result } = renderHook(() => useCampaigns());

    // Should be loading immediately
    expect(result.current.isLoading).toBe(true);

    await act(async () => {
      resolve!([sampleCampaign]);
    });

    await waitFor(() => expect(result.current.isLoading).toBe(false));
    expect(result.current.campaigns).toEqual([sampleCampaign]);
  });

  it('fetchCampaigns updates state with API response', async () => {
    mockGetCampaigns.mockResolvedValueOnce([]).mockResolvedValueOnce([sampleCampaign]);

    const { result } = renderHook(() => useCampaigns());

    await waitFor(() => expect(result.current.isLoading).toBe(false));

    await act(async () => {
      await result.current.fetchCampaigns();
    });

    expect(mockGetCampaigns).toHaveBeenCalledTimes(2);
    expect(result.current.campaigns).toEqual([sampleCampaign]);
  });

  it('sets error on fetchCampaigns failure', async () => {
    mockGetCampaigns
      .mockResolvedValueOnce([])
      .mockRejectedValueOnce(new Error('Network error'));

    const { result } = renderHook(() => useCampaigns());
    await waitFor(() => expect(result.current.isLoading).toBe(false));

    await act(async () => {
      await result.current.fetchCampaigns();
    });

    expect(result.current.error).toBe('common.errors.serverError');
  });

  // ── removeCampaign ────────────────────────────────────────────────────────

  it('removeCampaign calls deleteCampaign API and refreshes list', async () => {
    mockGetCampaigns
      .mockResolvedValueOnce([sampleCampaign])
      .mockResolvedValueOnce([]);
    mockDeleteCampaign.mockResolvedValue(undefined);

    const { result } = renderHook(() => useCampaigns());
    await waitFor(() => expect(result.current.isLoading).toBe(false));

    await act(async () => {
      await result.current.removeCampaign('campaign-uuid-1');
    });

    expect(mockDeleteCampaign).toHaveBeenCalledWith('campaign-uuid-1');
    // fetchCampaigns: once on mount + once after delete
    expect(mockGetCampaigns).toHaveBeenCalledTimes(2);
    expect(result.current.campaigns).toEqual([]);
  });
});
