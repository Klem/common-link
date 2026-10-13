import { describe, it, expect, vi, beforeEach } from 'vitest';
import { renderHook, act, waitFor } from '@testing-library/react';
import { useMollieKycStatus } from '../useMollieKycStatus';
import { MollieOnboardingStatus } from '@/types/mollie-connect';
import type { MollieKycStatus } from '@/types/mollie-connect';

const { mockGet, mockRefresh } = vi.hoisted(() => ({ mockGet: vi.fn(), mockRefresh: vi.fn() }));
vi.mock('@/lib/api/mollie-connect', () => ({
  getMollieKycStatus: mockGet,
  refreshMollieKycStatus: mockRefresh,
}));

const dto = (overrides: Partial<MollieKycStatus> = {}): MollieKycStatus => ({
  connected: true,
  pending: false,
  broken: false,
  onboardingStatus: MollieOnboardingStatus.NEEDS_DATA,
  canReceivePayments: false,
  dashboardUrl: 'https://my.mollie.com/dashboard/onboarding',
  canForceComplete: false,
  complementRequested: false,
  ...overrides,
});

describe('useMollieKycStatus', () => {
  beforeEach(() => {
    mockGet.mockReset().mockResolvedValue(dto());
    mockRefresh.mockReset();
  });

  it('loads once on mount and records when the status was read', async () => {
    const { result } = renderHook(() => useMollieKycStatus());

    await waitFor(() => expect(result.current.isLoading).toBe(false));
    expect(mockGet).toHaveBeenCalledTimes(1);
    expect(result.current.complementRequested).toBe(false);
    expect(result.current.lastUpdatedAt).toBeInstanceOf(Date);
  });

  it('re-reads silently without ever flipping isLoading', async () => {
    const { result } = renderHook(() => useMollieKycStatus());
    await waitFor(() => expect(result.current.isLoading).toBe(false));

    let resolve: (value: MollieKycStatus) => void = () => {};
    mockGet.mockReturnValue(new Promise<MollieKycStatus>((r) => (resolve = r)));
    let pending: Promise<void> = Promise.resolve();
    act(() => {
      pending = result.current.refresh({ silent: true });
    });
    expect(result.current.isLoading).toBe(false);
    await act(async () => {
      resolve(dto({ onboardingStatus: MollieOnboardingStatus.IN_REVIEW }));
      await pending;
    });
    expect(result.current.isLoading).toBe(false);
    expect(result.current.onboardingStatus).toBe(MollieOnboardingStatus.IN_REVIEW);
  });

  it('applies the forced-refresh status and shares one request between concurrent calls', async () => {
    const { result } = renderHook(() => useMollieKycStatus());
    await waitFor(() => expect(result.current.isLoading).toBe(false));
    mockRefresh.mockResolvedValue(dto({ complementRequested: true }));

    await act(async () => {
      await Promise.all([result.current.forceRefresh(), result.current.forceRefresh()]);
    });

    expect(mockRefresh).toHaveBeenCalledTimes(1);
    expect(result.current.complementRequested).toBe(true);
    expect(result.current.isLoading).toBe(false);
  });

  it('passes the silent flag to the API so background failures show no toast (R8)', async () => {
    const { result } = renderHook(() => useMollieKycStatus());
    await waitFor(() => expect(result.current.isLoading).toBe(false));
    expect(mockGet).toHaveBeenLastCalledWith({ silent: false });
    mockRefresh.mockResolvedValue(dto());

    await act(async () => {
      await result.current.refresh({ silent: true });
      await result.current.forceRefresh({ silent: true });
    });
    expect(mockGet).toHaveBeenLastCalledWith({ silent: true });
    expect(mockRefresh).toHaveBeenLastCalledWith({ silent: true });

    await act(async () => {
      await result.current.forceRefresh();
    });
    expect(mockRefresh).toHaveBeenLastCalledWith({ silent: false });
  });

  it('keeps the previous state and no error after a failed silent re-read (R8)', async () => {
    const debug = vi.spyOn(console, 'debug').mockImplementation(() => {});
    const { result } = renderHook(() => useMollieKycStatus());
    await waitFor(() => expect(result.current.isLoading).toBe(false));
    mockGet.mockRejectedValue(new Error('down'));

    await act(async () => {
      await result.current.refresh({ silent: true });
    });

    expect(result.current.error).toBeNull();
    expect(result.current.onboardingStatus).toBe(MollieOnboardingStatus.NEEDS_DATA);
    expect(debug).toHaveBeenCalled();
    debug.mockRestore();
  });

  it('still reports an error after a failed explicit re-read', async () => {
    const { result } = renderHook(() => useMollieKycStatus());
    await waitFor(() => expect(result.current.isLoading).toBe(false));
    mockGet.mockRejectedValue(new Error('down'));

    await act(async () => {
      await result.current.refresh();
    });

    expect(result.current.error).toBe('common.errors.serverError');
  });

  it('keeps the current state when the forced refresh fails', async () => {
    const { result } = renderHook(() => useMollieKycStatus());
    await waitFor(() => expect(result.current.isLoading).toBe(false));
    mockRefresh.mockRejectedValue(new Error('down'));

    await act(async () => {
      await result.current.forceRefresh();
    });

    expect(result.current.onboardingStatus).toBe(MollieOnboardingStatus.NEEDS_DATA);
    expect(result.current.error).toBeNull();
  });
});
