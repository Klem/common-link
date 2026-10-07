import { describe, it, expect, vi } from 'vitest';
import { renderHook, waitFor } from '@testing-library/react';
import { useCampaignStory } from '../useCampaignStory';
import { getCampaignReport } from '@/lib/api/donor';
import type { DonorCampaignReportDto } from '@/types/donor';

vi.mock('@/lib/api/donor', () => ({
  getCampaignReport: vi.fn(),
}));

const report = {
  campaignId: 'camp-1',
  story: { storyText: '<p>Un récit complet.</p>', storySummary: 'Résumé.', publishedAt: '2026-09-01T10:00:00Z' },
} as unknown as DonorCampaignReportDto;

describe('useCampaignStory', () => {
  it('does not fetch when campaignId is null', () => {
    const { result } = renderHook(() => useCampaignStory(null));

    expect(getCampaignReport).not.toHaveBeenCalled();
    expect(result.current).toEqual({ story: null, isLoading: false, error: null });
  });

  it('extracts the story from the campaign report on success', async () => {
    vi.mocked(getCampaignReport).mockResolvedValue(report);
    const { result } = renderHook(() => useCampaignStory('camp-1'));

    expect(result.current.isLoading).toBe(true);
    await waitFor(() => expect(result.current.story).toEqual(report.story));
    expect(getCampaignReport).toHaveBeenCalledWith('camp-1');
    expect(result.current.error).toBeNull();
  });

  it('sets an error when the fetch fails', async () => {
    vi.mocked(getCampaignReport).mockRejectedValue(new Error('network error'));
    const { result } = renderHook(() => useCampaignStory('camp-1'));

    await waitFor(() => expect(result.current.error).not.toBeNull());
    expect(result.current.story).toBeNull();
    expect(result.current.isLoading).toBe(false);
  });
});
