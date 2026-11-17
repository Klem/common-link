'use client';

import { useState } from 'react';
import type { ReactNode } from 'react';
import { useTranslations } from 'next-intl';
import { helloAssoVisioUrl } from './helloAssoFlow';

const strong = (chunks: ReactNode) => <strong>{chunks}</strong>;

/**
 * Loading state of the HelloAsso journey (mockup `view-loading`): skeleton of the stepper and of a screen while the
 * payment status is read. Replaces the flow.
 */
export function LoadingState() {
  const t = useTranslations('settings.helloasso.onboarding.loading');

  return (
    <div aria-busy="true" aria-label={t('aria')}>
      <div className="skel hao-skel-stepper" />
      <div className="card card-no-hover">
        <div className="card-body">
          <div className="skel hao-skel-title" />
          <div className="skel hao-skel-line" />
          <div className="skel hao-skel-line-short" />
          <div className="grid-3">
            <div className="skel hao-skel-block" />
            <div className="skel hao-skel-block" />
            <div className="skel hao-skel-block" />
          </div>
        </div>
      </div>
      <p className="sr-only">{t('text')}</p>
    </div>
  );
}

interface BrokenStateProps {
  /** Opens the "Relier" screen in key-replacement mode. */
  onReplaceKey: () => void;
}

/**
 * "Votre accès HelloAsso est à renouveler" (mockup `view-broken`, link BROKEN): HelloAsso refused our access, and
 * since the secret is never kept, only the association can restore it by pasting its key again. Replaces the flow.
 */
export function BrokenState({ onReplaceKey }: BrokenStateProps) {
  const t = useTranslations('settings.helloasso.onboarding.broken');
  const visioUrl = helloAssoVisioUrl();

  return (
    <div className="card card-no-hover">
      <div className="card-body">
        <div className="empty-state">
          <div className="empty-state-icon" aria-hidden="true">🔑</div>
          <h4>{t('title')}</h4>
          <p>{t('text')}</p>
          <p>{t.rich('keep', { strong })}</p>
          <div className="row hao-row-center">
            <button type="button" className="btn btn-primary" onClick={onReplaceKey}>
              {t('replace')}
            </button>
            {visioUrl && (
              <a className="btn btn-ghost" href={visioUrl} target="_blank" rel="noopener noreferrer">
                {t('help')}
              </a>
            )}
          </div>
          <p className="form-hint hao-broken-hint">{t('hint')}</p>
        </div>
      </div>
    </div>
  );
}

/**
 * Persistent help card "Un doute ? Faites-le à deux." with "Réserver une visio" (brief §2.7). The booking URL comes
 * from `NEXT_PUBLIC_HELLOASSO_VISIO_URL`; the card is hidden when it is empty.
 */
export function HelpCard() {
  const t = useTranslations('settings.helloasso.onboarding.help');
  const visioUrl = helloAssoVisioUrl();
  if (!visioUrl) return null;

  return (
    <div className="card card-no-hover hao-help-card">
      <div className="card-body hao-help-body">
        <div className="help-card">
          <div className="expense-icon" aria-hidden="true">🤝</div>
          <div className="hao-grow">
            <strong className="small">{t('title')}</strong>
            <div className="small muted">{t('text')}</div>
          </div>
          <a className="btn btn-secondary btn-sm" href={visioUrl} target="_blank" rel="noopener noreferrer">
            {t('book')}
          </a>
        </div>
      </div>
    </div>
  );
}

interface CopyRowProps {
  /** Visible label ("Adresse"). */
  label: string;
  value: string;
  /** Accessible name of the copy button. */
  copyAria: string;
  copyLabel: string;
  copiedLabel: string;
  className?: string;
}

/** Label + value + "Copier" button (mockup `copy-row`); the button reads "Copié" for 1.5 s after a copy. */
export function CopyRow({ label, value, copyAria, copyLabel, copiedLabel, className }: CopyRowProps) {
  const [copied, setCopied] = useState(false);

  const copy = () => {
    const done = () => {
      setCopied(true);
      setTimeout(() => setCopied(false), 1500);
    };
    if (navigator.clipboard?.writeText) navigator.clipboard.writeText(value).then(done, done);
    else done();
  };

  return (
    <div className={['copy-row', className].filter(Boolean).join(' ')}>
      <span className="copy-row-label">{label}</span>
      <span className="copy-row-value mono">{value}</span>
      <button type="button" className={`copy-btn${copied ? ' copied' : ''}`} onClick={copy} aria-label={copyAria}>
        {copied ? copiedLabel : copyLabel}
      </button>
    </div>
  );
}

interface PathCrumbsProps {
  /** Accessible name of the path. */
  label: string;
  steps: string[];
}

/** HelloAsso menu path "Mon compte › Intégrations et API" (mockup `path-crumbs`). */
export function PathCrumbs({ label, steps }: PathCrumbsProps) {
  return (
    <span className="path-crumbs" aria-label={label}>
      {steps.map((step, index) => (
        <span key={step}>
          {index > 0 && <span className="sep" aria-hidden="true">› </span>}
          {step}
        </span>
      ))}
    </span>
  );
}
