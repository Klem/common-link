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
import { AuthCard } from '@/components/auth/AuthCard';

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
        setAuth(data.accessToken, data.user);
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
    <div className="min-h-screen flex items-center justify-center px-4 py-12">
      <AuthCard className="max-w-[480px] w-full text-center">
        {state === 'verifying' && (
          <>
            <div className="mb-6 flex justify-center">
              <svg width="48" height="48" viewBox="0 0 24 24" fill="none" stroke="var(--color-teal)" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round">
                <circle cx="12" cy="12" r="10" opacity="0.3" />
                <path d="M12 6v6l4 2" />
              </svg>
            </div>
            <div className="flex items-center gap-2 justify-center text-text-2">
              <span className="animate-spin-around w-4 h-4 border-2 border-current border-t-transparent rounded-full" />
              <p>{t('verifying')}</p>
            </div>
          </>
        )}

        {state === 'success' && (
          <>
            <div className="mb-6 flex justify-center">
              <svg width="48" height="48" viewBox="0 0 24 24" fill="none" stroke="var(--color-teal)" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
                <path d="M20 6 9 17l-5-5" />
              </svg>
            </div>
            <h1 className="mb-3 font-display font-black text-2xl text-text">{t('success')}</h1>
            <p className="text-text-2 text-base">{t('successSubtitle')}</p>
          </>
        )}

        {state === 'failed' && (
          <>
            <div className="mb-6 alert alert-error">
              <span>{t('failed')}</span>
            </div>
            <p className="mb-6 text-text-2 text-base">{t('failedSubtitle')}</p>
            <Link
              href={`/${locale}${ROUTES.CHECK_EMAIL}`}
              className="btn btn-primary btn-md w-full"
            >
              {t('requestNew')}
            </Link>
          </>
        )}
      </AuthCard>
    </div>
  );
}
