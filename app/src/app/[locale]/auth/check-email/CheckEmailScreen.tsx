'use client';

import { useEffect, useState } from 'react';
import Link from 'next/link';
import { useLocale, useTranslations } from 'next-intl';
import { isAxiosError } from 'axios';
import api from '@/lib/api';
import { ROUTES } from '@/lib/routes';
import { AuthCard } from '@/components/auth/AuthCard';

export function CheckEmailScreen() {
  const t = useTranslations('auth.checkEmail');
  const locale = useLocale();
  const [pendingEmail, setPendingEmail] = useState('');
  const [resendState, setResendState] = useState<'idle' | 'sending' | 'sent'>('idle');

  useEffect(() => {
    const stored = sessionStorage.getItem('cl-pending-email');
    if (stored) setPendingEmail(stored);
  }, []);

  async function handleResend() {
    if (!pendingEmail) return;
    setResendState('sending');
    try {
      await api.post('/api/auth/resend-verification', { email: pendingEmail });
    } catch (err: unknown) {
      if (!isAxiosError(err) || err.response?.status !== 429) {
        // non-rate-limit errors are silently swallowed (endpoint is idempotent)
      }
    }
    setResendState('sent');
  }

  return (
    <div className="min-h-screen flex items-center justify-center px-4 py-12">
      <AuthCard className="max-w-[480px] w-full text-center">
        <div className="mb-6 flex justify-center">
          <svg width="48" height="48" viewBox="0 0 24 24" fill="none" stroke="var(--color-teal)" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round">
            <rect x="2" y="4" width="20" height="16" rx="2" />
            <path d="m22 7-8.97 5.7a1.94 1.94 0 0 1-2.06 0L2 7" />
          </svg>
        </div>

        <h1 className="mb-3 font-display font-black text-2xl text-text">
          {t('title')}
        </h1>
        <p className="mb-2 text-text-2 text-base">{t('subtitle')}</p>
        {pendingEmail && (
          <p className="mb-2 text-base">
            <span className="font-semibold text-text">{pendingEmail}</span>
          </p>
        )}
        <p className="mb-8 text-sm text-muted">{t('validFor')}</p>

        <div className="mb-6 rounded-lg border border-border bg-bg-3 p-4 text-sm text-text-2">
          <p className="mb-2">{t('noEmail')}</p>
          <p className="text-muted">{t('checkSpam')}</p>
        </div>

        {resendState === 'sent' ? (
          <p className="mb-4 text-sm text-green">{t('resent')}</p>
        ) : (
          <button
            onClick={handleResend}
            disabled={resendState === 'sending' || !pendingEmail}
            className="btn btn-primary btn-md w-full mb-4"
          >
            {resendState === 'sending' ? (
              <span className="flex items-center gap-2 justify-center">
                <span className="animate-spin-around w-4 h-4 border-2 border-current border-t-transparent rounded-full" />
                {t('resending')}
              </span>
            ) : t('resend')}
          </button>
        )}

        <Link
          href={`/${locale}${ROUTES.LOGIN}`}
          className="btn btn-ghost btn-sm"
        >
          {t('backToLogin')}
        </Link>
      </AuthCard>
    </div>
  );
}
