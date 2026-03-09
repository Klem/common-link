import { describe, it, expect, vi, beforeEach, beforeAll } from 'vitest';
import Cookies from 'js-cookie';
import { useAuthStore } from '@/stores/authStore';
import type { UserDto } from '@/types/auth';

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
  role: 'DONOR',
  displayName: 'Test User',
  provider: 'EMAIL',
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
  (Cookies.get as ReturnType<typeof vi.fn>).mockReturnValue(undefined);
});

describe('authStore', () => {
  describe('setAuth()', () => {
    it('stores accessToken in memory and refreshToken in cl-refresh cookie', () => {
      useAuthStore.getState().setAuth('access-token', 'refresh-token', mockUser);

      expect(useAuthStore.getState().accessToken).toBe('access-token');
      expect(useAuthStore.getState().isAuthenticated).toBe(true);
      expect(useAuthStore.getState().user).toEqual(mockUser);
      expect(Cookies.set).toHaveBeenCalledWith('cl-refresh', 'refresh-token', expect.any(Object));
      expect(Cookies.set).toHaveBeenCalledWith(
        'auth-session',
        JSON.stringify({ userId: 'user-1', role: 'DONOR' }),
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
    it('clears the store and removes cl-refresh and auth-session cookies', async () => {
      useAuthStore.setState({ accessToken: 'token', user: mockUser, isAuthenticated: true });
      (global.fetch as ReturnType<typeof vi.fn>).mockResolvedValue({ ok: true });

      await useAuthStore.getState().logout();

      expect(Cookies.remove).toHaveBeenCalledWith('cl-refresh');
      expect(Cookies.remove).toHaveBeenCalledWith('auth-session');
      expect(useAuthStore.getState().accessToken).toBeNull();
      expect(useAuthStore.getState().user).toBeNull();
      expect(useAuthStore.getState().isAuthenticated).toBe(false);
    });
  });

  describe('hydrateFromStorage()', () => {
    it('reads cl-refresh cookie and restores the session', async () => {
      (Cookies.get as ReturnType<typeof vi.fn>).mockReturnValue('stored-refresh-token');
      (global.fetch as ReturnType<typeof vi.fn>).mockResolvedValue({
        ok: true,
        json: async () => ({
          accessToken: 'new-access',
          refreshToken: 'new-refresh',
          user: mockUser,
        }),
      });

      await useAuthStore.getState().hydrateFromStorage();

      expect(useAuthStore.getState().accessToken).toBe('new-access');
      expect(useAuthStore.getState().isAuthenticated).toBe(true);
      expect(useAuthStore.getState().isLoading).toBe(false);
    });

    it('performs silent logout when cl-refresh cookie is absent', async () => {
      await useAuthStore.getState().hydrateFromStorage();

      expect(global.fetch).not.toHaveBeenCalled();
      expect(useAuthStore.getState().accessToken).toBeNull();
      expect(useAuthStore.getState().isLoading).toBe(false);
    });
  });
});
