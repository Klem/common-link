'use client';

import { useEffect, useRef, useState } from 'react';
import { useRouter } from 'next/navigation';
import { useLocale } from 'next-intl';
import { isAxiosError } from 'axios';
import api from '@/lib/api';
import { useAuthStore } from '@/stores/authStore';
import type { AuthResponseDto } from '@/types/auth';

/**
 * RFC 7807 Problem Detail shape returned by the backend when token verification fails.
 */
interface ProblemDetail {
  code?: string;
  [key: string]: unknown;
}

/** Lifecycle states of the magic link verification request. */
type VerifyStatus = 'idle' | 'verifying' | 'success' | 'error';

/**
 * Hook that verifies a magic link token on mount via `POST /api/auth/magic-link/verify`.
 *
 * Designed to be used on the login page when a `?token=` query parameter is detected.
 * Verification is triggered inside a `useEffect` and is guarded by a `calledRef`
 * to prevent double-invocation in React Strict Mode (where effects run twice in dev).
 *
 * On success:
 * - Stores auth state via `useAuthStore.setAuth`.
 * - Calls `onSuccess()` if provided; otherwise redirects to the role-specific dashboard.
 *
 * On failure, maps backend error codes to i18n keys:
 * - `TOKEN_EXPIRED` → `errors.tokenExpired`
 * - `TOKEN_USED` → `errors.tokenUsed` (single-use tokens)
 * - Other errors → `errors.genericError`
 *
 * @param token - The raw magic link token from the URL query string, or null.
 * @param onSuccess - Optional callback invoked on successful verification instead of
 *                    the default role-based redirect.
 * @returns `status` and i18n `error` key.
 */
export function useMagicLinkVerify(
  token: string | null,
  onSuccess?: () => void,
) {
  const router = useRouter();
  const locale = useLocale();
  const { setAuth } = useAuthStore();
  const [status, setStatus] = useState<VerifyStatus>(token ? 'verifying' : 'idle');
  const [error, setError] = useState<string | null>(null);

  const calledRef = useRef(false);

  useEffect(() => {
    if (!token || calledRef.current) return;
    calledRef.current = true;

    const verify = async () => {
      setStatus('verifying');
      try {
        const { data } = await api.post<AuthResponseDto>('/api/auth/magic-link/verify', { token });
        setAuth(data.accessToken, data.refreshToken, data.user);
        setStatus('success');
        if (onSuccess) {
          onSuccess();
        } else {
          router.push(`/${locale}/dashboard/${data.user.role.toLowerCase()}`);
        }
      } catch (err) {
        if (isAxiosError(err)) {
          const problemDetail = err.response?.data as ProblemDetail | undefined;
          if (problemDetail?.code === 'TOKEN_EXPIRED') {
            setError('errors.tokenExpired');
          } else if (problemDetail?.code === 'TOKEN_USED') {
            setError('errors.tokenUsed');
          } else {
            setError('errors.genericError');
          }
        } else {
          setError('errors.genericError');
        }
        setStatus('error');
      }
    };

    verify();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [token]);

  return { status, error };
}
