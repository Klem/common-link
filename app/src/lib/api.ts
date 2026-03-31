/**
 * Centralised Axios instance for all CommonLink API calls.
 *
 * Responsibilities:
 * 1. **Request interceptor** — attaches the current `accessToken` from the
 *    Zustand auth store as an `Authorization: Bearer` header on every request.
 *
 * 2. **Response interceptor — 401 handling** — on a 401 response the interceptor
 *    reads the `cl-refresh` cookie, calls `POST /api/auth/refresh`, stores the new
 *    access token, and replays the original request transparently.  A queue
 *    (`failedQueue`) ensures that concurrent 401s trigger only one refresh attempt;
 *    queued requests resume once the refresh resolves.  If refresh fails, the store
 *    is logged out and the user is redirected to `/login`.
 *
 * 3. **Response interceptor — error toasts** — 409 (conflict), 429 (rate limit),
 *    and 500+ (server error) automatically enqueue a toast notification via
 *    `useToastStore`.
 *
 * Import this singleton everywhere HTTP calls are needed — never create a second
 * Axios instance, as that would bypass the token refresh logic.
 */
import axios, { AxiosError, InternalAxiosRequestConfig } from 'axios';
import Cookies from 'js-cookie';
import { useAuthStore } from '@/stores/authStore';
import { useToastStore } from '@/stores/toastStore';

const api = axios.create({
  baseURL: process.env.NEXT_PUBLIC_API_URL ?? 'http://localhost:8080',
  headers: { 'Content-Type': 'application/json' },
});

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

    // 401 — attempt silent refresh
    if (status === 401 && !originalRequest._retry) {
      const refreshToken = Cookies.get('cl-refresh');

      if (!refreshToken) {
        useAuthStore.getState().logout();
        return Promise.reject(error);
      }

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
        const response = await axios.post<{
          accessToken: string;
          refreshToken: string;
        }>(
          `${process.env.NEXT_PUBLIC_API_URL ?? 'http://localhost:8080'}/api/auth/refresh`,
          { refreshToken },
        );

        const { accessToken, refreshToken: newRefreshToken } = response.data;
        useAuthStore.getState().setAccessToken(accessToken);

        // Update refresh cookie with new token
        Cookies.set('cl-refresh', newRefreshToken, {
          expires: 30,
          sameSite: 'strict',
          secure: process.env.NODE_ENV === 'production',
        });

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
      addToast('warning', `errors.rateLimitExceeded:${seconds}`);
    }

    // 500+ — server error
    if (status !== undefined && status >= 500) {
      addToast('error', 'errors.serverError');
    }

    return Promise.reject(error);
  },
);

export default api;
