import { describe, it, expect, vi, beforeEach } from 'vitest';
import { renderHook, act } from '@testing-library/react';
import { AxiosError, AxiosHeaders } from 'axios';
import { useMagicLink } from '../useMagicLink';
import { UserRole } from '@/types/auth';

vi.mock('@/lib/api', () => ({
  default: { post: vi.fn() },
}));

import api from '@/lib/api';

const mockPost = api.post as ReturnType<typeof vi.fn>;

/** Builds an AxiosError carrying an RFC 7807 body, as the backend returns it. */
const failure = (status: number, body: Record<string, unknown>): AxiosError => {
  const config = { headers: new AxiosHeaders() };
  const err = new AxiosError('Request failed', 'ERR_BAD_REQUEST', config as never);
  err.response = { status, statusText: '', data: body, headers: {}, config } as never;
  return err;
};

const signUpAsso = async (result: { current: ReturnType<typeof useMagicLink> }) => {
  await act(async () => {
    await result.current.sendLink('asso@example.com', UserRole.ASSOCIATION, {
      name: 'MyAsso',
      identifier: '775672272',
    });
  });
};

describe('useMagicLink — conflict mapping', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('reports a duplicate SIREN', async () => {
    mockPost.mockRejectedValue(failure(409, { code: 'SIREN_ALREADY_REGISTERED' }));
    const { result } = renderHook(() => useMagicLink());

    await signUpAsso(result);

    expect(result.current.status).toBe('error');
    expect(result.current.error).toBe('errors.sirenAlreadyRegistered');
  });

  it('reports a duplicate RNA distinctly from a duplicate email', async () => {
    mockPost.mockRejectedValue(failure(409, { code: 'RNA_ALREADY_REGISTERED' }));
    const { result } = renderHook(() => useMagicLink());

    await signUpAsso(result);

    expect(result.current.status).toBe('error');
    expect(result.current.error).toBe('errors.rnaAlreadyRegistered');
  });

  it('falls back to the existing-account message on a bare 409', async () => {
    mockPost.mockRejectedValue(failure(409, {}));
    const { result } = renderHook(() => useMagicLink());

    await signUpAsso(result);

    expect(result.current.error).toBe('errors.accountExists');
  });
});
