'use client';

import type { ReactNode } from 'react';
import { useTranslations } from 'next-intl';

const strong = (chunks: ReactNode) => <strong>{chunks}</strong>;

interface TestPaymentScreenProps {
  /** Mollie hosted dashboard link; the "open my Mollie account" button is hidden without it. */
  dashboardUrl: string | null;
  /** The association said it made its test payment — local UX acknowledgement, never a server state. */
  acknowledged: boolean;
  onAcknowledge: () => void;
  onBack: () => void;
}

/**
 * Screen 4 "Paiement test de 0,01 €" (mockup `screen-4`): why and how the association makes a
 * 0.01 € payment itself in its own Mollie account. The money stays on the association's Mollie
 * balance and the review starts automatically (Mollie's written answer of 9 October 2026, D5).
 *
 * The test payment is an instruction, not a detected state: "J'ai fait mon paiement test" is a
 * local acknowledgement only. It changes no server status and the screen stays the same until
 * Mollie itself moves the dossier to review. The wording never says the payment is free.
 *
 * TODO(R7): delivered without field validation of D5 — the Saint-Julien test payment has not been
 * confirmed. Revisit this screen (and the review trigger it announces) as soon as the field
 * feedback arrives.
 */
export default function TestPaymentScreen({ dashboardUrl, acknowledged, onAcknowledge, onBack }: TestPaymentScreenProps) {
  const t = useTranslations('settings.mollie.onboarding.testPayment');

  return (
    <section className="screen visible" aria-labelledby="mob-s4-title">
      <h2 id="mob-s4-title" className="mob-screen-title">{t('title')}</h2>
      <p className="lead mob-lead-spaced">{t('lead')}</p>

      <div className="grid-3 mob-grid-2">
        <div className="card card-no-hover">
          <div className="card-header-bar"><h3>{t('why.title')}</h3></div>
          <div className="card-body">
            <p className="small">{t.rich('why.p1', { strong })}</p>
            <p className="small mob-p-spaced">{t.rich('why.p2', { strong })}</p>
          </div>
        </div>
        <div className="card card-no-hover">
          <div className="card-header-bar">
            <h3>{t('how.title')}</h3>
            <span className="small muted">{t('how.hint')}</span>
          </div>
          <div className="card-body">
            <ol className="ol-steps">
              <li>{t('how.steps.s1')}</li>
              <li>{t('how.steps.s2')}</li>
              <li>{t('how.steps.s3')}</li>
            </ol>
            <p className="small muted mob-how-after">{t('how.after')}</p>
          </div>
        </div>
      </div>

      <div className="alert alert-info mob-alert-top">
        <span className="alert-icon" aria-hidden="true">🔒</span>
        <div>{t.rich('checkAddress', { strong })}</div>
      </div>

      {acknowledged && (
        <div className="alert alert-success mob-alert-top" role="status">
          <span className="alert-icon" aria-hidden="true">✓</span>
          <div>{t('acked')}</div>
        </div>
      )}

      <div className="screen-foot">
        <button type="button" className="btn btn-ghost" onClick={onBack}>
          <span aria-hidden="true">←</span> {t('back')}
        </button>
        <div className="row">
          {dashboardUrl && (
            <a className="btn btn-secondary" href={dashboardUrl} target="_blank" rel="noopener noreferrer">
              {t('openMollie')}
            </a>
          )}
          <button type="button" className="btn btn-primary" onClick={onAcknowledge} disabled={acknowledged}>
            {t('ack')}
          </button>
        </div>
      </div>
    </section>
  );
}
