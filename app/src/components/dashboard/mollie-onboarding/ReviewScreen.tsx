'use client';

import type { ReactNode } from 'react';
import { useTranslations } from 'next-intl';

const strong = (chunks: ReactNode) => <strong>{chunks}</strong>;

interface ReviewScreenProps {
  /** Mollie sent the dossier back (NEEDS_DATA after an observed IN_REVIEW, server-side `complementRequested`). */
  complementRequested: boolean;
  /** Local "J'ai fait mon paiement test" acknowledgement; drives the "Paiement test" timeline item only. */
  testAcknowledged: boolean;
  /** Mollie hosted onboarding link, opened by "Compléter mon dossier". */
  dashboardUrl: string | null;
  onCompleteFile: () => void;
}

/**
 * Screen 5 "Vérification de votre dossier" (mockup `screen-5`), driven by the server status:
 * IN_REVIEW, or NEEDS_DATA with `complementRequested` (alert "Complément demandé" and
 * "Compléter mon dossier"). The "Paiement test" item reflects the association's own
 * acknowledgement, never a detected event.
 */
export default function ReviewScreen({ complementRequested, testAcknowledged, dashboardUrl, onCompleteFile }: ReviewScreenProps) {
  const t = useTranslations('settings.mollie.onboarding.review');

  return (
    <section className="screen visible" aria-labelledby="mob-s5-title">
      <div className="row-between mob-title-row">
        <h2 id="mob-s5-title" className="mob-kit-title">{t('title')}</h2>
        <span className={`badge ${complementRequested ? 'badge-warning' : 'badge-info'}`}>
          {complementRequested ? t('badgeComplement') : t('badgeInReview')}
        </span>
      </div>
      <p className="lead mob-lead-spaced">{t('lead')}</p>

      {complementRequested && (
        <div className="alert alert-warning mob-alert-bottom-lg" role="alert">
          <span className="alert-icon" aria-hidden="true">📩</span>
          <div className="mob-warn-body">
            {t.rich('complement.text', { strong })}
            <div className="mob-warn-actions">
              <button type="button" className="btn btn-primary btn-sm" onClick={onCompleteFile} disabled={!dashboardUrl}>
                {t('complement.action')}
              </button>
            </div>
          </div>
        </div>
      )}

      <div className="card card-no-hover">
        <div className="card-body">
          <div className="timeline">
            <div className="timeline-item">
              <div className="timeline-dot done" aria-hidden="true">✓</div>
              <h4>{t('timeline.connected.title')}</h4>
              <p>{t('timeline.connected.text')}</p>
            </div>
            <div className="timeline-item">
              <div className="timeline-dot done" aria-hidden="true">✓</div>
              <h4>{t('timeline.sent.title')}</h4>
              <p>{t('timeline.sent.text')}</p>
            </div>
            <div className="timeline-item">
              <div className={`timeline-dot ${testAcknowledged ? 'done' : 'pending'}`} aria-hidden="true">
                {testAcknowledged ? '✓' : ''}
              </div>
              <h4>{t('timeline.test.title')}</h4>
              <p>{testAcknowledged ? t('timeline.test.acked') : t('timeline.test.todo')}</p>
            </div>
            <div className="timeline-item">
              <div className={`timeline-dot ${complementRequested ? 'error' : 'current'}`} aria-hidden="true">
                {complementRequested ? '!' : ''}
              </div>
              <h4>{t('timeline.review.title')}</h4>
              <p>{complementRequested ? t('timeline.review.complement') : t('timeline.review.text')}</p>
            </div>
            <div className="timeline-item">
              <div className="timeline-dot pending" aria-hidden="true" />
              <h4>{t('timeline.active.title')}</h4>
              <p>{t('timeline.active.text')}</p>
            </div>
          </div>
        </div>
      </div>

      <div className="alert alert-info mob-alert-top">
        <span className="alert-icon" aria-hidden="true">✉️</span>
        <div>{t.rich('emailNotice', { strong })}</div>
      </div>
    </section>
  );
}
