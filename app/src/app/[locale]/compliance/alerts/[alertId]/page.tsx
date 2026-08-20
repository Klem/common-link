'use client';

import { useState, useEffect, use } from 'react';
import { useLocale, useTranslations } from 'next-intl';
import Link from 'next/link';
import { getAlert, takeInCharge, closeAlert } from '@/lib/api/compliance';
import type { ComplianceAlertDetailDto } from '@/types/compliance';
import { ComplianceAlertDecision } from '@/types/compliance';
import { FreezeMatchEvidence } from '@/components/compliance/FreezeMatchEvidence';
import { PriorDecisionsBanner } from '@/components/compliance/PriorDecisionsBanner';
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

/**
 * Reads one field out of a journal entry payload.
 *
 * The payload was already being sent to the browser and never rendered, so the officer saw a
 * screening history reduced to a sequence number and an event name — the count, score, threshold
 * and register version it carries were dropped on the floor. Shapes differ per event type
 * (a CLEAR has no `topScore`, an UNAVAILABLE has only `reason`), hence the tolerant lookup.
 */
function payloadText(payload: Record<string, unknown>, key: string): string {
  const value = payload?.[key];
  if (value === null || value === undefined) return '—';
  return String(value);
}

/** Same as {@link payloadText}, rounded — raw Jaro-Winkler scores carry 16 decimals. */
function payloadScore(payload: Record<string, unknown>, key: string): string {
  const value = payload?.[key];
  if (typeof value !== 'number') return '—';
  return value.toFixed(4);
}

/**
 * Extracts the RFC-7807 `detail` from a failed request, or null when there is none.
 *
 * Without this the officer read "Request failed with status code 422" — axios's own message — while
 * the backend was returning the actionable reason ("La traçabilité de la notification à la DG Trésor
 * est obligatoire…"). The server messages are already in French and are the ones worth showing.
 */
function problemDetail(err: unknown): string | null {
  if (!err || typeof err !== 'object' || !('response' in err)) return null;
  const data = (err as { response?: { data?: unknown } }).response?.data;
  if (!data || typeof data !== 'object' || !('detail' in data)) return null;
  const detail = (data as { detail?: unknown }).detail;
  return typeof detail === 'string' && detail.trim() ? detail : null;
}

/**
 * Converts an `<input type="datetime-local">` value to an ISO-8601 instant.
 *
 * The input yields local wall-clock with no seconds and no zone (`2026-08-17T11:59`), which the
 * backend `Instant` cannot deserialise — it answered HTTP 400 before any business validation ran,
 * so a `SUSPICIOUS` closure was impossible to record. The value is interpreted in the officer's own
 * timezone, which is the intended reading of "when the DG Trésor was notified".
 *
 * Returns `undefined` for an empty field: the backend treats the whole treasury block as optional
 * except for a SUSPICIOUS decision, where it answers 422.
 */
function toInstant(datetimeLocal: string): string | undefined {
  if (!datetimeLocal) return undefined;
  const parsed = new Date(datetimeLocal);
  if (Number.isNaN(parsed.getTime())) return undefined;
  return parsed.toISOString();
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
        treasuryNotifiedAt: toInstant(treasuryNotifiedAt),
        treasuryNotificationMethod: treasuryMethod || undefined,
        treasuryNotificationRef: treasuryRef || undefined,
      });
      const updated = await getAlert(alertId);
      setAlert(updated);
    } catch (err: unknown) {
      setSubmitError(problemDetail(err) ?? t('detail.error'));
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
          <div>
            <span className="cm-label">{t('detail.subject')}</span>
            <strong>{alert.subjectLabel ?? t('evidence.subjectUnresolved')}</strong>
          </div>
          <div>
            <span className="cm-label">{t('detail.subjectType')}</span>
            <span>{t(`alerts.subjectType.${alert.subjectType}`)}</span>
          </div>
          <div>
            <span className="cm-label">{t('detail.origin')}</span>
            <span style={{ fontFamily: 'monospace' }}>{t(`alerts.origin.${alert.origin}`)}</span>
          </div>
          <div>
            <span className="cm-label">{t('detail.severity')}</span>
            <span>{t(`alerts.severity.${alert.severity}`)}</span>
          </div>
          <div>
            <span className="cm-label">{t('detail.status')}</span>
            <span>{t(`alerts.status.${alert.status}`)}</span>
          </div>
          <div>
            <span className="cm-label">{t('detail.createdAt')}</span>
            <span>{formatDateTime(alert.createdAt, locale)}</span>
          </div>
          <div>
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
            <div>
              <span className="cm-label">{t('detail.takenInChargeAt')}</span>
              <span>{formatDateTime(alert.takenInChargeAt, locale)}</span>
            </div>
          )}
          {alert.takenInChargeByLabel && (
            <div>
              <span className="cm-label">{t('detail.takenInChargeBy')}</span>
              <span>{alert.takenInChargeByLabel}</span>
            </div>
          )}
        </div>
      </div>

      <PriorDecisionsBanner priorDecisions={alert.priorDecisions} locale={locale} />

      <FreezeMatchEvidence
        matches={alert.matches}
        subjectLabel={alert.subjectLabel}
        subjectId={alert.subjectId}
        subjectRegistry={alert.subjectRegistry}
        locale={locale}
      />

      {/* Freeze-screening history */}
      <div className="cm-card" style={{ marginBottom: 24 }}>
        <div className="cm-card-title">{t('detail.history.title')}</div>
        {alert.freezeHistory.length === 0 ? (
          <p style={{ color: 'var(--color-text-2)', marginTop: 12 }}>{t('detail.history.empty')}</p>
        ) : (
          <div style={{ overflowX: 'auto', marginTop: 12 }}>
            <table className="cm-table">
              <thead>
                <tr>
                  <th>{t('detail.history.col.seq')}</th>
                  <th>{t('detail.history.col.event')}</th>
                  <th>{t('detail.history.col.matchCount')}</th>
                  <th>{t('detail.history.col.topScore')}</th>
                  <th>{t('detail.history.col.threshold')}</th>
                  <th>{t('detail.history.col.registryDate')}</th>
                  <th>{t('detail.history.col.reason')}</th>
                  <th>{t('detail.history.col.date')}</th>
                </tr>
              </thead>
              <tbody>
                {alert.freezeHistory.map((entry) => (
                  <tr key={entry.sequenceNo}>
                    <td style={{ fontFamily: 'monospace', fontSize: 12 }}>#{entry.sequenceNo}</td>
                    <td style={{ fontFamily: 'monospace', fontSize: 13 }}>{entry.eventType}</td>
                    <td style={{ fontFamily: 'monospace' }}>{payloadText(entry.payload, 'matchCount')}</td>
                    <td style={{ fontFamily: 'monospace' }}>{payloadScore(entry.payload, 'topScore')}</td>
                    <td style={{ fontFamily: 'monospace' }}>{payloadText(entry.payload, 'scoreThreshold')}</td>
                    <td>{payloadText(entry.payload, 'registryPublicationDate')}</td>
                    <td style={{ fontSize: 12 }}>{payloadText(entry.payload, 'reason')}</td>
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
            <div className="fg">
              <label className="cm-label" htmlFor="decision">{t('detail.decision.label')}</label>
              <select
                id="decision"
                className="fi"
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
            <div className="fg">
              <label className="cm-label" htmlFor="rationale">{t('detail.rationale.label')}</label>
              <textarea
                id="rationale"
                className="fi"
                rows={4}
                value={rationale}
                onChange={(e) => setRationale(e.target.value)}
                placeholder={t('detail.rationale.placeholder')}
                required
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

              <div className="fg">
                <label className="cm-label" htmlFor="treasuryNotifiedAt">
                  {t('detail.treasury.notifiedAt')}{isSuspicious ? ' *' : ''}
                </label>
                <input
                  id="treasuryNotifiedAt"
                  className="fi"
                  type="datetime-local"
                  value={treasuryNotifiedAt}
                  onChange={(e) => setTreasuryNotifiedAt(e.target.value)}
                  required={isSuspicious}
                />
              </div>

              <div className="fg">
                <label className="cm-label" htmlFor="treasuryMethod">
                  {t('detail.treasury.method')}{isSuspicious ? ' *' : ''}
                </label>
                <input
                  id="treasuryMethod"
                  className="fi"
                  type="text"
                  value={treasuryMethod}
                  onChange={(e) => setTreasuryMethod(e.target.value)}
                  placeholder={t('detail.treasury.methodPlaceholder')}
                  required={isSuspicious}
                />
              </div>

              <div className="fg" style={{ marginBottom: 0 }}>
                <label className="cm-label" htmlFor="treasuryRef">
                  {t('detail.treasury.ref')}{isSuspicious ? ' *' : ''}
                </label>
                <input
                  id="treasuryRef"
                  className="fi"
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
            <div>
              <span className="cm-label">{t('detail.closed.decision')}</span>
              <strong>{alert.decision ? t(`detail.decision.${alert.decision}`) : '—'}</strong>
            </div>
          </div>
          <div style={{ marginTop: 12 }}>
            <span className="cm-label">{t('detail.closed.rationale')}</span>
            <p style={{ whiteSpace: 'pre-wrap' }}>{alert.decisionRationale}</p>
          </div>
          {alert.treasuryNotifiedAt && (
            <div style={{ marginTop: 16, background: 'var(--color-bg-3)', borderRadius: 8, padding: 12 }}>
              <p className="cm-label" style={{ marginBottom: 8 }}>{t('detail.treasury.section')}</p>
              <div className="frow" style={{ flexWrap: 'wrap', gap: 12 }}>
                <div>
                  <span className="cm-label">{t('detail.treasury.notifiedAt')}</span>
                  <span>{formatDateTime(alert.treasuryNotifiedAt, locale)}</span>
                </div>
                <div>
                  <span className="cm-label">{t('detail.treasury.method')}</span>
                  <span>{alert.treasuryNotificationMethod}</span>
                </div>
                <div>
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
