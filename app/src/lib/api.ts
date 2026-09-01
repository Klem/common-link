/**
 * Centralised Axios instance for all CommonLink API calls.
 *
 * Responsibilities:
 * 1. **Request interceptor** — attaches the current `accessToken` from the
 *    Zustand auth store as an `Authorization: Bearer` header on every request.
 *
 * 2. **Response interceptor — 401 handling** — on a 401 response the interceptor
 *    calls `POST /api/auth/refresh` (no body; the HttpOnly `cl-refresh` cookie is
 *    sent automatically via `withCredentials`), stores the new access token, and
 *    replays the original request transparently. A queue (`failedQueue`) ensures
 *    that concurrent 401s trigger only one refresh attempt; queued requests resume
 *    once the refresh resolves. If refresh fails, the store is logged out and the
 *    user is redirected to `/login`.
 *
 * 3. **Response interceptor — error toasts** — 409 (conflict), 429 (rate limit),
 *    and 500+ (server error) automatically enqueue a toast notification via
 *    `useToastStore`.
 *
 * Import this singleton everywhere HTTP calls are needed — never create a second
 * Axios instance, as that would bypass the token refresh logic.
 */
import axios, { AxiosError, InternalAxiosRequestConfig } from 'axios';
import { useAuthStore } from '@/stores/authStore';
import { useToastStore } from '@/stores/toastStore';

const rawApiUrl = process.env.NEXT_PUBLIC_API_URL || 'http://localhost:8080';
const apiBaseURL = rawApiUrl.startsWith('http') ? rawApiUrl : `https://${rawApiUrl}`;

/**
 * Public, unauthenticated auth endpoints. A 401 from one of these is a domain outcome (wrong
 * password, invalid/expired/used token, etc.) — never a sign that the caller's session expired,
 * since there is no session yet. The refresh/logout dance below must not run for them: doing so
 * masked the real error behind a doomed refresh call and force-logged-out/redirected the browser
 * (wiping the on-screen error and any other tab's session) instead of just surfacing the failure.
 */
const PUBLIC_AUTH_PATHS = [
  '/api/auth/register',
  '/api/auth/verify-email',
  '/api/auth/resend-verification',
  '/api/auth/signup/google',
  '/api/auth/login/google',
  '/api/auth/magic-link/request',
  '/api/auth/magic-link/verify',
  '/api/auth/forgot-password',
  '/api/auth/login',
];

const isPublicAuthEndpoint = (url?: string): boolean =>
  !!url && PUBLIC_AUTH_PATHS.some((path) => url.includes(path));

const api = axios.create({
  baseURL: apiBaseURL,
  withCredentials: true,
});

/**
 * Resolves an API-relative path into an absolute URL against the API origin.
 *
 * Needed wherever the browser fetches a resource itself rather than through this Axios
 * instance — typically an `<img src>` pointing at a public API endpoint, which would
 * otherwise resolve against the frontend origin.
 *
 * @param path - API-relative path (`/api/...`) or an already absolute URL.
 * @returns Absolute URL.
 */
export const apiUrl = (path: string): string =>
  path.startsWith('http') ? path : `${apiBaseURL}${path}`;

// ─── Request interceptor — attach access token ────────────────────────────────

api.interceptors.request.use((config) => {
  const token = useAuthStore.getState().accessToken;
  if (token) {
    config.headers.Authorization = `Bearer ${token}`;
  }
  return config;
});

// ─── 401 refresh queue ────────────────────────────────────────────────────────

/** True while a token refresh request is in-flight, preventing duplicate refresh calls. */
let isRefreshing = false;
/**
 * Requests that received a 401 while a refresh was already in-flight.
 * Each entry holds the resolve/reject of a Promise that wraps the original request.
 * They are settled in bulk by `processQueue` once the refresh completes.
 */
let failedQueue: Array<{
  resolve: (token: string) => void;
  reject: (err: unknown) => void;
}> = [];

/**
 * Settles all queued requests after a token refresh attempt.
 * @param error - If non-null, all queued requests are rejected with this error.
 * @param token - The new access token to resolve waiting requests with.
 */
function processQueue(error: unknown, token: string | null = null) {
  failedQueue.forEach((prom) => {
    if (error) {
      prom.reject(error);
    } else {
      prom.resolve(token!);
    }
  });
  failedQueue = [];
}

// ─── Response interceptor ─────────────────────────────────────────────────────

api.interceptors.response.use(
  (response) => response,
  async (error: AxiosError) => {
    const originalRequest = error.config as InternalAxiosRequestConfig & {
      _retry?: boolean;
    };
    const status = error.response?.status;
    const { addToast } = useToastStore.getState();

    // 401 — attempt silent refresh via HttpOnly cookie (skipped for public auth endpoints, see
    // isPublicAuthEndpoint above)
    if (status === 401 && !originalRequest._retry && !isPublicAuthEndpoint(originalRequest.url)) {
      if (isRefreshing) {
        return new Promise<string>((resolve, reject) => {
          failedQueue.push({ resolve, reject });
        })
          .then((token) => {
            originalRequest.headers.Authorization = `Bearer ${token}`;
            return api(originalRequest);
          })
          .catch((err) => Promise.reject(err));
      }

      originalRequest._retry = true;
      isRefreshing = true;

      try {
        const response = await axios.post<{ accessToken: string }>(
          `${apiBaseURL}/api/auth/refresh`,
          {},
          { withCredentials: true },
        );

        const { accessToken } = response.data;
        useAuthStore.getState().setAccessToken(accessToken);

        processQueue(null, accessToken);
        originalRequest.headers.Authorization = `Bearer ${accessToken}`;
        return api(originalRequest);
      } catch (refreshError) {
        processQueue(refreshError, null);
        useAuthStore.getState().logout();
        return Promise.reject(refreshError);
      } finally {
        isRefreshing = false;
      }
    }

    // 409 — conflict
    if (status === 409) {
      addToast('warning', 'errors.conflict');
    }

    // 422 — validation errors (caller extracts field errors from response)
    // No global toast — let the form handle it via setError()

    // 429 — rate limit
    if (status === 429) {
      const retryAfter = error.response?.headers['retry-after'];
      const seconds = retryAfter ? String(retryAfter) : '?';
      addToast('warning', 'errors.rateLimitExceeded', { seconds });
    }

    // 500+ — server error
    if (status !== undefined && status >= 500) {
      addToast('error', 'errors.serverError');
    }

    return Promise.reject(error);
  },
);

export default api;
