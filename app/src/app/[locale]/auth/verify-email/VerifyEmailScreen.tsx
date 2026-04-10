'use client';

import { useEffect, useState } from 'react';
import Link from 'next/link';
import { useRouter } from 'next/navigation';
import { useLocale, useTranslations } from 'next-intl';
import { isAxiosError } from 'axios';
import api from '@/lib/api';
import { useAuthStore } from '@/stores/authStore';
import { ROUTES } from '@/lib/routes';
import type { AuthResponseDto } from '@/types/auth';
import { UserRole } from '@/types/auth';

type VerifyState = 'verifying' | 'success' | 'failed';

interface VerifyEmailScreenProps {
  token: string;
}

export function VerifyEmailScreen({ token }: VerifyEmailScreenProps) {
  const t = useTranslations('auth.verifyEmail');
  const locale = useLocale();
  const router = useRouter();
  const { setAuth } = useAuthStore();
  const [state, setState] = useState<VerifyState>('verifying');

  useEffect(() => {
    if (!token) {
      setState('failed');
      return;
    }

    api.post<AuthResponseDto>('/api/auth/verify-email', { token })
      .then(({ data }) => {
        setAuth(data.accessToken, data.refreshToken, data.user);
        sessionStorage.removeItem('cl-pending-email');
        setState('success');
        const dashboard =
          data.user.role === UserRole.ASSOCIATION
            ? ROUTES.ASSOCIATION_DASHBOARD
            : ROUTES.DONOR_DASHBOARD;
        setTimeout(() => router.push(`/${locale}${dashboard}`), 1500);
      })
      .catch((err: unknown) => {
        const status = isAxiosError(err) ? err.response?.status : undefined;
        void status;
        setState('failed');
      });
  // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [token]);

  return (
    <div className="min-h-screen flex items-center justify-center bg-bg px-4">
      <div
        className="w-full max-w-md rounded-xl border border-border bg-bg-2 text-center"
        style={{ padding: 'var(--space-card-p)' }}
      >
        {state === 'verifying' && (
          <>
            <div className="mb-6 text-5xl">⏳</div>
            <p className="text-text-2">{t('verifying')}</p>
          </>
        )}

        {state === 'success' && (
          <>
            <div className="mb-6 text-5xl">✅</div>
            <h1 className="mb-3 font-display text-2xl font-semibold text-green">{t('success')}</h1>
            <p className="text-text-2">{t('successSubtitle')}</p>
          </>
        )}

        {state === 'failed' && (
          <>
            <div className="mb-6 text-5xl">❌</div>
            <h1 className="mb-3 font-display text-2xl font-semibold text-red">{t('failed')}</h1>
            <p className="mb-6 text-text-2">{t('failedSubtitle')}</p>
            <Link
              href={`/${locale}${ROUTES.CHECK_EMAIL}`}
              className="inline-block rounded-md bg-green px-4 py-2 text-sm font-medium text-bg transition-opacity hover:opacity-90"
            >
              {t('requestNew')}
            </Link>
          </>
        )}
      </div>
    </div>
  );
}
