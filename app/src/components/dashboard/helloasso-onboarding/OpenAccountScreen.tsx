'use client';

import type { ReactNode } from 'react';
import { useTranslations } from 'next-intl';
import { HELLOASSO_SIGNUP_URL } from './helloAssoFlow';

const strong = (chunks: ReactNode) => <strong>{chunks}</strong>;
const bold = (chunks: ReactNode) => <b>{chunks}</b>;

interface OpenAccountScreenProps {
  /** "← Retour" → screen 0. */
  onBack: () => void;
  /** "Notre compte est ouvert, continuer" → screen 4. */
  onContinue: () => void;
}

/**
 * Screen 3 "Ouvrir le compte de paiement de l'association" (mockup `screen-3`, path "new"): the association opens its
 * own HelloAsso account in a new tab (no API creates it in key mode), then comes back to link it.
 */
export default function OpenAccountScreen({ onBack, onContinue }: OpenAccountScreenProps) {
  const t = useTranslations('settings.helloasso.onboarding.open');

  return (
    <section className="screen visible" aria-labelledby="hao-s3-title">
      <div className="row-between hao-title-row">
        <h2 id="hao-s3-title" className="hao-screen-title">{t('title')}</h2>
        <span className="badge badge-neutral">{t('badge')}</span>
      </div>
      <p className="lead hao-lead-spaced">{t('lead')}</p>

      <div className="solo-wrap">
        <aside className="provider-window provider-window-solo" aria-label={t('windowLabel')}>
          <div className="mw-bar">
            <span className="mw-dots" aria-hidden="true"><i /><i /><i /></span>
            <span className="mw-url"><span aria-hidden="true">🔒 </span>{t.rich('domain', { b: bold })}</span>
          </div>
          <div className="mw-body">
            <div className="empty-state-icon" aria-hidden="true">🗂️</div>
            <h4 className="hao-mw-title">{t('cardTitle')}</h4>
            <p className="small muted hao-mw-text">{t('cardText')}</p>
            <a className="btn btn-primary btn-sm" href={HELLOASSO_SIGNUP_URL} target="_blank" rel="noopener noreferrer">
              {t('cta')}
            </a>
            <div className="alert alert-info small hao-mw-alert">
              <span className="alert-icon" aria-hidden="true">🔒</span>
              <div>{t.rich('safety', { strong })}</div>
            </div>
          </div>
        </aside>
      </div>

      <div className="screen-foot">
        <button type="button" className="btn btn-ghost" onClick={onBack}>{t('back')}</button>
        <button type="button" className="btn btn-primary btn-lg" onClick={onContinue}>{t('continue')}</button>
      </div>
    </section>
  );
}
