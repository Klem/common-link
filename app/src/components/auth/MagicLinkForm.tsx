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
    <div
      className="rounded-[11px] p-[16px_18px] bg-green/[.04] border border-green/[.16]"
    >
      <div
        className="flex items-center gap-[7px] text-[12px] font-bold text-green mb-3 uppercase tracking-[0.05em]"
      >
        ✨ {t('magicLink.label')}
      </div>

      {state === 'sent' ? (
        <div
          className="text-center p-3 rounded-[8px] text-[12.5px] text-text-2 bg-green/[.06] border border-green/[.18] animate-slide-down-fade"
        >
          <strong className="text-green">✓ {t('magicLink.sent')}</strong>
          <span className="block text-[11px] text-muted mt-[5px]">
            {t('magicLink.notReceived')}{' '}
            <button
              type="button"
              onClick={handleResend}
              className="text-cyan text-[11px] bg-transparent border-none cursor-pointer p-0 underline-offset-2 hover:underline"
            >
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
            className="w-full bg-bg-3 border border-border text-text px-3 py-[10px] rounded-[8px] font-body text-[13.5px] outline-none transition-[border-color] duration-200 placeholder:text-muted focus:border-green/40"
          />
          {state === 'error' && (
            <p className="text-[11.5px] text-red">{t('errors.genericError')}</p>
          )}
          <button
            type="button"
            onClick={handleSubmit}
            disabled={!isValidEmail || state === 'sending'}
            className="w-full py-[13px] bg-green text-black border-none rounded-md font-display text-[14px] font-bold cursor-pointer transition-all duration-200 tracking-[0.02em] hover:bg-[#00d4b0] hover:-translate-y-px hover:shadow-[0_6px_20px_rgba(0,184,154,.25)] disabled:opacity-[.38] disabled:cursor-not-allowed disabled:translate-y-0 disabled:shadow-none"
          >
            ✉️ {buttonLabel}
          </button>
        </div>
      )}
    </div>
  );
}
