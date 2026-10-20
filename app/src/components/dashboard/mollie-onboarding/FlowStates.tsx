'use client';

import type { ReactNode } from 'react';
import { useTranslations } from 'next-intl';
import { VISIO_BOOKING_URL } from './onboardingFlow';

const strong = (chunks: ReactNode) => <strong>{chunks}</strong>;

/**
 * Loading state of the bank tab (mockup `view-loading`): skeleton of the stepper and of a screen,
 * shown while the Mollie status is read from our API. Replaces the flow.
 */
export function LoadingState() {
  const t = useTranslations('settings.mollie.onboarding.loading');

  return (
    <div aria-busy="true" aria-label={t('aria')}>
      <div className="skel mob-skel-stepper" />
      <div className="card card-no-hover">
        <div className="card-body">
          <div className="skel mob-skel-title" />
          <div className="skel mob-skel-line" />
          <div className="skel mob-skel-line-short" />
          <div className="grid-3">
            <div className="skel mob-skel-block" />
            <div className="skel mob-skel-block" />
            <div className="skel mob-skel-block" />
          </div>
        </div>
      </div>
      <p className="sr-only">{t('text')}</p>
    </div>
  );
}

interface BrokenStateProps {
  /** Re-opens the Mollie connection modal (same OAuth flow as the first connection). */
  onReconnect: () => void;
}

/**
 * "Reconnexion requise" state (mockup `view-broken`): the OAuth link to Mollie was revoked or
 * expired, so the status can no longer be refreshed. Replaces the flow.
 */
export function BrokenState({ onReconnect }: BrokenStateProps) {
  const t = useTranslations('settings.mollie.onboarding.broken');

  return (
    <div className="card card-no-hover">
      <div className="card-body">
        <div className="empty-state">
          <div className="empty-state-icon" aria-hidden="true">🔌</div>
          <h4>{t('title')}</h4>
          <p>{t('text')}</p>
          <div className="row mob-row-center">
            <button type="button" className="btn btn-primary" onClick={onReconnect}>
              {t('reconnect')}
            </button>
            <a className="btn btn-ghost" href={VISIO_BOOKING_URL} target="_blank" rel="noopener noreferrer">
              {t('help')}
            </a>
          </div>
          <p className="form-hint mob-broken-hint">{t.rich('hint', { strong })}</p>
        </div>
      </div>
    </div>
  );
}

/**
 * Persistent help card of the flow (mockup "Un doute ? Faites-le à deux."), shown under every
 * screen 0-6. The booking link is the public visio page of decision R4.
 */
export function HelpCard() {
  const t = useTranslations('settings.mollie.onboarding.help');

  return (
    <div className="card card-no-hover mob-help-card">
      <div className="card-body mob-help-body">
        <div className="help-card">
          <div className="expense-icon" aria-hidden="true">🤝</div>
          <div className="mob-help-text">
            <strong className="small">{t('title')}</strong>
            <div className="small muted">{t('text')}</div>
          </div>
          <a className="btn btn-secondary btn-sm" href={VISIO_BOOKING_URL} target="_blank" rel="noopener noreferrer">
            {t('book')}
          </a>
        </div>
      </div>
    </div>
  );
}
