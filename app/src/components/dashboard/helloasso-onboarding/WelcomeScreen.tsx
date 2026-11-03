'use client';

import { useTranslations } from 'next-intl';

interface WelcomeScreenProps {
  /** "Relier notre compte": the association already has a HelloAsso account → screen 4. */
  onExisting: () => void;
  /** "Commencer": no HelloAsso account yet → screen 3 (and the "journey started" event). */
  onNew: () => void;
}

/** Screen 0 "Recevoir vos dons" (mockup `screen-0`): two entry cards; the shared FAQ follows, rendered by the flow. */
export default function WelcomeScreen({ onExisting, onNew }: WelcomeScreenProps) {
  const t = useTranslations('settings.helloasso.onboarding.welcome');

  return (
    <section className="screen visible" aria-labelledby="hao-s0-title">
      <h2 id="hao-s0-title" className="sr-only">{t('title')}</h2>
      <div className="path-choice" role="group" aria-label={t('groupLabel')}>
        <div className="path-card featured">
          <span className="badge badge-active hao-self-start">{t('existing.badge')}</span>
          <h3>{t('existing.title')}</h3>
          <p>{t('existing.text')}</p>
          <button type="button" className="btn btn-primary" onClick={onExisting}>
            {t('existing.cta')}
          </button>
        </div>
        <div className="path-card">
          <span className="badge badge-neutral hao-self-start">{t('new.badge')}</span>
          <h3>{t('new.title')}</h3>
          <p>{t('new.text')}</p>
          <button type="button" className="btn btn-secondary" onClick={onNew}>
            {t('new.cta')}
          </button>
        </div>
      </div>
    </section>
  );
}
