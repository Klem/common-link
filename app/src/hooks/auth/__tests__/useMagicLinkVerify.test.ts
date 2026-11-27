import { describe, it, expect, vi, beforeEach } from 'vitest';
import { renderHook, waitFor } from '@testing-library/react';
import { AxiosError, AxiosHeaders } from 'axios';
import { useMagicLinkVerify } from '../useMagicLinkVerify';

vi.mock('@/lib/api', () => ({
  default: { post: vi.fn() },
}));
vi.mock('next/navigation', () => ({
  useRouter: () => ({ push: vi.fn() }),
}));
vi.mock('next-intl', () => ({
  useLocale: () => 'fr',
}));
vi.mock('@/stores/authStore', () => ({
  useAuthStore: () => ({ setAuth: vi.fn() }),
}));
vi.mock('@/stores/toastStore', () => ({
  useToastStore: () => ({ addToast: vi.fn() }),
}));

import api from '@/lib/api';

const mockPost = api.post as ReturnType<typeof vi.fn>;

/** Builds an AxiosError carrying an RFC 7807 body, as the backend returns it. */
const conflict = (body: Record<string, unknown>): AxiosError => {
  const config = { headers: new AxiosHeaders() };
  const err = new AxiosError('Conflict', 'ERR_BAD_REQUEST', config as never);
  err.response = { status: 409, statusText: 'Conflict', data: body, headers: {}, config } as never;
  return err;
};

describe('useMagicLinkVerify — conflict mapping', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('reports a SIREN registered since the link was sent', async () => {
    mockPost.mockRejectedValue(conflict({ code: 'SIREN_ALREADY_REGISTERED' }));
    const { result } = renderHook(() => useMagicLinkVerify('token-1'));

    await waitFor(() => expect(result.current.status).toBe('error'));
    expect(result.current.error).toBe('errors.sirenAlreadyRegistered');
  });

  it('reports an RNA registered since the link was sent', async () => {
    mockPost.mockRejectedValue(conflict({ code: 'RNA_ALREADY_REGISTERED' }));
    const { result } = renderHook(() => useMagicLinkVerify('token-2'));

    await waitFor(() => expect(result.current.status).toBe('error'));
    expect(result.current.error).toBe('errors.rnaAlreadyRegistered');
  });
});
