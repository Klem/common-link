import { describe, it, expect, vi, beforeEach } from 'vitest';
import { renderHook, act } from '@testing-library/react';
import { AxiosError, AxiosHeaders } from 'axios';
import { useEmailRegister } from '../useEmailRegister';
import { UserRole } from '@/types/auth';

vi.mock('@/lib/api', () => ({
  default: { post: vi.fn() },
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

const registerAsso = async (result: { current: ReturnType<typeof useEmailRegister> }) => {
  await act(async () => {
    await result.current
      .register('asso@example.com', 'password123', UserRole.ASSOCIATION, {
        name: 'MyAsso',
        identifier: '775672272',
      })
      .catch(() => undefined);
  });
};

describe('useEmailRegister — conflict mapping', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    sessionStorage.clear();
  });

  it('reports a duplicate SIREN distinctly from a duplicate email', async () => {
    mockPost.mockRejectedValue(conflict({ code: 'SIREN_ALREADY_REGISTERED' }));
    const { result } = renderHook(() => useEmailRegister());

    await registerAsso(result);

    expect(result.current.error).toBe('errors.sirenAlreadyRegistered');
  });

  it('still reports a duplicate email as an existing account', async () => {
    mockPost.mockRejectedValue(conflict({ code: 'EMAIL_ALREADY_EXISTS' }));
    const { result } = renderHook(() => useEmailRegister());

    await registerAsso(result);

    expect(result.current.error).toBe('errors.accountExists');
  });

  it('falls back to the existing-account message on a bare 409', async () => {
    mockPost.mockRejectedValue(conflict({}));
    const { result } = renderHook(() => useEmailRegister());

    await registerAsso(result);

    expect(result.current.error).toBe('errors.accountExists');
  });
});
