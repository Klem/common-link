import { describe, it, expect, vi, beforeEach } from 'vitest';
import { renderHook, act, waitFor } from '@testing-library/react';
import { useMoneriumStatus } from '../useMoneriumStatus';

vi.mock('@/lib/api/monerium', () => ({
  getMoneriumStatus: vi.fn(),
}));

import { getMoneriumStatus } from '@/lib/api/monerium';
const mockGetMoneriumStatus = getMoneriumStatus as ReturnType<typeof vi.fn>;

describe('useMoneriumStatus', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  // ── initial state ─────────────────────────────────────────────────────────

  it('starts with isLoading=true, connected=null, pending=null, error=null', () => {
    mockGetMoneriumStatus.mockReturnValue(new Promise(() => {})); // never resolves

    const { result } = renderHook(() => useMoneriumStatus());

    expect(result.current.isLoading).toBe(true);
    expect(result.current.connected).toBeNull();
    expect(result.current.pending).toBeNull();
    expect(result.current.error).toBeNull();
  });

  // ── success ───────────────────────────────────────────────────────────────

  it('sets connected=true and pending=false when wallet is connected', async () => {
    mockGetMoneriumStatus.mockResolvedValue({ connected: true, pending: false });

    const { result } = renderHook(() => useMoneriumStatus());

    await waitFor(() => expect(result.current.isLoading).toBe(false));

    expect(result.current.connected).toBe(true);
    expect(result.current.pending).toBe(false);
    expect(result.current.error).toBeNull();
  });

  it('sets connected=false and pending=true when OAuth flow is in progress', async () => {
    mockGetMoneriumStatus.mockResolvedValue({ connected: false, pending: true });

    const { result } = renderHook(() => useMoneriumStatus());

    await waitFor(() => expect(result.current.isLoading).toBe(false));

    expect(result.current.connected).toBe(false);
    expect(result.current.pending).toBe(true);
    expect(result.current.error).toBeNull();
  });

  it('sets connected=false and pending=false when wallet is not connected', async () => {
    mockGetMoneriumStatus.mockResolvedValue({ connected: false, pending: false });

    const { result } = renderHook(() => useMoneriumStatus());

    await waitFor(() => expect(result.current.isLoading).toBe(false));

    expect(result.current.connected).toBe(false);
    expect(result.current.pending).toBe(false);
    expect(result.current.error).toBeNull();
  });

  // ── error ─────────────────────────────────────────────────────────────────

  it('sets error key and keeps connected=null when API call fails', async () => {
    mockGetMoneriumStatus.mockRejectedValue(new Error('Network error'));

    const { result } = renderHook(() => useMoneriumStatus());

    await waitFor(() => expect(result.current.isLoading).toBe(false));

    expect(result.current.error).toBe('common.errors.serverError');
    expect(result.current.connected).toBeNull();
    expect(result.current.pending).toBeNull();
  });

  // ── refresh ───────────────────────────────────────────────────────────────

  it('refresh re-fetches and updates connected and pending state', async () => {
    mockGetMoneriumStatus.mockResolvedValue({ connected: false, pending: true });

    const { result } = renderHook(() => useMoneriumStatus());
    await waitFor(() => expect(result.current.isLoading).toBe(false));

    expect(result.current.connected).toBe(false);
    expect(result.current.pending).toBe(true);

    mockGetMoneriumStatus.mockResolvedValue({ connected: true, pending: false });

    await act(async () => {
      await result.current.refresh();
    });

    expect(result.current.connected).toBe(true);
    expect(result.current.pending).toBe(false);
    expect(result.current.isLoading).toBe(false);
    expect(mockGetMoneriumStatus).toHaveBeenCalledTimes(2);
  });

  it('refresh resets error state on successful re-fetch', async () => {
    mockGetMoneriumStatus.mockRejectedValueOnce(new Error('Network error'));

    const { result } = renderHook(() => useMoneriumStatus());
    await waitFor(() => expect(result.current.isLoading).toBe(false));

    expect(result.current.error).toBe('common.errors.serverError');

    mockGetMoneriumStatus.mockResolvedValue({ connected: true, pending: false });

    await act(async () => {
      await result.current.refresh();
    });

    expect(result.current.error).toBeNull();
    expect(result.current.connected).toBe(true);
    expect(result.current.pending).toBe(false);
  });
});
