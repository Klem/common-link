'use client';

import { useState } from 'react';
import { useRouter } from 'next/navigation';
import { useLocale } from 'next-intl';
import { isAxiosError } from 'axios';
import { z } from 'zod';
import api from '@/lib/api';
import { useAuthStore } from '@/stores/authStore';
import type { AuthResponseDto } from '@/types/auth';

export const emailLoginSchema = z.object({
  email: z.string().email('errors.emailInvalid'),
  password: z.string().min(8, 'errors.passwordTooShort'),
});

export type EmailLoginFormData = z.infer<typeof emailLoginSchema>;

interface ProblemDetail {
  code?: string;
  [key: string]: unknown;
}

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
