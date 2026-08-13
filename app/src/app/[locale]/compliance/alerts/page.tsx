'use client';

import { useState, useEffect, useCallback } from 'react';
import { useLocale, useTranslations } from 'next-intl';
import Link from 'next/link';
import { listAlerts } from '@/lib/api/compliance';
import type { ComplianceAlertSummaryDto } from '@/types/compliance';
import type { Page } from '@/types/payment';
import { ROUTES } from '@/lib/routes';

const SEVERITY_BADGE: Record<string, string> = {
  HIGH: 'badge-danger',
  MEDIUM: 'badge-warning',
  LOW: 'badge-info',
};

const STATUS_BADGE: Record<string, string> = {
  PENDING: 'badge-warning',
  IN_REVIEW: 'badge-info',
  CLOSED: 'badge-success',
};

function formatAge(ageSeconds: number, t: (key: string, values?: Record<string, string | number>) => string): string {
  const days = Math.floor(ageSeconds / 86400);
  const hours = Math.floor((ageSeconds % 86400) / 3600);
  const minutes = Math.floor((ageSeconds % 3600) / 60);
  if (days > 0) return t('alerts.ageFormat.daysHours', { days, hours });
  if (hours > 0) return t('alerts.ageFormat.hoursMinutes', { hours, minutes });
  return t('alerts.ageFormat.minutes', { minutes });
}

export default function AlertsPage() {
  const locale = useLocale();
  const t = useTranslations('compliance');

  const [currentPage, setCurrentPage] = useState(0);
  const [data, setData] = useState<Page<ComplianceAlertSummaryDto> | null>(null);
  const [isLoading, setIsLoading] = useState(true);
  const [hasError, setHasError] = useState(false);

  const loadData = useCallback(async () => {
    setIsLoading(true);
    setHasError(false);
    try {
      const result = await listAlerts(currentPage);
      setData(result);
    } catch {
      setHasError(true);
    } finally {
      setIsLoading(false);
    }
  }, [currentPage]);

  useEffect(() => {
    loadData();
  }, [loadData]);

  return (
    <div className="page">
      <div className="page-head">
        <div>
          <h1>{t('alerts.title')}</h1>
        </div>
      </div>

      {isLoading && <p className="camp-loading">{t('alerts.loading')}</p>}

      {hasError && !isLoading && (
        <p style={{ color: 'var(--color-error)', marginTop: 16 }}>{t('alerts.error')}</p>
      )}

      {!isLoading && !hasError && data && (
        <>
          {data.content.length === 0 ? (
            <p style={{ color: 'var(--color-text-2)', marginTop: 24 }}>{t('alerts.empty')}</p>
          ) : (
            <div style={{ overflowX: 'auto', marginTop: 16 }}>
              <table className="cm-table">
                <thead>
                  <tr>
                    <th>{t('alerts.col.subject')}</th>
                    <th>{t('alerts.col.origin')}</th>
                    <th>{t('alerts.col.severity')}</th>
                    <th>{t('alerts.col.status')}</th>
                    <th>{t('alerts.col.age')}</th>
                    <th>{t('alerts.col.actions')}</th>
                  </tr>
                </thead>
                <tbody>
                  {data.content.map((row) => (
                    <tr key={row.id}>
                      <td>
                        <strong>{row.subjectLabel ?? t('evidence.subjectUnresolved')}</strong>
                        <div style={{ fontSize: 12, color: 'var(--color-text-2)' }}>
                          {t(`alerts.subjectType.${row.subjectType}`)}
                        </div>
                      </td>
                      <td>
                        <span style={{ fontFamily: 'monospace', fontSize: 13 }}>
                          {t(`alerts.origin.${row.origin}`)}
                        </span>
                      </td>
                      <td>
                        <span className={`badge ${SEVERITY_BADGE[row.severity] ?? ''}`}>
                          {t(`alerts.severity.${row.severity}`)}
                        </span>
                      </td>
                      <td>
                        <span className={`badge ${STATUS_BADGE[row.status] ?? ''}`}>
                          {t(`alerts.status.${row.status}`)}
                        </span>
                      </td>
                      <td>
                        <strong
                          style={{
                            color: row.status !== 'CLOSED' && row.ageSeconds > 86400
                              ? 'var(--warm-coral)'
                              : undefined,
                          }}
                        >
                          {formatAge(row.ageSeconds, t)}
                        </strong>
                      </td>
                      <td>
                        <Link
                          href={`/${locale}${ROUTES.compliance.alertDetail(row.id)}`}
                          className="btn btn-sm btn-secondary"
                        >
                          {t('alerts.actions.view')}
                        </Link>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}

          {data.totalPages > 1 && (
            <div style={{ display: 'flex', alignItems: 'center', gap: 12, marginTop: 20 }}>
              <button
                className="btn btn-sm btn-secondary"
                disabled={data.first}
                onClick={() => setCurrentPage((p) => p - 1)}
              >
                ←
              </button>
              <span style={{ fontSize: 14, color: 'var(--color-text-2)' }}>
                {t('alerts.pagination.pageOf', {
                  current: data.number + 1,
                  total: data.totalPages,
                })}
              </span>
              <button
                className="btn btn-sm btn-secondary"
                disabled={data.last}
                onClick={() => setCurrentPage((p) => p + 1)}
              >
                →
              </button>
            </div>
          )}
        </>
      )}
    </div>
  );
}
