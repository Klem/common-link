'use client';

import { useEffect, useRef, useState } from 'react';
import { useRouter } from 'next/navigation';
import { useLocale } from 'next-intl';
import { isAxiosError } from 'axios';
import api from '@/lib/api';
import { useAuthStore } from '@/stores/authStore';
import type { AuthResponseDto } from '@/types/auth';

interface ProblemDetail {
  code?: string;
  [key: string]: unknown;
}

type VerifyStatus = 'idle' | 'verifying' | 'success' | 'error';

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
