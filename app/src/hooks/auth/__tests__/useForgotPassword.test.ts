import { describe, it, expect, vi, beforeEach } from 'vitest';
import { renderHook, act, waitFor } from '@testing-library/react';
import { useForgotPassword } from '../useForgotPassword';

vi.mock('@/lib/api', () => ({
  default: { post: vi.fn() },
}));

import api from '@/lib/api';

const mockPost = api.post as ReturnType<typeof vi.fn>;

beforeEach(() => {
  vi.clearAllMocks();
});

describe('useForgotPassword', () => {
  it('posts the email and resolves to sent', async () => {
    mockPost.mockResolvedValue({ data: undefined });
    const { result } = renderHook(() => useForgotPassword());

    await act(async () => {
      await result.current.sendLink('test@example.com');
    });

    expect(mockPost).toHaveBeenCalledWith('/api/auth/forgot-password', { email: 'test@example.com' });
    expect(result.current.status).toBe('sent');
  });

  it('resolves to sent even when the request fails, to avoid leaking account existence', async () => {
    mockPost.mockRejectedValue(new Error('network error'));
    const { result } = renderHook(() => useForgotPassword());

    await act(async () => {
      await result.current.sendLink('nobody@example.com');
    });

    await waitFor(() => {
      expect(result.current.status).toBe('sent');
    });
  });

  it('reset returns to idle', async () => {
    mockPost.mockResolvedValue({ data: undefined });
    const { result } = renderHook(() => useForgotPassword());

    await act(async () => {
      await result.current.sendLink('test@example.com');
    });
    act(() => {
      result.current.reset();
    });

    expect(result.current.status).toBe('idle');
  });
});
