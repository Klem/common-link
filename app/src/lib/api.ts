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

let isRefreshing = false;
let failedQueue: Array<{
  resolve: (token: string) => void;
  reject: (err: unknown) => void;
}> = [];

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
      addToast('warning', 'common.errors.conflict');
    }

    // 422 — validation errors (caller extracts field errors from response)
    // No global toast — let the form handle it via setError()

    // 429 — rate limit
    if (status === 429) {
      const retryAfter = error.response?.headers['retry-after'];
      const seconds = retryAfter ? String(retryAfter) : '?';
      addToast('warning', `common.errors.rateLimitExceeded:${seconds}`);
    }

    // 500+ — server error
    if (status !== undefined && status >= 500) {
      addToast('error', 'common.errors.serverError');
    }

    return Promise.reject(error);
  },
);

export default api;
