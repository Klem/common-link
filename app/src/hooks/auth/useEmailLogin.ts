'use client';

import { useState } from 'react';
import { useRouter } from 'next/navigation';
import { useLocale } from 'next-intl';
import { isAxiosError } from 'axios';
import { z } from 'zod';
import api from '@/lib/api';
import { useAuthStore } from '@/stores/authStore';
import type { AuthResponseDto } from '@/types/auth';

/**
 * Zod validation schema for the email/password login form.
 * Error values are i18n keys resolved by the consuming component.
 */
export const emailLoginSchema = z.object({
  email: z.string().email('errors.emailInvalid'),
  password: z.string().min(8, 'errors.passwordTooShort'),
});

/** Inferred TypeScript type for the login form data. */
export type EmailLoginFormData = z.infer<typeof emailLoginSchema>;

/**
 * RFC 7807 Problem Detail shape returned by the backend on auth errors.
 * The `code` field carries a machine-readable error discriminator.
 */
interface ProblemDetail {
  code?: string;
  [key: string]: unknown;
}

/**
 * Hook that handles email + password authentication against `POST /api/auth/login`.
 *
 * On success, stores the returned tokens via `useAuthStore.setAuth` and redirects
 * the user to their role-specific dashboard (`/[locale]/dashboard/[role]`).
 *
 * On failure, maps backend error codes to i18n error keys:
 * - `PASSWORD_NOT_SET` (401) → user registered via magic link and has no password yet.
 * - Generic 401 → invalid credentials.
 *
 * @returns `onSubmit` handler, `loading` flag, and i18n error key string.
 */
export function useEmailLogin() {
  const router = useRouter();
  const locale = useLocale();
  const { setAuth } = useAuthStore();
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const onSubmit = async (email: string, password: string): Promise<void> => {
    setLoading(true);
    setError(null);
    try {
      const { data } = await api.post<AuthResponseDto>('/api/auth/login', { email, password });
      setAuth(data.accessToken, data.refreshToken, data.user);
      router.push(`/${locale}/dashboard/${data.user.role.toLowerCase()}`);
    } catch (err) {
      if (isAxiosError(err)) {
        const problemDetail = err.response?.data as ProblemDetail | undefined;
        if (err.response?.status === 401) {
          if (problemDetail?.code === 'PASSWORD_NOT_SET') {
            setError('errors.passwordNotSet');
          } else {
            setError('errors.invalidCredentials');
          }
        } else {
          setError('errors.genericError');
        }
      } else {
        setError('errors.genericError');
      }
    } finally {
      setLoading(false);
    }
  };

  return { onSubmit, loading, error };
}
