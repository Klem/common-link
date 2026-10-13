'use client';

import { useState } from 'react';
import type { ReactNode } from 'react';
import { useTranslations } from 'next-intl';
import { VISIO_BOOKING_URL } from './onboardingFlow';

/** FAQ entries of screen 0, in mockup order, with their paragraph keys. */
const FAQ_ITEMS = [
  { id: 'whyMollie', paragraphs: ['p1', 'p2', 'p3'] },
  { id: 'documents', paragraphs: ['p1', 'p2'] },
  { id: 'cost', paragraphs: ['p1', 'p2'] },
  { id: 'genuine', paragraphs: ['p1', 'p2'] },
  { id: 'resume', paragraphs: ['p1'] },
  { id: 'help', paragraphs: ['p1', 'p2'] },
] as const;

const strong = (chunks: ReactNode) => <strong>{chunks}</strong>;

interface WelcomeScreenProps {
  /** Mollie hosted onboarding link; "Reprendre mon dossier" is shown only when set (connected, NEEDS_DATA). */
  resumeUrl: string | null;
  /** Shows the "Dossier à compléter" badge (a Mollie onboarding exists and needs data). */
  needsData: boolean;
  onStart: () => void;
  /** Called when "Reprendre mon dossier" is clicked (the link itself opens Mollie in a new tab). */
  onResume?: () => void;
  /** Opens the "Why Mollie?" modal (`MollieInfoModal`); the link under the FAQ is hidden without it. */
  onLearnMore?: () => void;
}

/**
 * Screen 0 "Recevoir vos dons": three static figures and the six-question FAQ of the mockup.
 *
 * The figures are not API data: "environ 20 min" is labelled as an estimate. The trust wording
 * (Mollie licence, documents not passed on to CommonLink) is the imposed D11 wording. FAQ 6 offers
 * the visio booking link (decision R4). A discreet "En savoir plus" link under the FAQ opens the
 * detailed "Why Mollie?" modal (decision R10; not in the mockup).
 */
export default function WelcomeScreen({ resumeUrl, needsData, onStart, onResume, onLearnMore }: WelcomeScreenProps) {
  const t = useTranslations('settings.mollie.onboarding.welcome');
  const tM = useTranslations('settings.mollie');
  const [open, setOpen] = useState<Record<string, boolean>>({});

  return (
    <section className="screen visible" aria-labelledby="mob-s0-title">
      <div className="card card-no-hover">
        <div className="card-body">
          <div className="row-between mob-head-top">
            <div>
              <h2 id="mob-s0-title" className="mob-s0-title">{t('title')}</h2>
              <p className="lead">{t.rich('lead', { strong })}</p>
            </div>
            {needsData && <span className="badge badge-warning">{t('badgeToComplete')}</span>}
          </div>

          <div className="grid-3 mob-figures">
            <div className="stat-card">
              <div className="stat-card-header">
                <div className="stat-card-icon teal" aria-hidden="true">⏱️</div>
                <span className="badge badge-neutral">{t('figures.estimate')}</span>
              </div>
              <div className="stat-card-value">{t('figures.duration.value')}</div>
              <div className="stat-card-label">{t('figures.duration.label')}</div>
            </div>
            <div className="stat-card">
              <div className="stat-card-header">
                <div className="stat-card-icon indigo" aria-hidden="true">💶</div>
              </div>
              <div className="stat-card-value">{t('figures.testPayment.value')}</div>
              <div className="stat-card-label">{t('figures.testPayment.label')}</div>
            </div>
            <div className="stat-card">
              <div className="stat-card-header">
                <div className="stat-card-icon amber" aria-hidden="true">📅</div>
              </div>
              <div className="stat-card-value">{t('figures.review.value')}</div>
              <div className="stat-card-label">{t('figures.review.label')}</div>
            </div>
          </div>
        </div>
      </div>

      <div className="card card-no-hover mob-card-spaced">
        <div className="card-header-bar">
          <h3>{t('faq.title')}</h3>
          <span className="small muted">{t('faq.hint')}</span>
        </div>
        <div className="card-body">
          <div className="acc-list">
            {FAQ_ITEMS.map((item) => {
              const expanded = open[item.id] === true;
              const panelId = `mob-faq-${item.id}`;
              return (
                <div key={item.id} className="acc-item">
                  <button
                    type="button"
                    className="acc-trigger"
                    id={`${panelId}-t`}
                    aria-expanded={expanded}
                    aria-controls={panelId}
                    onClick={() => setOpen((prev) => ({ ...prev, [item.id]: !expanded }))}
                  >
                    {t(`faq.items.${item.id}.question`)}
                    <span className="acc-chev" aria-hidden="true">▾</span>
                  </button>
                  <div className="acc-panel" id={panelId} role="region" aria-labelledby={`${panelId}-t`} hidden={!expanded}>
                    {item.paragraphs.map((p) => (
                      <p key={p}>{t.rich(`faq.items.${item.id}.${p}`, { strong })}</p>
                    ))}
                    {item.id === 'help' && (
                      <a className="btn btn-secondary btn-sm mob-faq-visio" href={VISIO_BOOKING_URL} target="_blank" rel="noopener noreferrer">
                        {t('faq.items.help.book')}
                      </a>
                    )}
                  </div>
                </div>
              );
            })}
          </div>
          {onLearnMore && (
            <div className="mob-learn-more">
              <button type="button" className="btn btn-tertiary btn-sm" onClick={onLearnMore}>
                {tM('learnMore')}
              </button>
            </div>
          )}
        </div>
      </div>

      <div className="screen-foot">
        {resumeUrl ? (
          <a href={resumeUrl} target="_blank" rel="noopener noreferrer" className="btn btn-secondary" onClick={onResume}>
            {t('resume')}
          </a>
        ) : (
          <span />
        )}
        <button type="button" className="btn btn-primary btn-lg" onClick={onStart}>
          {t('start')}
        </button>
      </div>
    </section>
  );
}
