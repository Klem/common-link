'use client';

import { useState, useEffect, useCallback } from 'react';
import { useLocale, useTranslations } from 'next-intl';
import Link from 'next/link';
import { listAssociations } from '@/lib/api/compliance';
import type { ComplianceAssociationSummaryDto } from '@/types/compliance';
import type { AssociationStatus } from '@/types/association';
import type { Page } from '@/types/payment';
import { ROUTES } from '@/lib/routes';
import { ASSOCIATION_STATUS_BADGE_CLASS } from '@/components/compliance/complianceShared';

export default function AssociationsPage() {
  const locale = useLocale();
  const t = useTranslations('compliance');

  const [currentPage, setCurrentPage] = useState(0);
  const [data, setData] = useState<Page<ComplianceAssociationSummaryDto> | null>(null);
  const [isLoading, setIsLoading] = useState(true);
  const [hasError, setHasError] = useState(false);

  const loadData = useCallback(async () => {
    setIsLoading(true);
    setHasError(false);
    try {
      const result = await listAssociations(currentPage);
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
          <h1>{t('associations.title')}</h1>
          <p>{t('associations.subtitle')}</p>
        </div>
      </div>

      {isLoading && <p className="camp-loading">{t('associations.loading')}</p>}

      {hasError && !isLoading && (
        <p style={{ color: 'var(--color-error)', marginTop: 16 }}>{t('associations.error')}</p>
      )}

      {!isLoading && !hasError && data && (
        <>
          {data.content.length === 0 ? (
            <p style={{ color: 'var(--color-text-2)', marginTop: 24 }}>{t('associations.empty')}</p>
          ) : (
            <div style={{ overflowX: 'auto', marginTop: 16 }}>
              <table className="cm-table">
                <thead>
                  <tr>
                    <th>{t('associations.col.name')}</th>
                    <th>{t('associations.col.identifier')}</th>
                    <th>{t('associations.col.status')}</th>
                    <th>{t('associations.col.verification')}</th>
                    <th>{t('associations.col.risk')}</th>
                  </tr>
                </thead>
                <tbody>
                  {data.content.map((row) => (
                    <tr key={row.id}>
                      <td>
                        <Link
                          href={`/${locale}${ROUTES.compliance.associationDetail(row.id)}`}
                          style={{ color: 'var(--color-indigo)', textDecoration: 'underline' }}
                        >
                          {row.name}
                        </Link>
                      </td>
                      <td>
                        <span style={{ fontFamily: 'monospace', fontSize: 13 }}>{row.identifier}</span>
                      </td>
                      <td>
                        <span className={ASSOCIATION_STATUS_BADGE_CLASS[row.status as AssociationStatus]}>
                          {t(`associations.status.${row.status}`)}
                        </span>
                      </td>
                      <td>{t(`associations.verification.${row.verificationStatus}`)}</td>
                      <td>{t(`associations.risk.${row.riskLevel}`)}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}

          {data.totalPages > 1 && (
            <div className="pager">
              <button
                className="cm-btn cm-btn-outline"
                disabled={data.first}
                onClick={() => setCurrentPage((p) => p - 1)}
              >
                &lsaquo;
              </button>
              <span>
                {t('associations.pagination.pageOf', { current: data.number + 1, total: data.totalPages })}
              </span>
              <button
                className="cm-btn cm-btn-outline"
                disabled={data.last}
                onClick={() => setCurrentPage((p) => p + 1)}
              >
                &rsaquo;
              </button>
            </div>
          )}
        </>
      )}
    </div>
  );
}
