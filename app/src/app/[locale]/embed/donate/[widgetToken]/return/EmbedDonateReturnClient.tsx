'use client';

import { useEffect, useRef, useState } from 'react';
import { useTranslations } from 'next-intl';
import { getDonationStatus, DonationReturnStatus } from '@/lib/api/public';

const PollState = {
  LOADING: 'LOADING',
  CONFIRMED: 'CONFIRMED',
  PENDING_TIMEOUT: 'PENDING_TIMEOUT',
  FAILED: 'FAILED',
} as const;
type PollState = (typeof PollState)[keyof typeof PollState];

const POLL_INTERVAL_MS = 3000;
const POLL_MAX_ATTEMPTS = 5;

interface Props {
  paymentId: string | null;
  widgetToken: string;
  locale: string;
  /** Override poll interval for testing only. */
  _pollIntervalMs?: number;
}

export function EmbedDonateReturnClient({ paymentId, widgetToken, locale, _pollIntervalMs }: Props) {
  const t = useTranslations('widget.return');
  const [pollState, setPollState] = useState<PollState>(
    paymentId ? PollState.LOADING : PollState.FAILED,
  );

  const embedBaseUrl = `/${locale}/embed/donate/${widgetToken}`;

  useEffect(() => {
    if (!paymentId) return;

    let cancelled = false;
    let attempts = 0;
    const interval = _pollIntervalMs ?? POLL_INTERVAL_MS;

    const poll = async () => {
      if (cancelled) return;
      attempts += 1;

      try {
        const result = await getDonationStatus(paymentId);
        if (cancelled) return;
        if (result.status === DonationReturnStatus.CONFIRMED) {
          setPollState(PollState.CONFIRMED);
          return;
        }
      } catch {
        if (!cancelled) setPollState(PollState.FAILED);
        return;
      }

      if (attempts >= POLL_MAX_ATTEMPTS) {
        if (!cancelled) setPollState(PollState.PENDING_TIMEOUT);
        return;
      }

      setTimeout(poll, interval);
    };

    poll();

    return () => {
      cancelled = true;
    };
  }, [paymentId, _pollIntervalMs]);

  return (
    <div className="widget-return">
      {pollState === PollState.LOADING && (
        <div className="widget-return-loading" aria-live="polite">
          <p>{t('loading')}</p>
        </div>
      )}

      {pollState === PollState.CONFIRMED && (
        <div className="widget-return-success" aria-live="polite">
          <h2>{t('confirmed.title')}</h2>
          <p>{t('confirmed.message')}</p>
          <a href={embedBaseUrl} className="btn btn-primary">
            {t('retry')}
          </a>
        </div>
      )}

      {pollState === PollState.PENDING_TIMEOUT && (
        <div className="widget-return-pending" aria-live="polite">
          <h2>{t('pending.title')}</h2>
          <p>{t('pending.message')}</p>
          <a href={embedBaseUrl} className="btn btn-secondary">
            {t('retry')}
          </a>
        </div>
      )}

      {pollState === PollState.FAILED && (
        <div className="widget-return-failed" aria-live="polite">
          <h2>{t('failed.title')}</h2>
          <p>{t('failed.message')}</p>
          <a href={embedBaseUrl} className="btn btn-secondary">
            {t('retry')}
          </a>
        </div>
      )}
    </div>
  );
}
