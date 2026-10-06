import { describe, it, expect, vi, beforeEach } from 'vitest';
import { renderHook, act, waitFor } from '@testing-library/react';
import { useCampaignDonors } from '../useCampaignDonors';

vi.mock('@/lib/api/donor-campaign', () => ({
  listDonors: vi.fn(),
  getDonorDonations: vi.fn(),
}));

import { listDonors, getDonorDonations } from '@/lib/api/donor-campaign';
const mockListDonors = listDonors as ReturnType<typeof vi.fn>;
const mockGetDonorDonations = getDonorDonations as ReturnType<typeof vi.fn>;

const emptyPage = { content: [], totalElements: 0, totalPages: 0, number: 0, size: 12, first: true, last: true };

function donation(id: string) {
  return { id, amount: 10, providerRef: `mollie:${id}`, confirmedAt: null, createdAt: '2026-03-15T09:00:00Z', onChain: false };
}

describe('useCampaignDonors', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockListDonors.mockResolvedValue(emptyPage);
  });

  it('loads donors sorted by amount desc by default', async () => {
    renderHook(() => useCampaignDonors('camp-1'));
    await waitFor(() => expect(mockListDonors).toHaveBeenCalledWith('camp-1', 0, 12, undefined, 'amount', 'desc'));
  });

  it('toggleSort flips direction on the same column and resets to asc on another', async () => {
    const { result } = renderHook(() => useCampaignDonors('camp-1'));
    await waitFor(() => expect(result.current.isLoading).toBe(false));

    act(() => result.current.toggleSort('amount'));
    await waitFor(() => expect(mockListDonors).toHaveBeenLastCalledWith('camp-1', 0, 12, undefined, 'amount', 'asc'));

    act(() => result.current.toggleSort('name'));
    await waitFor(() => expect(mockListDonors).toHaveBeenLastCalledWith('camp-1', 0, 12, undefined, 'name', 'asc'));
    expect(result.current.direction).toBe('asc');
  });

  it('toggleSort goes back to page 0', async () => {
    const { result } = renderHook(() => useCampaignDonors('camp-1'));
    act(() => result.current.setPage(2));
    act(() => result.current.toggleSort('date'));
    expect(result.current.page).toBe(0);
  });

  it('toggleDonor keeps a single donor open and loads every page of its donations', async () => {
    mockGetDonorDonations
      .mockResolvedValueOnce({ ...emptyPage, content: [donation('a'), donation('b')], last: false })
      .mockResolvedValueOnce({ ...emptyPage, content: [donation('c')], last: true });
    const { result } = renderHook(() => useCampaignDonors('camp-1'));

    act(() => result.current.toggleDonor('donor-1'));
    expect(result.current.openDonorId).toBe('donor-1');
    await waitFor(() => expect(result.current.donorDonations.map((d) => d.id)).toEqual(['a', 'b', 'c']));
    expect(mockGetDonorDonations).toHaveBeenNthCalledWith(1, 'camp-1', 'donor-1', 0, 100);
    expect(mockGetDonorDonations).toHaveBeenNthCalledWith(2, 'camp-1', 'donor-1', 1, 100);

    mockGetDonorDonations.mockResolvedValue({ ...emptyPage, content: [donation('z')] });
    act(() => result.current.toggleDonor('donor-2'));
    expect(result.current.openDonorId).toBe('donor-2');

    act(() => result.current.toggleDonor('donor-2'));
    expect(result.current.openDonorId).toBeNull();
    await waitFor(() => expect(result.current.donorDonations).toEqual([]));
  });
});
