import { create } from 'zustand';
import Cookies from 'js-cookie';
import type { UserDto } from '@/types/auth';

export type { UserDto };

/**
 * Zustand state shape for global authentication state.
 *
 * Token storage strategy:
 * - `accessToken` lives in memory only (short-lived, 15 min) — never persisted.
 * - `refreshToken` is stored in the `cl-refresh` cookie (SameSite=Strict, 30 days)
 *    so it survives page reloads without exposing it to localStorage XSS risks.
 * - `auth-session` cookie carries `{ userId, role }` as a hint for the Next.js
 *    middleware to protect dashboard routes without decoding the JWT server-side.
 */
interface AuthState {
  /** JWT access token held in memory. Injected into every Axios request header. */
  accessToken: string | null;
  /** Authenticated user data. Null when logged out or before hydration completes. */
  user: UserDto | null;
  /** True once `setAuth` has been called with valid credentials. */
  isAuthenticated: boolean;
  /**
   * True while `hydrateFromStorage` is running on app mount.
   * The `AuthProvider` blocks rendering until this is false to prevent
   * unauthenticated flashes on protected routes.
   */
  isLoading: boolean;

  /**
   * Stores tokens and user after a successful login or signup response.
   * Persists `refreshToken` to the `cl-refresh` cookie and writes the
   * `auth-session` cookie used by the Next.js middleware.
   */
  setAuth: (accessToken: string, refreshToken: string, user: UserDto) => void;
  /**
   * Updates only the in-memory access token.
   * Called by the Axios interceptor after a successful silent refresh,
   * without touching the refresh cookie or user object.
   */
  setAccessToken: (accessToken: string) => void;
  /**
   * Calls `POST /api/auth/logout`, clears all auth state and cookies,
   * then performs a hard redirect to `/login`.
   * Errors from the server call are intentionally swallowed — the client
   * clears its state regardless.
   */
  logout: () => Promise<void>;
  /**
   * Called once on app mount (inside `AuthProvider`).
   * Reads the `cl-refresh` cookie; if present, attempts a silent token refresh
   * to restore the session without requiring the user to log in again.
   * On failure (revoked/expired token) the cookies are cleared silently.
   */
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
