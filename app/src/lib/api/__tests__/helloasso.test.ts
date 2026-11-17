import { describe, it, expect, vi, beforeEach } from 'vitest';
import { AxiosError, AxiosHeaders } from 'axios';
import {
  disconnect,
  ensureNotificationUrl,
  helloAssoErrorCode,
  httpStatusOf,
  linkAccount,
  markOnboardingStarted,
  refreshEligibility,
  verifyCredentials,
} from '../helloasso';
import { HelloAssoErrorCode } from '@/types/helloasso';

const { mockPost, mockDelete } = vi.hoisted(() => ({ mockPost: vi.fn(), mockDelete: vi.fn() }));
vi.mock('@/lib/api', () => ({ default: { post: mockPost, delete: mockDelete } }));

const BASE = '/api/association/helloasso';

describe('helloasso API', () => {
  beforeEach(() => {
    mockPost.mockReset().mockResolvedValue({ data: {} });
    mockDelete.mockReset().mockResolvedValue({ data: undefined });
  });

  it('reads the notification URL, silently', async () => {
    mockPost.mockResolvedValue({ data: { notificationUrl: 'https://api.example/hook/tok' } });
    await expect(ensureNotificationUrl()).resolves.toBe('https://api.example/hook/tok');
    expect(mockPost).toHaveBeenCalledWith(`${BASE}/notification-url`, null, { silentErrors: true });
  });

  it('verifies the keys silently, with the abort signal', async () => {
    const response = { verifyId: 'v1', orgName: 'Les Amis', slug: 'les-amis', matchesKyb: true, canCollect: false };
    mockPost.mockResolvedValue({ data: response });
    const controller = new AbortController();

    await expect(verifyCredentials({ clientId: 'id', clientSecret: 'secret' }, controller.signal)).resolves.toEqual(response);
    expect(mockPost).toHaveBeenCalledWith(
      `${BASE}/credentials/verify`,
      { clientId: 'id', clientSecret: 'secret' },
      { silentErrors: true, signal: controller.signal },
    );
  });

  it('links with the verification handle and the mandate', async () => {
    const request = { verifyId: 'v1', mandate: { accepted: true, version: 'helloasso-mandate-v2-2026-10-28' } };
    await linkAccount(request);
    expect(mockPost).toHaveBeenCalledWith(`${BASE}/connection`, request, { silentErrors: true });
  });

  it('disconnects, refreshes eligibility and records the journey start', async () => {
    await disconnect();
    expect(mockDelete).toHaveBeenCalledWith(`${BASE}/connection`);
    await refreshEligibility();
    expect(mockPost).toHaveBeenCalledWith(`${BASE}/eligibility/refresh`, null, { silentErrors: true });
    await markOnboardingStarted();
    expect(mockPost).toHaveBeenCalledWith(`${BASE}/onboarding-started`, null, { silentErrors: true });
  });
});

describe('error helpers', () => {
  const axiosError = (status: number, data: unknown) =>
    new AxiosError('failed', 'ERR_BAD_REQUEST', undefined, undefined, {
      status,
      statusText: '',
      data,
      headers: {},
      config: { headers: new AxiosHeaders() },
    });

  it('reads the ProblemDetail code and the status', () => {
    const error = axiosError(422, { code: HelloAssoErrorCode.INVALID_KEYS });
    expect(helloAssoErrorCode(error)).toBe(HelloAssoErrorCode.INVALID_KEYS);
    expect(httpStatusOf(error)).toBe(422);
  });

  it('is null for anything else', () => {
    expect(helloAssoErrorCode(new Error('boom'))).toBeNull();
    expect(helloAssoErrorCode(axiosError(500, 'oops'))).toBeNull();
    expect(httpStatusOf(new Error('boom'))).toBeNull();
  });
});
