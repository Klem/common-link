'use client';

import { useState, useEffect, use } from 'react';
import { useLocale, useTranslations } from 'next-intl';
import Link from 'next/link';
import { getCampaignDonorAcceptances, getCampaignReviewHistory } from '@/lib/api/compliance';
import type { AuditLogEntryDto, DonorLegalAcceptanceGroupDto } from '@/types/compliance';
import { ROUTES } from '@/lib/routes';
import { payloadText } from '@/components/compliance/complianceShared';

interface Props {
  params: Promise<{ associationId: string; campaignId: string }>;
}

function formatDateTime(iso: string, locale: string): string {
  return new Date(iso).toLocaleString(locale === 'fr' ? 'fr-FR' : 'en-GB', {
    dateStyle: 'medium', timeStyle: 'short',
  });
}

export default function CampaignReviewHistoryPage({ params }: Props) {
  const { associationId, campaignId } = use(params);
  const locale = useLocale();
  const t = useTranslations('compliance');

  const [history, setHistory] = useState<AuditLogEntryDto[] | null>(null);
  const [isLoading, setIsLoading] = useState(true);
  const [hasError, setHasError] = useState(false);
  const [notFound, setNotFound] = useState(false);

  const [donorAcceptances, setDonorAcceptances] = useState<DonorLegalAcceptanceGroupDto[] | null>(null);
  const [donorAcceptancesLoading, setDonorAcceptancesLoading] = useState(true);
  const [donorAcceptancesError, setDonorAcceptancesError] = useState(false);

  useEffect(() => {
    setIsLoading(true);
    setHasError(false);
    setNotFound(false);
    getCampaignReviewHistory(campaignId)
      .then(setHistory)
      .catch((err: unknown) => {
        if (err && typeof err === 'object' && 'response' in err) {
          const status = (err as { response?: { status?: number } }).response?.status;
          if (status === 404) { setNotFound(true); return; }
        }
        setHasError(true);
      })
      .finally(() => setIsLoading(false));
  }, [campaignId]);

  useEffect(() => {
    setDonorAcceptancesLoading(true);
    setDonorAcceptancesError(false);
    getCampaignDonorAcceptances(campaignId)
      .then(setDonorAcceptances)
      .catch(() => setDonorAcceptancesError(true))
      .finally(() => setDonorAcceptancesLoading(false));
  }, [campaignId]);

  return (
    <div className="page">
      <div className="page-head">
        <div>
          <Link
            href={`/${locale}${ROUTES.compliance.associationDetail(associationId)}`}
            style={{ fontSize: 13, color: 'var(--color-text-2)', textDecoration: 'none' }}
          >
            {t('associations.campaignHistory.backToAssociation')}
          </Link>
          <h1 style={{ marginTop: 8 }}>{t('associations.campaignHistory.title')}</h1>
        </div>
      </div>

      <div className="cm-card" style={{ marginBottom: 24, background: 'var(--mist-lavender)' }}>
        <p>{t('associations.campaignHistory.notice')}</p>
      </div>

      {isLoading && <p className="camp-loading">{t('associations.campaignHistory.loading')}</p>}
      {notFound && <p style={{ color: 'var(--color-text-2)' }}>{t('associations.detail.notFound')}</p>}
      {hasError && !isLoading && (
        <p style={{ color: 'var(--color-error)' }}>{t('associations.campaignHistory.error')}</p>
      )}

      {!isLoading && !hasError && !notFound && history && (
        history.length === 0 ? (
          <p style={{ color: 'var(--color-text-2)' }}>{t('associations.campaignHistory.empty')}</p>
        ) : (
          <div className="cm-card">
            <div style={{ overflowX: 'auto' }}>
              <table className="cm-table">
                <thead>
                  <tr>
                    <th>{t('associations.campaignHistory.col.date')}</th>
                    <th>{t('associations.campaignHistory.col.outcome')}</th>
                    <th>{t('associations.campaignHistory.col.reason')}</th>
                  </tr>
                </thead>
                <tbody>
                  {history.map((entry) => {
                    const reason = payloadText(entry.payload, 'reason');
                    const isRefused = entry.eventType === 'CAMPAIGN_REVIEW_REFUSED';
                    return (
                      <tr key={entry.sequenceNo}>
                        <td>{formatDateTime(entry.occurredAt, locale)}</td>
                        <td>
                          <span className={`badge ${isRefused ? 'badge-error' : 'badge-success'}`}>
                            {t(`associations.campaignHistory.outcome.${entry.eventType}`)}
                          </span>
                        </td>
                        <td>
                          {isRefused && reason !== '—' ? (
                            <strong style={{ color: 'var(--warm-coral)' }}>
                              {t(`associations.campaignHistory.reason.${reason}`)}
                            </strong>
                          ) : (
                            '—'
                          )}
                        </td>
                      </tr>
                    );
                  })}
                </tbody>
              </table>
            </div>
          </div>
        )
      )}

      {/* Donor CGU/CGV acceptance history, aggregated by donor */}
      <div className="cm-card" style={{ marginTop: 24 }}>
        <div className="cm-card-title">{t('associations.campaignHistory.donorAcceptances.title')}</div>
        <p style={{ color: 'var(--color-text-2)', fontSize: 13, marginTop: 4 }}>
          {t('associations.campaignHistory.donorAcceptances.notice')}
        </p>
        {donorAcceptancesLoading ? (
          <p className="camp-loading">{t('associations.campaignHistory.donorAcceptances.loading')}</p>
        ) : donorAcceptancesError ? (
          <p style={{ color: 'var(--color-error)' }}>{t('associations.campaignHistory.donorAcceptances.error')}</p>
        ) : !donorAcceptances || donorAcceptances.length === 0 ? (
          <p style={{ color: 'var(--color-text-2)' }}>{t('associations.campaignHistory.donorAcceptances.empty')}</p>
        ) : (
          donorAcceptances.map((group, i) => (
            <div
              key={group.donorId}
              style={{
                marginTop: 16,
                paddingTop: i === 0 ? 0 : 16,
                borderTop: i === 0 ? undefined : '1px solid var(--color-border)',
              }}
            >
              <div className="avatar-row">
                <div className="donor-name">{group.donorName ?? group.donorEmail ?? group.donorId}</div>
                {group.donorName && group.donorEmail && (
                  <span style={{ color: 'var(--color-text-2)', fontSize: 13 }}>{group.donorEmail}</span>
                )}
              </div>
              <div style={{ overflowX: 'auto', marginTop: 8 }}>
                <table className="cm-table">
                  <thead>
                    <tr>
                      <th>{t('associations.campaignHistory.donorAcceptances.col.document')}</th>
                      <th>{t('associations.campaignHistory.donorAcceptances.col.version')}</th>
                      <th>{t('associations.campaignHistory.donorAcceptances.col.acceptedAt')}</th>
                    </tr>
                  </thead>
                  <tbody>
                    {group.acceptances.map((row) => (
                      <tr key={row.id}>
                        <td>{row.documentType}</td>
                        <td>{row.documentVersion}</td>
                        <td>{formatDateTime(row.acceptedAt, locale)}</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            </div>
          ))
        )}
      </div>
    </div>
  );
}
