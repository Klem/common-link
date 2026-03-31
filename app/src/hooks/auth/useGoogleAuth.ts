'use client';

import { useState } from 'react';
import { useRouter } from 'next/navigation';
import { useLocale } from 'next-intl';
import { isAxiosError } from 'axios';
import api from '@/lib/api';
import { useAuthStore } from '@/stores/authStore';
import type { AuthResponseDto } from '@/types/auth';

/** User role discriminator — determines which dashboard the user lands on. */
type UserRole = 'ASSOCIATION' | 'DONOR';

/** Internal state shape for the Google auth hook. */
interface GoogleAuthState {
  loading: boolean;
  /** i18n error key, or null when no error is present. */
  error: string | null;
}

/**
 * Hook that bridges the `@react-oauth/google` `<GoogleLogin>` component with
 * the CommonLink backend.
 *
 * IMPORTANT: CommonLink uses the `<GoogleLogin>` component (not `useGoogleLogin`),
 * which returns an **idToken JWT** in `credentialResponse.credential`. This idToken
 * is forwarded to the backend for server-side validation via `GoogleIdTokenVerifier`.
 *
 * Provides two operations:
 * - `login(idToken)` — `POST /api/auth/login/google` for existing accounts.
 *   Redirects to the role-specific dashboard on success.
 *   Maps 401 to `errors.noAccount` (user has no CommonLink account yet).
 *
 * - `signUp(idToken, role)` — `POST /api/auth/signup/google` for new accounts.
 *   Stores auth state but does NOT redirect; the caller decides the next step
 *   (e.g. show setPassword or redirect to dashboard).
 *   Maps 409 to `errors.accountExists`.
 *
 * Both functions re-throw on error so callers can hide loading overlays.
 *
 * @returns `login`, `signUp`, `loading`, and i18n `error` key.
 */
export function useGoogleAuth() {
  const router = useRouter();
  const locale = useLocale();
  const { setAuth } = useAuthStore();
  const [state, setState] = useState<GoogleAuthState>({ loading: false, error: null });

  const login = async (idToken: string): Promise<void> => {
    setState({ loading: true, error: null });
    try {
      const { data } = await api.post<AuthResponseDto>('/api/auth/login/google', { idToken });
      setAuth(data.accessToken, data.refreshToken, data.user);
      router.push(`/${locale}/dashboard/${data.user.role.toLowerCase()}`);
    } catch (err) {
      if (isAxiosError(err) && err.response?.status === 401) {
        setState({ loading: false, error: 'errors.noAccount' });
      } else {
        setState({ loading: false, error: 'errors.genericError' });
      }
      throw err;
    }
  };

  const signUp = async (idToken: string, role: UserRole): Promise<void> => {
    setState({ loading: true, error: null });
    try {
      const { data } = await api.post<AuthResponseDto>('/api/auth/signup/google', { idToken, role });
      setAuth(data.accessToken, data.refreshToken, data.user);
      // Caller is responsible for next navigation (show setPassword or redirect)
    } catch (err) {
      if (isAxiosError(err) && err.response?.status === 409) {
        setState({ loading: false, error: 'errors.accountExists' });
      } else {
        setState({ loading: false, error: 'errors.genericError' });
      }
      throw err;
    }
    setState({ loading: false, error: null });
  };

  return { login, signUp, ...state };
}
