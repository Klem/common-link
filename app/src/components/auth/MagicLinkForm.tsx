'use client';

import { useState } from 'react';
import { useTranslations } from 'next-intl';
import { UserRole } from '@/types/auth';
type MagicLinkState = 'idle' | 'sending' | 'sent' | 'error';

interface MagicLinkFormProps {
  onSubmit: (email: string) => Promise<void> | void;
  role: UserRole;
}

export function MagicLinkForm({ onSubmit, role }: MagicLinkFormProps) {
  const t = useTranslations('auth');
  const [email, setEmail] = useState('');
  const [state, setState] = useState<MagicLinkState>('idle');

  const isValidEmail = /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email);

  const handleSubmit = async () => {
    if (!isValidEmail || state === 'sending') return;
    setState('sending');
    try {
      await onSubmit(email);
      setState('sent');
    } catch {
      setState('error');
    }
  };

  const handleResend = () => {
    setEmail('');
    setState('idle');
  };

  const buttonLabel =
    state === 'sending'
      ? t('magicLink.sending')
      : role === UserRole.ASSOCIATION
        ? t('magicLink.sendButton')
        : t('signup.donor.magicLink');

  return (
    <div className="rounded-[11px] p-[16px_18px] bg-green/[.04] border border-green/[.16]">
      <div className="flex items-center gap-[7px] text-[12px] font-bold text-green mb-3 uppercase tracking-[0.05em]">
        ✨ {t('magicLink.label')}
      </div>

      {state === 'sent' ? (
        <div className="alert alert-success text-center animate-slide-down-fade">
          <strong>✓ {t('magicLink.sent')}</strong>
          <span className="block text-xs mt-1">
            {t('magicLink.notReceived')}{' '}
            <button type="button" onClick={handleResend} className="btn btn-ghost btn-sm">
              {t('magicLink.resend')}
            </button>
          </span>
        </div>
      ) : (
        <div className="flex flex-col gap-2">
          <input
            type="email"
            value={email}
            onChange={(e) => setEmail(e.target.value)}
            placeholder={t('magicLink.emailPlaceholder')}
            autoComplete="email"
            className="form-input"
          />
          {state === 'error' && (
            <p className="form-error">{t('errors.genericError')}</p>
          )}
          <button
            type="button"
            onClick={handleSubmit}
            disabled={!isValidEmail || state === 'sending'}
            className="btn btn-primary btn-md w-full"
          >
            ✉️ {buttonLabel}
          </button>
        </div>
      )}
    </div>
  );
}
