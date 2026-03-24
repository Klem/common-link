'use client';

import { useState } from 'react';
import { isAxiosError } from 'axios';
import api from '@/lib/api';

type UserRole = 'ASSOCIATION' | 'DONOR';

interface ProblemDetail {
  code?: string;
  [key: string]: unknown;
}

export function useEmailRegister() {
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [sent, setSent] = useState(false);

  const register = async (email: string, password: string, role: UserRole): Promise<void> => {
    setLoading(true);
    setError(null);
    try {
      await api.post('/api/auth/register', { email, password, role });
      sessionStorage.setItem('cl-pending-email', email);
      setSent(true);
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

  const reset = () => {
    setSent(false);
    setError(null);
  };

  return { register, loading, error, sent, reset };
}
