'use client';

import { useState } from 'react';
import { isAxiosError } from 'axios';
import api from '@/lib/api';
import { useAuthStore } from '@/stores/authStore';
import type { AuthResponseDto } from '@/types/auth';

type UserRole = 'ASSOCIATION' | 'DONOR';

interface ProblemDetail {
  code?: string;
  [key: string]: unknown;
}

export function useEmailRegister() {
  const { setAuth } = useAuthStore();
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const register = async (email: string, password: string, role: UserRole): Promise<void> => {
    setLoading(true);
    setError(null);
    try {
      const { data } = await api.post<AuthResponseDto>('/api/auth/register', {
        email,
        password,
        role,
      });
      setAuth(data.accessToken, data.refreshToken, data.user);
    } catch (err) {
      if (isAxiosError(err)) {
        const problemDetail = err.response?.data as ProblemDetail | undefined;
        if (err.response?.status === 409 || problemDetail?.code === 'EMAIL_ALREADY_EXISTS') {
          setError('errors.accountExists');
        } else {
          setError('errors.genericError');
        }
      } else {
        setError('errors.genericError');
      }
      throw err;
    } finally {
      setLoading(false);
    }
  };

  return { register, loading, error };
}
