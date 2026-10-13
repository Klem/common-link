'use client';

import type { ReactNode } from 'react';
import { useTranslations } from 'next-intl';

const strong = (chunks: ReactNode) => <strong>{chunks}</strong>;

/** Settings tabs screen 6 links to; values are the page's `SettingsTab` ids. */
export const CollectionTab = {
  WIDGET: 'widget',
  LANDING: 'landing',
} as const;
export type CollectionTab = typeof CollectionTab[keyof typeof CollectionTab];

interface ActiveScreenProps {
  /** The page's `bankDone` (COMPLETED and payments enabled): the only thing that opens Widget/Landing. */
  collecting: boolean;
  /** ISO instant of the self-completion attestation, null when never attested. */
  attestedAt: string | null;
  /** The attestation request is in flight, or its current state is still unknown. */
  attestBusy: boolean;
  attestError: boolean;
  onAttest: () => void;
  /** Switches to another settings tab of the page (simple `setActiveTab`). */
  onOpenTab: (tab: CollectionTab) => void;
}

/**
 * Screen 6 "Actif" (mockup `screen-6`): success when the association collects, or the
 * "Encaissement pas encore activé" warning for a COMPLETED account without payments (never
 * "Connecter Mollie" there).
 *
 * The self-completion attestation is journalled server-side and unlocks nothing: the Widget and
 * Landing links follow `collecting` (`bankDone`) only. Once recorded it cannot be withdrawn
 * (append-only journal), so the box stays checked and disabled.
 */
export default function ActiveScreen({ collecting, attestedAt, attestBusy, attestError, onAttest, onOpenTab }: ActiveScreenProps) {
  const t = useTranslations('settings.mollie.onboarding.active');
  const attested = attestedAt !== null;

  const tabs = [
    { id: CollectionTab.WIDGET, icon: '🧩', label: t('tabs.widget') },
    { id: CollectionTab.LANDING, icon: '📄', label: t('tabs.landing') },
  ];

  return (
    <section className="screen visible" aria-labelledby={collecting ? 'mob-s6-title' : undefined}>
      {collecting ? (
        <div className="card card-no-hover">
          <div className="card-body">
            <div className="empty-state mob-active-state">
              <div className="empty-state-icon" aria-hidden="true">🎉</div>
              <h4 id="mob-s6-title">{t('title')}</h4>
              <p>{t('text')}</p>
              <span className="badge badge-success">{t('badge')}</span>
            </div>
          </div>
        </div>
      ) : (
        <div className="alert alert-warning" role="status">
          <span className="alert-icon" aria-hidden="true">⏳</span>
          <div>{t.rich('noPayments', { strong })}</div>
        </div>
      )}

      <div className="card card-no-hover mob-card-spaced">
        <div className="card-body">
          <label className="form-checkbox" htmlFor="mob-attest">
            <input
              id="mob-attest"
              type="checkbox"
              checked={attested}
              disabled={attested || attestBusy}
              onChange={(e) => {
                if (e.target.checked) onAttest();
              }}
            />
            {t('attest.label')}
          </label>
          <p className="form-hint mob-attest-hint">{t('attest.hint')}</p>
          {attestError && <p className="form-hint mob-attest-hint" role="alert">{t('attest.error')}</p>}
        </div>
      </div>

      <div className="mob-tabs-wrap">
        <div className="camp-tabs" role="group" aria-label={t('tabs.aria')}>
          {tabs.map((tab, index) => (
            <button
              key={tab.id}
              type="button"
              className={`camp-tab${collecting ? '' : ' tab-locked'}${collecting && index === 0 ? ' active' : ''}`}
              aria-disabled={!collecting}
              disabled={!collecting}
              onClick={() => collecting && onOpenTab(tab.id)}
            >
              <span aria-hidden="true">{tab.icon}</span> {tab.label}{' '}
              <span className={`badge ${collecting ? 'badge-success' : 'badge-neutral'}`}>
                {collecting ? t('tabs.open') : t('tabs.locked')}
              </span>
            </button>
          ))}
        </div>
        {!collecting && <p className="form-hint mob-tabs-hint">{t('tabs.hint')}</p>}
      </div>
    </section>
  );
}
