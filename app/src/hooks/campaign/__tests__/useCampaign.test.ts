import { describe, it, expect, vi, beforeEach } from 'vitest';
import { renderHook, act, waitFor } from '@testing-library/react';
import { useCampaign } from '../useCampaign';

vi.mock('@/lib/api/campaign', () => ({
  getCampaign: vi.fn(),
  updateCampaign: vi.fn(),
}));

vi.mock('@/stores/toastStore', () => ({
  useToastStore: () => ({ addToast: vi.fn() }),
}));

import { getCampaign, updateCampaign } from '@/lib/api/campaign';

const mockGetCampaign = getCampaign as ReturnType<typeof vi.fn>;
const mockUpdateCampaign = updateCampaign as ReturnType<typeof vi.fn>;

const campaignId = 'campaign-uuid-1';

const sampleCampaign = {
  id: campaignId,
  name: 'Hiver Solidaire 2025',
  emoji: '🌍',
  description: 'Aide hivernale',
  goal: 40000,
  raised: 5000,
  status: 'DRAFT' as const,
  startDate: null,
  endDate: null,
  contractAddress: null,
  budgetSections: [],
  milestones: [],
  createdAt: new Date().toISOString(),
  updatedAt: new Date().toISOString(),
};

describe('useCampaign', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockGetCampaign.mockResolvedValue(sampleCampaign);
  });

  // ── fetchCampaign ─────────────────────────────────────────────────────────

  it('fetches campaign on mount', async () => {
    const { result } = renderHook(() => useCampaign(campaignId));

    await waitFor(() => {
      expect(result.current.isLoading).toBe(false);
    });

    expect(mockGetCampaign).toHaveBeenCalledWith(campaignId);
    expect(result.current.campaign).toEqual(sampleCampaign);
  });

  it('sets error key when fetchCampaign fails', async () => {
    mockGetCampaign
      .mockResolvedValueOnce(sampleCampaign)
      .mockRejectedValueOnce(new Error('Not found'));

    const { result } = renderHook(() => useCampaign(campaignId));
    await waitFor(() => expect(result.current.isLoading).toBe(false));

    await act(async () => {
      await result.current.fetchCampaign();
    });

    expect(result.current.error).toBe('campaigns.editor.notFound');
  });

  it('campaign is null while loading', async () => {
    let resolve: (value: typeof sampleCampaign) => void;
    mockGetCampaign.mockReturnValue(
      new Promise<typeof sampleCampaign>((res) => { resolve = res; }),
    );

    const { result } = renderHook(() => useCampaign(campaignId));

    expect(result.current.campaign).toBeNull();
    expect(result.current.isLoading).toBe(true);

    await act(async () => {
      resolve!(sampleCampaign);
    });

    await waitFor(() => expect(result.current.isLoading).toBe(false));
    expect(result.current.campaign).toEqual(sampleCampaign);
  });

  // ── updateCampaignInfo ────────────────────────────────────────────────────

  it('updateCampaignInfo calls API and updates local state', async () => {
    const updated = { ...sampleCampaign, name: 'Nouveau Nom' };
    mockUpdateCampaign.mockResolvedValue(updated);

    const { result } = renderHook(() => useCampaign(campaignId));
    await waitFor(() => expect(result.current.isLoading).toBe(false));

    await act(async () => {
      await result.current.updateCampaignInfo({ name: 'Nouveau Nom' });
    });

    expect(mockUpdateCampaign).toHaveBeenCalledWith(campaignId, { name: 'Nouveau Nom' });
    expect(result.current.campaign?.name).toBe('Nouveau Nom');
    expect(result.current.isSaving).toBe(false);
  });

  it('isSaving is true during updateCampaignInfo then false after', async () => {
    let resolve: (value: typeof sampleCampaign) => void;
    mockUpdateCampaign.mockReturnValue(
      new Promise<typeof sampleCampaign>((res) => { resolve = res; }),
    );

    const { result } = renderHook(() => useCampaign(campaignId));
    await waitFor(() => expect(result.current.isLoading).toBe(false));

    act(() => {
      result.current.updateCampaignInfo({ name: 'X' });
    });

    expect(result.current.isSaving).toBe(true);

    await act(async () => {
      resolve!(sampleCampaign);
    });

    await waitFor(() => expect(result.current.isSaving).toBe(false));
  });
});
