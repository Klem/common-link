'use client';

import { useState, useEffect, use } from 'react';
import { useLocale, useTranslations } from 'next-intl';
import Link from 'next/link';
import { getAlert, takeInCharge, closeAlert } from '@/lib/api/compliance';
import type { ComplianceAlertDetailDto } from '@/types/compliance';
import { ComplianceAlertDecision } from '@/types/compliance';
import { ROUTES } from '@/lib/routes';

interface AlertDetailPageProps {
  params: Promise<{ locale: string; alertId: string }>;
}

function formatAge(ageSeconds: number, t: (key: string, values?: Record<string, string | number>) => string): string {
  const days = Math.floor(ageSeconds / 86400);
  const hours = Math.floor((ageSeconds % 86400) / 3600);
  const minutes = Math.floor((ageSeconds % 3600) / 60);
  if (days > 0) return t('alerts.ageFormat.daysHours', { days, hours });
  if (hours > 0) return t('alerts.ageFormat.hoursMinutes', { hours, minutes });
  return t('alerts.ageFormat.minutes', { minutes });
}

function formatDateTime(iso: string | null, locale: string): string {
  if (!iso) return '—';
  return new Date(iso).toLocaleString(locale === 'fr' ? 'fr-FR' : 'en-GB', {
    dateStyle: 'medium',
    timeStyle: 'short',
  });
}

export default function AlertDetailPage({ params }: AlertDetailPageProps) {
  const { alertId } = use(params);
  const locale = useLocale();
  const t = useTranslations('compliance');

  const [alert, setAlert] = useState<ComplianceAlertDetailDto | null>(null);
  const [isLoading, setIsLoading] = useState(true);
  const [hasError, setHasError] = useState(false);

  const [decision, setDecision] = useState('');
  const [rationale, setRationale] = useState('');
  const [treasuryNotifiedAt, setTreasuryNotifiedAt] = useState('');
  const [treasuryMethod, setTreasuryMethod] = useState('');
  const [treasuryRef, setTreasuryRef] = useState('');
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [submitError, setSubmitError] = useState<string | null>(null);

  useEffect(() => {
    setIsLoading(true);
    setHasError(false);
    getAlert(alertId)
      .then(setAlert)
      .catch(() => setHasError(true))
      .finally(() => setIsLoading(false));
  }, [alertId]);

  const handleTakeInCharge = async () => {
    setIsSubmitting(true);
    setSubmitError(null);
    try {
      await takeInCharge(alertId);
      const updated = await getAlert(alertId);
      setAlert(updated);
    } catch {
      setSubmitError(t('detail.error'));
    } finally {
      setIsSubmitting(false);
    }
  };

  const handleClose = async (e: React.FormEvent) => {
    e.preventDefault();
    setIsSubmitting(true);
    setSubmitError(null);
    try {
      await closeAlert(alertId, {
        decision,
        rationale,
        treasuryNotifiedAt: treasuryNotifiedAt || undefined,
        treasuryNotificationMethod: treasuryMethod || undefined,
        treasuryNotificationRef: treasuryRef || undefined,
      });
      const updated = await getAlert(alertId);
      setAlert(updated);
    } catch (err: unknown) {
      const msg = err instanceof Error ? err.message : t('detail.error');
      setSubmitError(msg);
    } finally {
      setIsSubmitting(false);
    }
  };

  const isSuspicious = decision === ComplianceAlertDecision.SUSPICIOUS;

  if (isLoading) return <div className="page"><p className="camp-loading">{t('detail.loading')}</p></div>;
  if (hasError || !alert) return <div className="page"><p style={{ color: 'var(--color-error)' }}>{t('detail.error')}</p></div>;

  return (
    <div className="page">
      <div className="page-head">
        <div>
          <Link
            href={`/${locale}${ROUTES.compliance.alerts}`}
            style={{ fontSize: 13, color: 'var(--color-text-2)', textDecoration: 'none' }}
          >
            ← {t('detail.backToList')}
          </Link>
          <h1 style={{ marginTop: 8 }}>{t('detail.title')}</h1>
        </div>
      </div>

      {/* Alert metadata */}
      <div className="cm-card" style={{ marginBottom: 24 }}>
        <div className="cm-card-title">{t('detail.metadata')}</div>
        <div className="frow" style={{ flexWrap: 'wrap', gap: 16, marginTop: 12 }}>
          <div className="fi">
            <span className="cm-label">{t('detail.origin')}</span>
            <span style={{ fontFamily: 'monospace' }}>{t(`alerts.origin.${alert.origin}`)}</span>
          </div>
          <div className="fi">
            <span className="cm-label">{t('detail.severity')}</span>
            <span>{t(`alerts.severity.${alert.severity}`)}</span>
          </div>
          <div className="fi">
            <span className="cm-label">{t('detail.status')}</span>
            <span>{t(`alerts.status.${alert.status}`)}</span>
          </div>
          <div className="fi">
            <span className="cm-label">{t('detail.createdAt')}</span>
            <span>{formatDateTime(alert.createdAt, locale)}</span>
          </div>
          <div className="fi">
            <span className="cm-label">{t('detail.age')}</span>
            <strong
              style={{
                color: alert.status !== 'CLOSED' && alert.ageSeconds > 86400
                  ? 'var(--warm-coral)'
                  : undefined,
              }}
            >
              {formatAge(alert.ageSeconds, t)}
            </strong>
          </div>
          {alert.takenInChargeAt && (
            <div className="fi">
              <span className="cm-label">{t('detail.takenInChargeAt')}</span>
              <span>{formatDateTime(alert.takenInChargeAt, locale)}</span>
            </div>
          )}
        </div>
      </div>

      {/* Freeze-screening history */}
      <div className="cm-card" style={{ marginBottom: 24 }}>
        <div className="cm-card-title">{t('detail.history.title')}</div>
        {alert.freezeHistory.length === 0 ? (
          <p style={{ color: 'var(--color-text-2)', marginTop: 12 }}>{t('detail.history.empty')}</p>
        ) : (
          <div style={{ overflowX: 'auto', marginTop: 12 }}>
            <table>
              <thead>
                <tr>
                  <th>{t('detail.history.col.seq')}</th>
                  <th>{t('detail.history.col.event')}</th>
                  <th>{t('detail.history.col.date')}</th>
                </tr>
              </thead>
              <tbody>
                {alert.freezeHistory.map((entry) => (
                  <tr key={entry.sequenceNo}>
                    <td style={{ fontFamily: 'monospace', fontSize: 12 }}>#{entry.sequenceNo}</td>
                    <td style={{ fontFamily: 'monospace', fontSize: 13 }}>{entry.eventType}</td>
                    <td>{formatDateTime(entry.occurredAt, locale)}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </div>

      {/* Decision section */}
      {alert.status === 'PENDING' && (
        <div className="cm-card" style={{ marginBottom: 24 }}>
          <div className="cm-card-title">{t('detail.takeInCharge.title')}</div>
          <p style={{ color: 'var(--color-text-2)', marginTop: 8 }}>{t('detail.takeInCharge.description')}</p>
          {submitError && (
            <p style={{ color: 'var(--color-error)', marginTop: 8 }}>{submitError}</p>
          )}
          <button
            className="btn btn-primary"
            style={{ marginTop: 16 }}
            onClick={handleTakeInCharge}
            disabled={isSubmitting}
          >
            {isSubmitting ? t('detail.submitting') : t('detail.takeInCharge.button')}
          </button>
        </div>
      )}

      {alert.status === 'IN_REVIEW' && (
        <div className="cm-card" style={{ marginBottom: 24 }}>
          <div className="cm-card-title">{t('detail.decision.title')}</div>
          <form onSubmit={handleClose} style={{ marginTop: 12 }}>
            {/* Decision select */}
            <div className="fi" style={{ marginBottom: 16 }}>
              <label className="cm-label" htmlFor="decision">{t('detail.decision.label')}</label>
              <select
                id="decision"
                className="fsel"
                value={decision}
                onChange={(e) => setDecision(e.target.value)}
                required
              >
                <option value="">{t('detail.decision.placeholder')}</option>
                {Object.values(ComplianceAlertDecision).map((d) => (
                  <option key={d} value={d}>{t(`detail.decision.${d}`)}</option>
                ))}
              </select>
            </div>

            {/* Rationale */}
            <div className="fi" style={{ marginBottom: 16 }}>
              <label className="cm-label" htmlFor="rationale">{t('detail.rationale.label')}</label>
              <textarea
                id="rationale"
                rows={4}
                value={rationale}
                onChange={(e) => setRationale(e.target.value)}
                placeholder={t('detail.rationale.placeholder')}
                required
                style={{ width: '100%', resize: 'vertical' }}
              />
            </div>

            {/* DG Trésor section */}
            <div style={{ background: 'var(--color-bg-3)', borderRadius: 8, padding: 16, marginBottom: 16 }}>
              <p className="cm-label" style={{ marginBottom: 8 }}>{t('detail.treasury.section')}</p>
              <p style={{ fontSize: 13, color: 'var(--color-text-2)', marginBottom: 16 }}>
                {t('detail.treasury.note')}
              </p>

              {isSuspicious && (
                <p style={{ fontSize: 12, color: 'var(--warm-coral)', marginBottom: 12 }}>
                  {t('detail.treasury.requiredForSuspicious')}
                </p>
              )}

              <div className="fi" style={{ marginBottom: 12 }}>
                <label className="cm-label" htmlFor="treasuryNotifiedAt">
                  {t('detail.treasury.notifiedAt')}{isSuspicious ? ' *' : ''}
                </label>
                <input
                  id="treasuryNotifiedAt"
                  type="datetime-local"
                  value={treasuryNotifiedAt}
                  onChange={(e) => setTreasuryNotifiedAt(e.target.value)}
                  required={isSuspicious}
                />
              </div>

              <div className="fi" style={{ marginBottom: 12 }}>
                <label className="cm-label" htmlFor="treasuryMethod">
                  {t('detail.treasury.method')}{isSuspicious ? ' *' : ''}
                </label>
                <input
                  id="treasuryMethod"
                  type="text"
                  value={treasuryMethod}
                  onChange={(e) => setTreasuryMethod(e.target.value)}
                  placeholder={t('detail.treasury.methodPlaceholder')}
                  required={isSuspicious}
                />
              </div>

              <div className="fi">
                <label className="cm-label" htmlFor="treasuryRef">
                  {t('detail.treasury.ref')}{isSuspicious ? ' *' : ''}
                </label>
                <input
                  id="treasuryRef"
                  type="text"
                  value={treasuryRef}
                  onChange={(e) => setTreasuryRef(e.target.value)}
                  placeholder={t('detail.treasury.refPlaceholder')}
                  required={isSuspicious}
                />
              </div>
            </div>

            {submitError && (
              <p style={{ color: 'var(--color-error)', marginBottom: 12 }}>{submitError}</p>
            )}

            <button
              type="submit"
              className="btn btn-primary"
              disabled={isSubmitting || !decision || !rationale.trim()}
            >
              {isSubmitting ? t('detail.submitting') : t('detail.submit')}
            </button>
          </form>
        </div>
      )}

      {alert.status === 'CLOSED' && (
        <div className="cm-card">
          <div className="cm-card-title">{t('detail.closed.title')}</div>
          <div className="frow" style={{ flexWrap: 'wrap', gap: 16, marginTop: 12 }}>
            <div className="fi">
              <span className="cm-label">{t('detail.closed.decision')}</span>
              <strong>{alert.decision ? t(`detail.decision.${alert.decision}`) : '—'}</strong>
            </div>
          </div>
          <div className="fi" style={{ marginTop: 12 }}>
            <span className="cm-label">{t('detail.closed.rationale')}</span>
            <p style={{ whiteSpace: 'pre-wrap' }}>{alert.decisionRationale}</p>
          </div>
          {alert.treasuryNotifiedAt && (
            <div style={{ marginTop: 16, background: 'var(--color-bg-3)', borderRadius: 8, padding: 12 }}>
              <p className="cm-label" style={{ marginBottom: 8 }}>{t('detail.treasury.section')}</p>
              <div className="frow" style={{ flexWrap: 'wrap', gap: 12 }}>
                <div className="fi">
                  <span className="cm-label">{t('detail.treasury.notifiedAt')}</span>
                  <span>{formatDateTime(alert.treasuryNotifiedAt, locale)}</span>
                </div>
                <div className="fi">
                  <span className="cm-label">{t('detail.treasury.method')}</span>
                  <span>{alert.treasuryNotificationMethod}</span>
                </div>
                <div className="fi">
                  <span className="cm-label">{t('detail.treasury.ref')}</span>
                  <span style={{ fontFamily: 'monospace' }}>{alert.treasuryNotificationRef}</span>
                </div>
              </div>
            </div>
          )}
        </div>
      )}
    </div>
  );
}
