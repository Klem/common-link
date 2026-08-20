'use client';

import { useTranslations } from 'next-intl';
import type { PriorDecisionDto } from '@/types/compliance';

interface PriorDecisionsBannerProps {
  priorDecisions: PriorDecisionDto[];
  locale: string;
}

function formatDateTime(iso: string, locale: string): string {
  return new Date(iso).toLocaleString(locale === 'fr' ? 'fr-FR' : 'en-GB', {
    dateStyle: 'medium',
    timeStyle: 'short',
  });
}

/**
 * Shows how the same subject was ruled on before.
 *
 * Closure is irreversible and every fresh correspondence raises a new alert
 * (`docs/legal/E4-traitement-alerte-et-information-tresor.md` §4.1), so an association whose name
 * scores above the threshold re-alerts on every donation. Surfacing the prior ruling spares the
 * officer from re-deriving an identical analysis each time.
 *
 * **Informative only.** Nothing is suppressed and no alert is auto-closed: a whitelist that
 * survived a register change would be an LCB-FT hole, since the same name can be designated
 * tomorrow under a programme that did not exist today. The officer still examines every alert.
 */
export function PriorDecisionsBanner({ priorDecisions, locale }: PriorDecisionsBannerProps) {
  const t = useTranslations('compliance');

  if (priorDecisions.length === 0) return null;

  return (
    <div
      className="cm-card"
      style={{ marginBottom: 24, borderLeft: '3px solid var(--slate-lavender)' }}
    >
      <div className="cm-card-title">{t('priorDecisions.title')}</div>
      <p style={{ fontSize: 13, color: 'var(--color-text-2)', marginTop: 8 }}>
        {t('priorDecisions.note')}
      </p>

      <div style={{ marginTop: 12 }}>
        {priorDecisions.map((d) => (
          <div
            key={d.alertId}
            style={{
              paddingTop: 12,
              paddingBottom: 12,
              borderTop: '1px solid rgba(0,0,0,.06)',
            }}
          >
            <div className="frow" style={{ flexWrap: 'wrap', gap: 16 }}>
              <div>
                <span className="cm-label">{t('priorDecisions.decision')}</span>
                <strong>{d.decision ? t(`detail.decision.${d.decision}`) : '—'}</strong>
              </div>
              <div>
                <span className="cm-label">{t('priorDecisions.origin')}</span>
                <span>{t(`alerts.origin.${d.origin}`)}</span>
              </div>
              <div>
                <span className="cm-label">{t('priorDecisions.date')}</span>
                <span>{formatDateTime(d.createdAt, locale)}</span>
              </div>
            </div>
            {d.decisionRationale && (
              <p style={{ whiteSpace: 'pre-wrap', marginTop: 8, fontSize: 13 }}>
                {d.decisionRationale}
              </p>
            )}
          </div>
        ))}
      </div>
    </div>
  );
}
