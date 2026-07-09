'use client';

import { useState, useEffect, useCallback, Suspense } from 'react';
import { useSearchParams, useRouter } from 'next/navigation';
import { useLocale, useTranslations } from 'next-intl';
import Link from 'next/link';
import { listVerifications } from '@/lib/api/admin';
import type { AdminVerificationSummaryDto } from '@/types/admin';
import type { Page } from '@/types/payment';
import { VerificationStatus } from '@/types/association';
import { ROUTES } from '@/lib/routes';
import { STATUS_BADGE_CLASS } from '@/components/admin/adminShared';

const TABS = [
  VerificationStatus.PENDING,
  VerificationStatus.VERIFIED,
  VerificationStatus.REJECTED,
  VerificationStatus.UNVERIFIED,
] as const;

function VerificationsContent() {
  const locale = useLocale();
  const t = useTranslations('admin');
  const router = useRouter();
  const searchParams = useSearchParams();

  const rawStatus = searchParams.get('status') as VerificationStatus | null;
  const activeStatus: VerificationStatus =
    rawStatus && (TABS as readonly string[]).includes(rawStatus)
      ? rawStatus
      : VerificationStatus.PENDING;

  const [currentPage, setCurrentPage] = useState(0);
  const [data, setData] = useState<Page<AdminVerificationSummaryDto> | null>(null);
  const [isLoading, setIsLoading] = useState(true);
  const [hasError, setHasError] = useState(false);

  const loadData = useCallback(async () => {
    setIsLoading(true);
    setHasError(false);
    try {
      const result = await listVerifications(activeStatus, currentPage);
      setData(result);
    } catch {
      setHasError(true);
    } finally {
      setIsLoading(false);
    }
  }, [activeStatus, currentPage]);

  useEffect(() => {
    loadData();
  }, [loadData]);

  const handleTabChange = (status: VerificationStatus) => {
    setCurrentPage(0);
    router.push(`/${locale}${ROUTES.admin.verifications}?status=${status}`);
  };

  const formatDate = (iso: string | null) => {
    if (!iso) return '—';
    return new Date(iso).toLocaleDateString(locale === 'fr' ? 'fr-FR' : 'en-GB', {
      day: '2-digit',
      month: 'short',
      year: 'numeric',
    });
  };

  return (
    <div className="page">
      <div className="page-head">
        <div>
          <h1>{t('verifications.title')}</h1>
        </div>
      </div>

      <div className="camp-filter-bar">
        {TABS.map((status) => (
          <button
            key={status}
            className={`btn btn-sm ${activeStatus === status ? 'btn-primary' : 'btn-secondary'}`}
            onClick={() => handleTabChange(status)}
          >
            {t(`verifications.tabs.${status.toLowerCase()}`)}
          </button>
        ))}
      </div>

      {isLoading && (
        <p className="camp-loading">{t('verifications.loading')}</p>
      )}

      {hasError && !isLoading && (
        <p style={{ color: 'var(--color-error)', marginTop: 16 }}>
          {t('verifications.error')}
        </p>
      )}

      {!isLoading && !hasError && data && (
        <>
          {data.content.length === 0 ? (
            <p style={{ color: 'var(--color-text-2)', marginTop: 24 }}>
              {t('verifications.empty')}
            </p>
          ) : (
            <div style={{ overflowX: 'auto', marginTop: 16 }}>
              <table>
                <thead>
                  <tr>
                    <th>{t('verifications.col.name')}</th>
                    <th>{t('verifications.col.identifier')}</th>
                    <th>{t('verifications.col.submitted')}</th>
                    <th>{t('verifications.col.docs')}</th>
                    <th>{t('verifications.col.status')}</th>
                  </tr>
                </thead>
                <tbody>
                  {data.content.map((row) => (
                    <tr key={row.associationId}>
                      <td>
                        <Link
                          href={`/${locale}${ROUTES.admin.verificationDetail(row.associationId)}`}
                          style={{ color: 'var(--color-indigo)', textDecoration: 'underline' }}
                        >
                          {row.name}
                        </Link>
                      </td>
                      <td>
                        <span style={{ fontFamily: 'monospace', fontSize: 13 }}>
                          {row.identifier}
                        </span>
                      </td>
                      <td>{formatDate(row.submittedAt)}</td>
                      <td>{row.docCount}</td>
                      <td>
                        <span className={STATUS_BADGE_CLASS[row.status]}>
                          {t(`status.${row.status}`)}
                        </span>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}

          {data.totalPages > 1 && (
            <div
              style={{
                display: 'flex',
                alignItems: 'center',
                gap: 12,
                marginTop: 20,
              }}
            >
              <button
                className="btn btn-sm btn-secondary"
                disabled={data.first}
                onClick={() => setCurrentPage((p) => p - 1)}
              >
                ←
              </button>
              <span style={{ fontSize: 14, color: 'var(--color-text-2)' }}>
                {t('verifications.pagination.pageOf', {
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

export default function VerificationsPage() {
  return (
    <Suspense>
      <VerificationsContent />
    </Suspense>
  );
}
