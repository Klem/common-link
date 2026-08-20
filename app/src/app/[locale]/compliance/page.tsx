'use client';

import { useState, useEffect } from 'react';
import { useLocale, useTranslations } from 'next-intl';
import Link from 'next/link';
import { ROUTES } from '@/lib/routes';
import { countOpenAlerts, listRegistryScans, listAuditLog } from '@/lib/api/compliance';
import type { Page } from '@/types/payment';
import type { ComplianceRegistryScanSummaryDto, AuditLogEntryDto } from '@/types/compliance';

export default function ComplianceDashboardPage() {
  const t = useTranslations('compliance');
  const locale = useLocale();

  const [alertTotal, setAlertTotal] = useState<number | null>(null);
  const [alertError, setAlertError] = useState(false);

  const [registryPage, setRegistryPage] = useState(0);
  const [registryData, setRegistryData] = useState<Page<ComplianceRegistryScanSummaryDto> | null>(null);
  const [registryLoading, setRegistryLoading] = useState(true);
  const [registryError, setRegistryError] = useState(false);

  const [auditEntries, setAuditEntries] = useState<AuditLogEntryDto[] | null>(null);
  const [auditError, setAuditError] = useState(false);

  // The tile counts alerts still awaiting treatment. It previously read totalElements from an
  // unfiltered alert page, which also counted CLOSED alerts — a backlog figure that treatment
  // could never bring down.
  useEffect(() => {
    countOpenAlerts()
      .then(setAlertTotal)
      .catch(() => setAlertError(true));
  }, []);

  useEffect(() => {
    setRegistryLoading(true);
    setRegistryError(false);
    listRegistryScans(registryPage, 10)
      .then((d) => setRegistryData(d))
      .catch(() => setRegistryError(true))
      .finally(() => setRegistryLoading(false));
  }, [registryPage]);

  useEffect(() => {
    listAuditLog()
      .then((d) => setAuditEntries(d))
      .catch(() => setAuditError(true));
  }, []);

  return (
    <div className="main-content">
      <h1 className="cm-card-title">{t('dashboard.title')}</h1>

      {/* Panel A — Pending alert count */}
      <div className="cm-card">
        <div className="cm-card-title">{t('dashboard.alertCount.title')}</div>
        {alertError ? (
          <p className="alert-warning">{t('dashboard.alertCount.error')}</p>
        ) : alertTotal === null ? (
          <p>{t('dashboard.alertCount.loading')}</p>
        ) : (
          <p className="stat-card__value">{alertTotal}</p>
        )}
        <Link href={`/${locale}${ROUTES.compliance.alerts}`} className="cm-btn cm-btn-outline">
          {t('dashboard.alertCount.viewAll')}
        </Link>
      </div>

      {/* Panel B — Latest registry scans */}
      <div className="cm-card">
        <div className="cm-card-title">{t('registry.title')}</div>
        {registryLoading ? (
          <p>{t('registry.loading')}</p>
        ) : registryError ? (
          <p className="alert-warning">{t('registry.error')}</p>
        ) : !registryData || registryData.content.length === 0 ? (
          <p>{t('registry.empty')}</p>
        ) : (
          <>
            <div style={{ overflowX: 'auto' }}>
              <table className="cm-table">
                <thead>
                  <tr>
                    <th>{t('registry.col.association')}</th>
                    <th>{t('registry.col.verdict')}</th>
                    <th>{t('registry.col.exists')}</th>
                    <th>{t('registry.col.rna')}</th>
                    <th>{t('registry.col.warnings')}</th>
                    <th>{t('registry.col.date')}</th>
                  </tr>
                </thead>
                <tbody>
                  {registryData.content.map((scan) => (
                    <tr key={scan.associationId}>
                      <td>{scan.associationName}</td>
                      <td>{t(`registry.verdict.${scan.scopeVerdict}`)}</td>
                      <td>
                        {scan.associationExists === null
                          ? t('registry.exists.null')
                          : scan.associationExists
                          ? t('registry.exists.true')
                          : t('registry.exists.false')}
                      </td>
                      <td>
                        {scan.rnaActive === null
                          ? t('registry.rna.null')
                          : scan.rnaActive
                          ? t('registry.rna.true')
                          : t('registry.rna.false')}
                      </td>
                      <td>{scan.warningCount}</td>
                      <td>{new Date(scan.checkedAt).toLocaleDateString(locale)}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
            {registryData.totalPages > 1 && (
              <div className="pager">
                <button
                  className="cm-btn cm-btn-outline"
                  disabled={registryData.first}
                  onClick={() => setRegistryPage((p) => p - 1)}
                >
                  &lsaquo;
                </button>
                <span>
                  {t('registry.pagination.pageOf', {
                    current: registryData.number + 1,
                    total: registryData.totalPages,
                  })}
                </span>
                <button
                  className="cm-btn cm-btn-outline"
                  disabled={registryData.last}
                  onClick={() => setRegistryPage((p) => p + 1)}
                >
                  &rsaquo;
                </button>
              </div>
            )}
          </>
        )}
      </div>

      {/* Panel C — Recent audit log */}
      <div className="cm-card">
        <div className="cm-card-title">{t('auditLog.title')}</div>
        {auditError ? (
          <p className="alert-warning">{t('auditLog.error')}</p>
        ) : auditEntries === null ? (
          <p>{t('auditLog.loading')}</p>
        ) : auditEntries.length === 0 ? (
          <p>{t('auditLog.empty')}</p>
        ) : (
          <div style={{ overflowX: 'auto' }}>
            <table className="cm-table">
              <thead>
                <tr>
                  <th>{t('auditLog.col.seq')}</th>
                  <th>{t('auditLog.col.event')}</th>
                  <th>{t('auditLog.col.date')}</th>
                </tr>
              </thead>
              <tbody>
                {auditEntries.map((entry) => (
                  <tr key={entry.sequenceNo}>
                    <td>{entry.sequenceNo}</td>
                    <td>{entry.eventType}</td>
                    <td>{new Date(entry.occurredAt).toLocaleString(locale)}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </div>
    </div>
  );
}
