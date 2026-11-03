import { describe, it, expect, vi, beforeEach } from 'vitest';
import { renderHook, act, waitFor } from '@testing-library/react';
import { usePaymentStatus } from '../usePaymentStatus';
import { BankSetupStatus } from '@/lib/bankSetupStatus';
import { PaymentProviderId } from '@/types/paymentProvider';
import type { PaymentStatusResponse } from '@/types/paymentProvider';
import { HelloAssoLink, HelloAssoWebhookState } from '@/types/helloasso';

const { mockGet } = vi.hoisted(() => ({ mockGet: vi.fn() }));
vi.mock('@/lib/api/paymentProvider', () => ({ getPaymentStatus: mockGet }));

const dto = (overrides: Partial<PaymentStatusResponse> = {}): PaymentStatusResponse => ({
  provider: PaymentProviderId.MOLLIE,
  status: BankSetupStatus.NEEDS_DATA,
  actionUrl: 'https://my.mollie.com/dashboard/onboarding',
  warnings: [],
  ...overrides,
});

describe('usePaymentStatus', () => {
  beforeEach(() => {
    mockGet.mockReset().mockResolvedValue(dto());
  });

  it('starts loading, then exposes the status read once on mount', async () => {
    const { result } = renderHook(() => usePaymentStatus());
    expect(result.current.isLoading).toBe(true);
    expect(result.current.status).toBeNull();

    await waitFor(() => expect(result.current.isLoading).toBe(false));
    expect(mockGet).toHaveBeenCalledTimes(1);
    expect(result.current.provider).toBe(PaymentProviderId.MOLLIE);
    expect(result.current.status).toBe(BankSetupStatus.NEEDS_DATA);
    expect(result.current.actionUrl).toBe('https://my.mollie.com/dashboard/onboarding');
    expect(result.current.error).toBeNull();
    // A Mollie response carries no `helloasso` key.
    expect(result.current.helloasso).toBeNull();
  });

  it('exposes the HelloAsso link details of a HelloAsso association', async () => {
    const helloasso = {
      link: HelloAssoLink.OK,
      notificationUrl: 'https://api.example/api/public/webhooks/helloasso/tok',
      organizationSlug: 'les-amis',
      canCollect: false,
      everCompliant: false,
      lastEligibilityCheckAt: '2026-11-03T09:41:00Z',
      webhook: HelloAssoWebhookState.UNKNOWN,
      clientIdMasked: 'abcd…wxyz',
      linkedAt: '2026-11-03T09:38:00Z',
      linkedBy: 'asso@example.org',
      secretWipedAt: '2026-11-03T09:38:00Z',
      lastRefreshAt: null,
    };
    mockGet.mockResolvedValue(dto({ provider: PaymentProviderId.HELLOASSO, status: BankSetupStatus.IN_REVIEW, helloasso }));

    const { result } = renderHook(() => usePaymentStatus());

    await waitFor(() => expect(result.current.isLoading).toBe(false));
    expect(result.current.helloasso).toEqual(helloasso);
  });

  it('settles with an error key when the read fails', async () => {
    mockGet.mockRejectedValue(new Error('500'));

    const { result } = renderHook(() => usePaymentStatus());

    await waitFor(() => expect(result.current.isLoading).toBe(false));
    expect(result.current.error).toBe('common.errors.serverError');
    expect(result.current.status).toBeNull();
  });

  it('re-reads silently without flipping isLoading and keeps the previous state on failure', async () => {
    const { result } = renderHook(() => usePaymentStatus());
    await waitFor(() => expect(result.current.isLoading).toBe(false));

    mockGet.mockRejectedValue(new Error('network'));
    await act(async () => {
      await result.current.refresh({ silent: true });
    });

    expect(mockGet).toHaveBeenLastCalledWith({ silent: true });
    expect(result.current.isLoading).toBe(false);
    expect(result.current.error).toBeNull();
    expect(result.current.status).toBe(BankSetupStatus.NEEDS_DATA);
  });

  it('applies a refreshed status', async () => {
    const { result } = renderHook(() => usePaymentStatus());
    await waitFor(() => expect(result.current.isLoading).toBe(false));

    mockGet.mockResolvedValue(dto({ status: BankSetupStatus.COMPLETED, actionUrl: null }));
    await act(async () => {
      await result.current.refresh();
    });

    expect(result.current.status).toBe(BankSetupStatus.COMPLETED);
    expect(result.current.actionUrl).toBeNull();
  });
});
