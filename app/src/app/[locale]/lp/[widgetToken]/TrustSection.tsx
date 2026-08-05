'use client';

import { useTranslations } from 'next-intl';

interface Props {
  taxReductionRate: number;
}

export function TrustSection({ taxReductionRate }: Props) {
  const t = useTranslations('landing');

  return (
    <section className="lp-section">
      <div className="lp-container">
        <span className="lp-eyebrow">{t('trust.eyebrow')}</span>
        <h2 className="lp-section-title">{t('trust.title')}</h2>
        <div className="lp-trust-grid">
          <div className="lp-trust-card">
            <div className="lp-trust-icon">🧾</div>
            <h3 className="lp-trust-card-title">{t('trust.receipt.title')}</h3>
            <p className="lp-trust-card-body">{t('trust.receipt.body', { rate: taxReductionRate })}</p>
          </div>
          <div className="lp-trust-card">
            <div className="lp-trust-icon">🔗</div>
            <h3 className="lp-trust-card-title">{t('trust.traceability.title')}</h3>
            <p className="lp-trust-card-body">{t('trust.traceability.body')}</p>
          </div>
        </div>
      </div>
    </section>
  );
}
