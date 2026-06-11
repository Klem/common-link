import { describe, it, expect, vi, beforeEach, beforeAll } from 'vitest';
import Cookies from 'js-cookie';
import { useAuthStore } from '@/stores/authStore';
import type { UserDto } from '@/types/auth';
import { UserRole, AuthProvider } from '@/types/auth';

vi.mock('js-cookie', () => ({
  default: {
    set: vi.fn(),
    get: vi.fn(),
    remove: vi.fn(),
  },
}));

global.fetch = vi.fn();

beforeAll(() => {
  Object.defineProperty(window, 'location', {
    configurable: true,
    value: { href: '' },
  });
});

const mockUser: UserDto = {
  id: 'user-1',
  email: 'test@example.com',
  role: UserRole.DONOR,
  displayName: 'Test User',
  provider: AuthProvider.EMAIL,
  emailVerified: true,
  createdAt: '2024-01-01T00:00:00Z',
};

beforeEach(() => {
  useAuthStore.setState({
    accessToken: null,
    user: null,
    isAuthenticated: false,
    isLoading: true,
  });
  vi.clearAllMocks();
});

describe('authStore', () => {
  describe('setAuth()', () => {
    it('stores accessToken in memory and writes auth-session cookie', () => {
      useAuthStore.getState().setAuth('access-token', mockUser);

      expect(useAuthStore.getState().accessToken).toBe('access-token');
      expect(useAuthStore.getState().isAuthenticated).toBe(true);
      expect(useAuthStore.getState().user).toEqual(mockUser);
      expect(Cookies.set).not.toHaveBeenCalledWith('cl-refresh', expect.anything(), expect.anything());
      expect(Cookies.set).toHaveBeenCalledWith(
        'auth-session',
        JSON.stringify({ userId: 'user-1', role: UserRole.DONOR }),
        expect.any(Object),
      );
    });
  });

  describe('setAccessToken()', () => {
    it('updates only the accessToken without touching cookies', () => {
      useAuthStore.setState({ accessToken: 'old-token', isAuthenticated: true });
      useAuthStore.getState().setAccessToken('new-token');

      expect(useAuthStore.getState().accessToken).toBe('new-token');
      expect(Cookies.set).not.toHaveBeenCalled();
    });
  });

  describe('logout()', () => {
    it('clears the store, removes auth-session cookie, and sends credentials', async () => {
      useAuthStore.setState({ accessToken: 'token', user: mockUser, isAuthenticated: true });
      (global.fetch as ReturnType<typeof vi.fn>).mockResolvedValue({ ok: true });

      await useAuthStore.getState().logout();

      expect(global.fetch).toHaveBeenCalledWith(
        expect.stringContaining('/api/auth/logout'),
        expect.objectContaining({ credentials: 'include' }),
      );
      expect(Cookies.remove).not.toHaveBeenCalledWith('cl-refresh');
      expect(Cookies.remove).toHaveBeenCalledWith('auth-session');
      expect(useAuthStore.getState().accessToken).toBeNull();
      expect(useAuthStore.getState().user).toBeNull();
      expect(useAuthStore.getState().isAuthenticated).toBe(false);
    });
  });

  describe('hydrateFromStorage()', () => {
    it('always attempts refresh with credentials and restores session on success', async () => {
      (global.fetch as ReturnType<typeof vi.fn>).mockResolvedValue({
        ok: true,
        json: async () => ({
          accessToken: 'new-access',
          user: mockUser,
        }),
      });

      await useAuthStore.getState().hydrateFromStorage();

      expect(global.fetch).toHaveBeenCalledWith(
        expect.stringContaining('/api/auth/refresh'),
        expect.objectContaining({ credentials: 'include' }),
      );
      expect(useAuthStore.getState().accessToken).toBe('new-access');
      expect(useAuthStore.getState().isAuthenticated).toBe(true);
      expect(useAuthStore.getState().isLoading).toBe(false);
    });

    it('clears session silently when refresh returns 401', async () => {
      (global.fetch as ReturnType<typeof vi.fn>).mockResolvedValue({ ok: false });

      await useAuthStore.getState().hydrateFromStorage();

      expect(global.fetch).toHaveBeenCalled();
      expect(useAuthStore.getState().accessToken).toBeNull();
      expect(useAuthStore.getState().isLoading).toBe(false);
    });
  });
});
