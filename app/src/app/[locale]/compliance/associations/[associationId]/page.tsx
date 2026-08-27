'use client';

import { useState, useEffect, use, Suspense } from 'react';
import { useSearchParams, useRouter } from 'next/navigation';
import { useLocale, useTranslations } from 'next-intl';
import Link from 'next/link';
import { getAssociationDetail, listAssociationCampaigns, listLegalAcceptances } from '@/lib/api/compliance';
import type { ComplianceAssociationDetailDto } from '@/types/compliance';
import type { CampaignSummaryDto } from '@/types/campaign';
import type { CampaignStatus } from '@/types/campaign';
import type { LegalAcceptanceDto } from '@/types/legal';
import { LegalAcceptanceSubjectType } from '@/types/legal';
import { ROUTES } from '@/lib/routes';
import { ASSOCIATION_STATUS_BADGE_CLASS, CAMPAIGN_STATUS_BADGE_CLASS } from '@/components/compliance/complianceShared';

interface Props {
  params: Promise<{ associationId: string }>;
}

const CAMPAIGN_TABS: CampaignStatus[] = [
  'DRAFT', 'LIVE', 'PAUSED', 'REVERT_REQUESTED', 'CANCELLED', 'COMPLETED', 'ENDED',
];

function formatDate(iso: string | null, locale: string): string {
  if (!iso) return '—';
  return new Date(iso).toLocaleDateString(locale === 'fr' ? 'fr-FR' : 'en-GB', {
    day: '2-digit', month: 'short', year: 'numeric',
  });
}

function formatEuros(amount: number, locale: string): string {
  return new Intl.NumberFormat(locale === 'fr' ? 'fr-FR' : 'en-GB', {
    style: 'currency', currency: 'EUR', maximumFractionDigits: 0,
  }).format(amount);
}

function AssociationDetailContent({ associationId }: { associationId: string }) {
  const locale = useLocale();
  const t = useTranslations('compliance');
  const router = useRouter();
  const searchParams = useSearchParams();

  const [detail, setDetail] = useState<ComplianceAssociationDetailDto | null>(null);
  const [detailLoading, setDetailLoading] = useState(true);
  const [detailError, setDetailError] = useState(false);
  const [notFound, setNotFound] = useState(false);

  const [campaigns, setCampaigns] = useState<CampaignSummaryDto[] | null>(null);
  const [campaignsLoading, setCampaignsLoading] = useState(true);
  const [campaignsError, setCampaignsError] = useState(false);

  const [acceptances, setAcceptances] = useState<LegalAcceptanceDto[] | null>(null);
  const [acceptancesLoading, setAcceptancesLoading] = useState(true);
  const [acceptancesError, setAcceptancesError] = useState(false);

  const activeFilter = (searchParams.get('status') as CampaignStatus | null) ?? '';

  useEffect(() => {
    setDetailLoading(true);
    setDetailError(false);
    setNotFound(false);
    getAssociationDetail(associationId)
      .then(setDetail)
      .catch((err: unknown) => {
        if (err && typeof err === 'object' && 'response' in err) {
          const status = (err as { response?: { status?: number } }).response?.status;
          if (status === 404) { setNotFound(true); return; }
        }
        setDetailError(true);
      })
      .finally(() => setDetailLoading(false));
  }, [associationId]);

  useEffect(() => {
    setCampaignsLoading(true);
    setCampaignsError(false);
    listAssociationCampaigns(associationId)
      .then(setCampaigns)
      .catch(() => setCampaignsError(true))
      .finally(() => setCampaignsLoading(false));
  }, [associationId]);

  useEffect(() => {
    setAcceptancesLoading(true);
    setAcceptancesError(false);
    listLegalAcceptances(LegalAcceptanceSubjectType.ASSOCIATION, associationId)
      .then(setAcceptances)
      .catch(() => setAcceptancesError(true))
      .finally(() => setAcceptancesLoading(false));
  }, [associationId]);

  const handleTabChange = (status: CampaignStatus | '') => {
    const query = status ? `?status=${status}` : '';
    router.push(`/${locale}${ROUTES.compliance.associationDetail(associationId)}${query}`);
  };

  const filteredCampaigns = campaigns?.filter((c) => !activeFilter || c.status === activeFilter) ?? [];

  if (detailLoading) return <div className="page"><p className="camp-loading">{t('associations.detail.loading')}</p></div>;
  if (notFound) return <div className="page"><p style={{ color: 'var(--color-text-2)' }}>{t('associations.detail.notFound')}</p></div>;
  if (detailError || !detail) return <div className="page"><p style={{ color: 'var(--color-error)' }}>{t('associations.detail.error')}</p></div>;

  return (
    <div className="page">
      <div className="page-head">
        <div>
          <Link
            href={`/${locale}${ROUTES.compliance.associations}`}
            style={{ fontSize: 13, color: 'var(--color-text-2)', textDecoration: 'none' }}
          >
            {t('associations.detail.backToList')}
          </Link>
          <h1 style={{ marginTop: 8 }}>{detail.name}</h1>
        </div>
      </div>

      {/* Compliance status */}
      <div className="cm-card" style={{ marginBottom: 24 }}>
        <div className="cm-card-title">{t('associations.detail.status.title')}</div>
        <div className="frow" style={{ flexWrap: 'wrap', gap: 16, marginTop: 12 }}>
          <div>
            <span className="cm-label">{t('associations.detail.status.current')}</span>
            <span className={ASSOCIATION_STATUS_BADGE_CLASS[detail.status as keyof typeof ASSOCIATION_STATUS_BADGE_CLASS]}>
              {t(`associations.status.${detail.status}`)}
            </span>
          </div>
          <div>
            <span className="cm-label">{t('associations.detail.status.verification')}</span>
            <span>{t(`associations.verification.${detail.verificationStatus}`)}</span>
          </div>
          <div>
            <span className="cm-label">{t('associations.detail.status.verifiedAt')}</span>
            <span>{formatDate(detail.verifiedAt, locale)}</span>
          </div>
          <div>
            <span className="cm-label">{t('associations.detail.status.risk')}</span>
            <span>{t(`associations.risk.${detail.riskLevel}`)}</span>
          </div>
          <div>
            <span className="cm-label">{t('associations.detail.status.riskAssessedAt')}</span>
            <span>{formatDate(detail.riskLevelAssessedAt, locale)}</span>
          </div>
          <div>
            <span className="cm-label">{t('associations.detail.status.riskVersion')}</span>
            <span>{detail.riskClassificationVersion ?? '—'}</span>
          </div>
        </div>
        {detail.status === 'SUSPENDED' && (
          <p style={{ marginTop: 16, color: 'var(--warm-coral)' }}>
            {t('associations.detail.status.suspendedNotice')}{' '}
            <Link href={`/${locale}${ROUTES.compliance.alerts}`} style={{ textDecoration: 'underline' }}>
              {t('associations.detail.status.suspendedLink')}
            </Link>
          </p>
        )}
      </div>

      {/* CGU/CGV acceptance history */}
      <div className="cm-card" style={{ marginBottom: 24 }}>
        <div className="cm-card-title">{t('associations.detail.cguCgv.title')}</div>
        {acceptancesLoading ? (
          <p className="camp-loading">{t('associations.detail.cguCgv.loading')}</p>
        ) : acceptancesError ? (
          <p style={{ color: 'var(--color-error)' }}>{t('associations.detail.cguCgv.error')}</p>
        ) : !acceptances || acceptances.length === 0 ? (
          <p style={{ color: 'var(--color-text-2)' }}>{t('associations.detail.cguCgv.empty')}</p>
        ) : (
          <div style={{ overflowX: 'auto', marginTop: 12 }}>
            <table className="cm-table">
              <thead>
                <tr>
                  <th>{t('associations.detail.cguCgv.col.document')}</th>
                  <th>{t('associations.detail.cguCgv.col.version')}</th>
                  <th>{t('associations.detail.cguCgv.col.acceptedAt')}</th>
                  <th>{t('associations.detail.cguCgv.col.signer')}</th>
                </tr>
              </thead>
              <tbody>
                {acceptances.map((row) => (
                  <tr key={row.id}>
                    <td>{row.documentType}</td>
                    <td>{row.documentVersion}</td>
                    <td>{formatDate(row.acceptedAt, locale)}</td>
                    <td>{row.signerName ?? '—'}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </div>

      {/* Legal information */}
      <div className="cm-card" style={{ marginBottom: 24 }}>
        <div className="cm-card-title">{t('associations.detail.legalInfo.title')}</div>
        <div className="frow" style={{ flexWrap: 'wrap', gap: 16, marginTop: 12 }}>
          <div>
            <span className="cm-label">{t('associations.detail.legalInfo.identifier')}</span>
            <span style={{ fontFamily: 'monospace' }}>{detail.identifier}</span>
          </div>
          <div>
            <span className="cm-label">{t('associations.detail.legalInfo.siren')}</span>
            <span>{detail.siren ?? '—'}</span>
          </div>
          <div>
            <span className="cm-label">{t('associations.detail.legalInfo.address')}</span>
            <span>{detail.addressLine1 ?? '—'}</span>
          </div>
          <div>
            <span className="cm-label">{t('associations.detail.legalInfo.city')}</span>
            <span>{[detail.postalCode, detail.city].filter(Boolean).join(' ') || '—'}</span>
          </div>
          <div>
            <span className="cm-label">{t('associations.detail.legalInfo.creationYear')}</span>
            <span>{detail.creationYear ?? '—'}</span>
          </div>
          <div style={{ minWidth: '100%' }}>
            <span className="cm-label">{t('associations.detail.legalInfo.object')}</span>
            <span>{detail.legalObject ?? '—'}</span>
          </div>
          <div>
            <span className="cm-label">{t('associations.detail.legalInfo.signer')}</span>
            <span>{detail.signerName ?? '—'}</span>
          </div>
          <div>
            <span className="cm-label">{t('associations.detail.legalInfo.signerRole')}</span>
            <span>{detail.signerRole ?? '—'}</span>
          </div>
          <div>
            <span className="cm-label">{t('associations.detail.legalInfo.contactName')}</span>
            <span>{detail.contactName ?? '—'}</span>
          </div>
          <div>
            <span className="cm-label">{t('associations.detail.legalInfo.contactEmail')}</span>
            <span>{detail.contactEmail ?? '—'}</span>
          </div>
          <div>
            <span className="cm-label">{t('associations.detail.legalInfo.phone')}</span>
            <span>{detail.phone ?? '—'}</span>
          </div>
        </div>
      </div>

      {/* Campaigns, filtered by status */}
      <div className="cm-card">
        <div className="cm-card-title">{t('associations.detail.campaigns.title')}</div>

        <div className="camp-filter-bar" style={{ marginTop: 12 }}>
          <button
            className={`btn btn-sm ${activeFilter === '' ? 'btn-primary' : 'btn-secondary'}`}
            onClick={() => handleTabChange('')}
          >
            {t('associations.detail.campaigns.filterAll')}
          </button>
          {CAMPAIGN_TABS.map((status) => (
            <button
              key={status}
              className={`btn btn-sm ${activeFilter === status ? 'btn-primary' : 'btn-secondary'}`}
              onClick={() => handleTabChange(status)}
            >
              {t(`associations.detail.campaigns.status.${status}`)}
            </button>
          ))}
        </div>

        {campaignsLoading ? (
          <p className="camp-loading">{t('associations.detail.campaigns.loading')}</p>
        ) : campaignsError ? (
          <p style={{ color: 'var(--color-error)' }}>{t('associations.detail.campaigns.error')}</p>
        ) : filteredCampaigns.length === 0 ? (
          <p style={{ color: 'var(--color-text-2)', marginTop: 16 }}>{t('associations.detail.campaigns.empty')}</p>
        ) : (
          <div style={{ overflowX: 'auto', marginTop: 16 }}>
            <table className="cm-table">
              <thead>
                <tr>
                  <th>{t('associations.detail.campaigns.col.name')}</th>
                  <th>{t('associations.detail.campaigns.col.status')}</th>
                  <th>{t('associations.detail.campaigns.col.goal')}</th>
                  <th>{t('associations.detail.campaigns.col.raised')}</th>
                  <th>{t('associations.detail.campaigns.col.createdAt')}</th>
                </tr>
              </thead>
              <tbody>
                {filteredCampaigns.map((campaign) => (
                  <tr key={campaign.id}>
                    <td>
                      <Link
                        href={`/${locale}${ROUTES.compliance.campaignDetail(associationId, campaign.id)}`}
                        style={{ color: 'var(--color-indigo)', textDecoration: 'underline' }}
                      >
                        {campaign.emoji} {campaign.name}
                      </Link>
                    </td>
                    <td>
                      <span className={CAMPAIGN_STATUS_BADGE_CLASS[campaign.status]}>
                        {t(`associations.detail.campaigns.status.${campaign.status}`)}
                      </span>
                    </td>
                    <td>{formatEuros(campaign.goal, locale)}</td>
                    <td>{formatEuros(campaign.raised, locale)}</td>
                    <td>{formatDate(campaign.createdAt, locale)}</td>
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

export default function AssociationDetailPage({ params }: Props) {
  const { associationId } = use(params);
  return (
    <Suspense fallback={null}>
      <AssociationDetailContent associationId={associationId} />
    </Suspense>
  );
}
