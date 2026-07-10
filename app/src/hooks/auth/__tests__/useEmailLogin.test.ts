import { describe, it, expect, vi, beforeEach } from 'vitest';
import { renderHook, act } from '@testing-library/react';
import { useEmailLogin } from '../useEmailLogin';

const mockPush = vi.fn();

vi.mock('next/navigation', () => ({
  useRouter: () => ({ push: mockPush }),
}));

vi.mock('next-intl', () => ({
  useLocale: () => 'fr',
}));

vi.mock('@/lib/api', () => ({
  default: { post: vi.fn() },
}));

vi.mock('@/stores/authStore', () => ({
  useAuthStore: vi.fn(() => ({ setAuth: vi.fn() })),
}));

import api from '@/lib/api';
import { useAuthStore } from '@/stores/authStore';

const mockPost = api.post as ReturnType<typeof vi.fn>;
const mockSetAuth = vi.fn();

const makeUser = (role: string) => ({
  id: 'user-1',
  email: 'test@example.com',
  role,
  displayName: 'Test User',
  provider: 'EMAIL',
  emailVerified: true,
  createdAt: '2024-01-01T00:00:00Z',
});

beforeEach(() => {
  vi.clearAllMocks();
  (useAuthStore as unknown as ReturnType<typeof vi.fn>).mockReturnValue({ setAuth: mockSetAuth });
});

describe('useEmailLogin — post-login redirect', () => {
  it('redirects DONOR to /fr/dashboard/donor after login', async () => {
    mockPost.mockResolvedValueOnce({ data: { accessToken: 'tok', user: makeUser('DONOR') } });
    const { result } = renderHook(() => useEmailLogin());
    await act(() => result.current.onSubmit('a@b.com', 'password1'));
    expect(mockPush).toHaveBeenCalledWith('/fr/dashboard/donor');
  });

  it('redirects ASSOCIATION to /fr/dashboard/association after login', async () => {
    mockPost.mockResolvedValueOnce({ data: { accessToken: 'tok', user: makeUser('ASSOCIATION') } });
    const { result } = renderHook(() => useEmailLogin());
    await act(() => result.current.onSubmit('a@b.com', 'password1'));
    expect(mockPush).toHaveBeenCalledWith('/fr/dashboard/association');
  });

  it('redirects CURATOR to /fr/admin after login', async () => {
    mockPost.mockResolvedValueOnce({ data: { accessToken: 'tok', user: makeUser('CURATOR') } });
    const { result } = renderHook(() => useEmailLogin());
    await act(() => result.current.onSubmit('curator@cl.org', 'password1'));
    expect(mockPush).toHaveBeenCalledWith('/fr/admin');
  });

  it('honors ?redirect deep-link for CURATOR', async () => {
    mockPost.mockResolvedValueOnce({ data: { accessToken: 'tok', user: makeUser('CURATOR') } });
    const deepLink = '/fr/admin/verifications/abc-123';
    const { result } = renderHook(() => useEmailLogin(deepLink));
    await act(() => result.current.onSubmit('curator@cl.org', 'password1'));
    expect(mockPush).toHaveBeenCalledWith(deepLink);
  });

  it('ignores null redirectAfterLogin and falls back to home path', async () => {
    mockPost.mockResolvedValueOnce({ data: { accessToken: 'tok', user: makeUser('CURATOR') } });
    const { result } = renderHook(() => useEmailLogin(null));
    await act(() => result.current.onSubmit('curator@cl.org', 'password1'));
    expect(mockPush).toHaveBeenCalledWith('/fr/admin');
  });
});
