import { create } from 'zustand';
import Cookies from 'js-cookie';
import type { UserDto } from '@/types/auth';

export type { UserDto };

/**
 * Zustand state shape for global authentication state.
 *
 * Token storage strategy:
 * - `accessToken` lives in memory only (short-lived, 15 min) — never persisted.
 * - The refresh token is stored in the `cl-refresh` HttpOnly cookie set by the
 *   backend — not readable from JS, protected from XSS. Sent automatically via
 *   `credentials: 'include'` on every refresh request.
 * - `auth-session` cookie carries `{ userId, role }` as a hint for the Next.js
 *   middleware to protect dashboard routes without decoding the JWT server-side.
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
   * Writes the `auth-session` cookie used by the Next.js middleware.
   * The refresh token is managed exclusively by the backend via the HttpOnly
   * `cl-refresh` cookie — not passed here.
   */
  setAuth: (accessToken: string, user: UserDto) => void;
  /**
   * Updates only the in-memory access token.
   * Called by the Axios interceptor after a successful silent refresh,
   * without touching the refresh cookie or user object.
   */
  setAccessToken: (accessToken: string) => void;
  /** Updates the in-memory user object (e.g. after a profile or password change). */
  setUser: (user: UserDto) => void;
  /**
   * Calls `POST /api/auth/logout`, clears all auth state and the auth-session
   * cookie, then performs a hard redirect to `/login`. The backend clears the
   * HttpOnly cl-refresh cookie. Errors from the server call are intentionally
   * swallowed — the client clears its state regardless.
   */
  logout: () => Promise<void>;
  /**
   * Called once on app mount (inside `AuthProvider`).
   * Attempts a silent token refresh — the HttpOnly cl-refresh cookie is sent
   * automatically via `credentials: 'include'`. On 401, treats the session as
   * expired and clears client state silently.
   */
  hydrateFromStorage: () => Promise<void>;
}

const IS_PROD = process.env.NODE_ENV === 'production';

/**
 * API origin for the refresh call, normalised exactly like `lib/api.ts` and `lib/api/public.ts`.
 *
 * Those two prepend `https://` when the variable carries no scheme, and fall back with `||` so an
 * empty string is treated as unset. This file did neither, and the difference is not cosmetic: a
 * bare host — the shape a PaaS environment variable routinely takes — makes this a **relative**
 * URL. The refresh then posts to the frontend's own origin, 404s, and the catch below wipes
 * `auth-session`, which sends the middleware straight to the login page. Everything authenticated
 * by header keeps working, so only the session appears broken.
 *
 * Deliberately not imported from `@/lib/api`: that module imports this store, and the cycle would
 * be resolved at runtime in an order nothing guarantees.
 */
const RAW_API_URL = process.env.NEXT_PUBLIC_API_URL || 'http://localhost:8080';
const SEARCH_ASSOCIATION_URL = RAW_API_URL.startsWith('http') ? RAW_API_URL : `https://${RAW_API_URL}`;

export const useAuthStore = create<AuthState>((set, get) => ({
  accessToken: null,
  user: null,
  isAuthenticated: false,
  isLoading: true,

  setAuth: (accessToken, user) => {
    // role is stored verbatim from the JWT claim — CURATOR is a valid value here
    Cookies.set('auth-session', JSON.stringify({ userId: user.id, role: user.role }), {
      expires: 30,
      // `lax`, not `strict`: a Strict cookie is withheld on a top-level navigation coming from
      // another site, so returning from the bank's Open Banking tunnel arrived with no cookie at
      // all — the middleware saw no session and bounced the association to the login page, in the
      // middle of authorising a transfer. Lax is sent on exactly that navigation and still
      // withheld on cross-site subresources and POSTs. It costs nothing in authentication either:
      // this cookie is a routing hint for the middleware, never a credential — the API is guarded
      // by the bearer token and the `cl-refresh` cookie, which is checked independently.
      sameSite: 'lax',
      secure: IS_PROD,
    });
    set({ accessToken, user, isAuthenticated: true });
  },

  setAccessToken: (accessToken) => {
    set({ accessToken });
  },

  setUser: (user) => {
    set({ user });
  },

  logout: async () => {
    const { accessToken } = get();
    try {
      if (accessToken) {
        await fetch(`${SEARCH_ASSOCIATION_URL}/api/auth/logout`, {
          method: 'POST',
          headers: { Authorization: `Bearer ${accessToken}` },
          credentials: 'include',
        });
      }
    } catch {
      // Ignore logout errors — clean up client-side regardless
    } finally {
      Cookies.remove('auth-session');
      set({ accessToken: null, user: null, isAuthenticated: false });
      window.location.href = '/login';
    }
  },

  hydrateFromStorage: async () => {
    try {
      const response = await fetch(`${SEARCH_ASSOCIATION_URL}/api/auth/refresh`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        credentials: 'include',
      });

      // A retryable status is not an expired session. 429 (the refresh endpoint is rate-limited
      // per client IP, and a misread X-Forwarded-For makes every user share one quota) and 5xx
      // both mean "ask again later", yet falling through to the catch below logs the user out and
      // bounces them to /login mid-task. Leave the session alone and let the next mount retry.
      if (response.status === 429 || response.status >= 500) {
        return;
      }

      if (!response.ok) {
        throw new Error('Refresh failed');
      }

      const data = (await response.json()) as {
        accessToken: string;
        user: UserDto;
      };
      get().setAuth(data.accessToken, data.user);
    } catch {
      // Silent logout — token expired or revoked
      Cookies.remove('auth-session');
      set({ accessToken: null, user: null, isAuthenticated: false });
    } finally {
      set({ isLoading: false });
    }
  },
}));
