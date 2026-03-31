'use client';

import { useEffect, useState } from 'react';
import Link from 'next/link';
import { useLocale, useTranslations } from 'next-intl';
import { isAxiosError } from 'axios';
import api from '@/lib/api';
import { ROUTES } from '@/lib/routes';

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
    <div className="min-h-screen flex items-center justify-center bg-bg px-4">
      <div
        className="w-full max-w-md rounded-xl border border-border bg-bg-2 text-center"
        style={{ padding: 'var(--space-card-p)' }}
      >
        <div className="mb-6 text-5xl">✉️</div>

        <h1 className="mb-3 font-display text-2xl font-semibold text-text">
          {t('title')}
        </h1>
        <p className="mb-2 text-text-2">{t('subtitle')}</p>
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
            className="mb-4 w-full rounded-md border border-border bg-bg-3 py-2 text-sm text-text-2 transition-colors hover:bg-bg-2 disabled:opacity-50"
          >
            {resendState === 'sending' ? t('resending') : t('resend')}
          </button>
        )}

        <Link
          href={`/${locale}${ROUTES.LOGIN}`}
          className="text-sm text-text-2 underline-offset-2 hover:text-text hover:underline"
        >
          {t('backToLogin')}
        </Link>
      </div>
    </div>
  );
}
