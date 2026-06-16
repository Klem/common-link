'use client';

import { useState, useEffect } from 'react';
import { useRouter } from 'next/navigation';
import { useLocale, useTranslations } from 'next-intl';
import { ROUTES } from '@/lib/routes';

const DISMISSED_KEY = 'cl-acc-card-dismissed';

interface CheckItem {
  key: 'kyc' | 'bank';
  done: boolean;
}

interface AccountCompletionCardProps {
  verified: boolean;
  bankConnected: boolean;
}

/**
 * Account-completion banner shown on the association dashboard home page.
 * Hidden when both KYC and bank account are set up, or when dismissed
 * (session-scoped via sessionStorage).
 */
export function AccountCompletionCard({ verified, bankConnected }: AccountCompletionCardProps) {
  const t = useTranslations('dashboard.association.home.accCard');
  const router = useRouter();
  const locale = useLocale();
  const [dismissed, setDismissed] = useState(false);

  useEffect(() => {
    setDismissed(sessionStorage.getItem(DISMISSED_KEY) === '1');
  }, []);

  const checks: CheckItem[] = [
    { key: 'kyc', done: verified },
    { key: 'bank', done: bankConnected },
  ];
  const done = checks.filter((c) => c.done).length;
  const total = checks.length;

  if (done === total || dismissed) return null;

  const isPartial = done >= 1;

  const subText = !verified
    ? t('sub.notVerified')
    : t('sub.bankOnly');

  function getCheckState(check: CheckItem, index: number): 'done' | 'pending' | 'todo' {
    if (check.done) return 'done';
    const prevDone = index === 0 ? true : checks[index - 1].done;
    return prevDone ? 'pending' : 'todo';
  }

  function handleCta(key: 'kyc' | 'bank') {
    const tab = key === 'kyc' ? 'verif' : 'bank';
    router.push(`/${locale}${ROUTES.ASSOCIATION_PROFILE}?tab=${tab}`);
  }

  function handleDismiss() {
    sessionStorage.setItem(DISMISSED_KEY, '1');
    setDismissed(true);
  }

  return (
    <div className={`acc-card${isPartial ? ' partial' : ''}`}>
      <div className="acc-card-head">
        <div>
          <div className="acc-card-title">{t('title')}</div>
          <div className="acc-card-sub">{subText}</div>
        </div>
        <button className="acc-card-close" onClick={handleDismiss} title={t('dismiss')}>
          ×
        </button>
      </div>
      <div className="acc-checks">
        {checks.map((check, i) => {
          const state = getCheckState(check, i);
          const icLabel = state === 'done' ? '✓' : state === 'pending' ? '⏳' : String(i + 1);
          return (
            <div key={check.key} className={`acc-check ${state}`}>
              <div className="acc-check-ic">{icLabel}</div>
              <div className="acc-check-body">
                <div className="acc-check-title">
                  <span aria-hidden="true">{check.key === 'kyc' ? '📋' : '🏦'}</span>{' '}
                  <span>{t(`checks.${check.key}.title`)}</span>
                </div>
                <div className="acc-check-desc">{t(`checks.${check.key}.desc`)}</div>
              </div>
              {state === 'done' && (
                <span className="acc-check-eta">✓ Validée</span>
              )}
              {state === 'pending' && (
                <button className="acc-check-cta" onClick={() => handleCta(check.key)}>
                  {t(`checks.${check.key}.cta`)}
                </button>
              )}
              {state === 'todo' && (
                <span className="acc-check-eta">{t(`checks.${check.key}.eta`)}</span>
              )}
            </div>
          );
        })}
      </div>
    </div>
  );
}
