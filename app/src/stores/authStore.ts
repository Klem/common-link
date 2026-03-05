import { create } from 'zustand';
import Cookies from 'js-cookie';

export interface UserDto {
  id: string;
  email: string;
  role: 'ASSOCIATION' | 'DONOR';
  name?: string;
}

interface AuthState {
  accessToken: string | null;
  user: UserDto | null;
  isAuthenticated: boolean;
  isLoading: boolean;

  setAuth: (accessToken: string, refreshToken: string, user: UserDto) => void;
  setAccessToken: (accessToken: string) => void;
  logout: () => Promise<void>;
  hydrateFromStorage: () => Promise<void>;
}

const IS_PROD = process.env.NODE_ENV === 'production';
const SEARCH_ASSOCIATION_URL = process.env.NEXT_PUBLIC_API_URL ?? 'http://localhost:8080';

export const useAuthStore = create<AuthState>((set, get) => ({
  accessToken: null,
  user: null,
  isAuthenticated: false,
  isLoading: true,

  setAuth: (accessToken, refreshToken, user) => {
    Cookies.set('cl-refresh', refreshToken, {
      expires: 30,
      sameSite: 'strict',
      secure: IS_PROD,
    });
    Cookies.set('auth-session', JSON.stringify({ userId: user.id, role: user.role }), {
      expires: 30,
      sameSite: 'strict',
      secure: IS_PROD,
    });
    set({ accessToken, user, isAuthenticated: true });
  },

  setAccessToken: (accessToken) => {
    set({ accessToken });
  },

  logout: async () => {
    const { accessToken } = get();
    try {
      if (accessToken) {
        await fetch(`${SEARCH_ASSOCIATION_URL}/api/auth/logout`, {
          method: 'POST',
          headers: { Authorization: `Bearer ${accessToken}` },
        });
      }
    } catch {
      // Ignore logout errors — clean up client-side regardless
    } finally {
      Cookies.remove('cl-refresh');
      Cookies.remove('auth-session');
      set({ accessToken: null, user: null, isAuthenticated: false });
      window.location.href = '/login';
    }
  },

  hydrateFromStorage: async () => {
    const refreshToken = Cookies.get('cl-refresh');
    if (!refreshToken) {
      set({ isLoading: false });
      return;
    }

    try {

      const response = await fetch(`${SEARCH_ASSOCIATION_URL}/api/auth/refresh`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ refreshToken }),
      });

      if (!response.ok) {
        throw new Error('Refresh failed');
      }

      const data = (await response.json()) as {
        accessToken: string;
        refreshToken: string;
        user: UserDto;
      };
      get().setAuth(data.accessToken, data.refreshToken, data.user);
    } catch {
      // Silent logout — token expired or revoked
      Cookies.remove('cl-refresh');
      Cookies.remove('auth-session');
      set({ accessToken: null, user: null, isAuthenticated: false });
    } finally {
      set({ isLoading: false });
    }
  },
}));
