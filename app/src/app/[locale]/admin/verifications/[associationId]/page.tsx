'use client';

import { useState, useEffect } from 'react';
import { use } from 'react';
import { useLocale, useTranslations } from 'next-intl';
import Link from 'next/link';
import { getVerificationDetail } from '@/lib/api/admin';
import type { AdminVerificationDetailDto } from '@/types/admin';
import { VerificationStatus } from '@/types/association';
import { ROUTES } from '@/lib/routes';
import { STATUS_BADGE_CLASS } from '@/components/admin/adminShared';
import { VerificationDocumentRow } from '@/components/admin/VerificationDocumentRow';
import { VerificationDecisionPanel } from '@/components/admin/VerificationDecisionPanel';
import { RegistryPreCheckBanner } from '@/components/admin/RegistryPreCheckBanner';

interface Props {
  params: Promise<{ associationId: string }>;
}

export default function VerificationDetailPage({ params }: Props) {
  const { associationId } = use(params);
  const locale = useLocale();
  const t = useTranslations('admin');

  const [detail, setDetail] = useState<AdminVerificationDetailDto | null>(null);
  const [isLoading, setIsLoading] = useState(true);
  const [notFound, setNotFound] = useState(false);
  const [hasError, setHasError] = useState(false);

  const loadDetail = async () => {
    setIsLoading(true);
    setNotFound(false);
    setHasError(false);
    try {
      const data = await getVerificationDetail(associationId);
      setDetail(data);
    } catch (err: unknown) {
      if (err && typeof err === 'object' && 'response' in err) {
        const axiosErr = err as { response?: { status?: number } };
        if (axiosErr.response?.status === 404) {
          setNotFound(true);
          return;
        }
      }
      setHasError(true);
    } finally {
      setIsLoading(false);
    }
  };

  useEffect(() => {
    loadDetail();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [associationId]);

  const formatDate = (iso: string | null | undefined) => {
    if (!iso) return '—';
    return new Date(iso).toLocaleDateString(locale === 'fr' ? 'fr-FR' : 'en-GB', {
      day: '2-digit',
      month: 'short',
      year: 'numeric',
    });
  };

  if (isLoading) {
    return (
      <div className="page">
        <p className="camp-loading">{t('verifications.loading')}</p>
      </div>
    );
  }

  if (notFound) {
    return (
      <div className="page">
        <p style={{ color: 'var(--color-text-2)' }}>{t('verificationDetail.notFound')}</p>
      </div>
    );
  }

  if (hasError || !detail) {
    return (
      <div className="page">
        <p style={{ color: 'var(--color-error)' }}>{t('verificationDetail.error')}</p>
      </div>
    );
  }

  return (
    <div className="page">
      {/* Back link */}
      <div style={{ marginBottom: 16 }}>
        <Link
          href={`/${locale}${ROUTES.admin.verifications}?status=${detail.status}`}
          style={{ color: 'var(--color-indigo)', textDecoration: 'underline', fontSize: 14 }}
        >
          {t('verificationDetail.backToList')}
        </Link>
      </div>

      {/* Header */}
      <div className="page-head">
        <div>
          <h1>{detail.name}</h1>
          <p style={{ fontFamily: 'monospace', fontSize: 13, color: 'var(--color-text-2)' }}>
            {detail.identifier}
          </p>
        </div>
        <span className={STATUS_BADGE_CLASS[detail.status]}>
          {t(`status.${detail.status}`)}
        </span>
      </div>

      {/* Meta info */}
      <div
        style={{
          display: 'flex',
          gap: 24,
          flexWrap: 'wrap',
          fontSize: 14,
          color: 'var(--color-text-2)',
          marginBottom: 24,
        }}
      >
        {detail.submittedAt && (
          <span>
            {t('verificationDetail.submittedOn')} : <strong>{formatDate(detail.submittedAt)}</strong>
          </span>
        )}
        {detail.status === VerificationStatus.VERIFIED && detail.verifiedAt && (
          <span>
            {t('verificationDetail.verifiedOn')} : <strong>{formatDate(detail.verifiedAt)}</strong>
          </span>
        )}
        <span>{detail.docCount} {t('verifications.col.docs').toLowerCase()}</span>
      </div>

      {/* Rejection reason — shown prominently when status is REJECTED */}
      {detail.status === VerificationStatus.REJECTED && detail.rejectionReason && (
        <div
          style={{
            background: 'rgba(231,76,60,0.08)',
            border: '1px solid rgba(231,76,60,0.25)',
            borderRadius: 8,
            padding: '12px 16px',
            marginBottom: 24,
          }}
        >
          <p
            style={{
              fontWeight: 700,
              fontSize: 13,
              color: 'var(--color-error)',
              marginBottom: 4,
            }}
          >
            {t('verificationDetail.rejectionReason')}
          </p>
          <p style={{ fontSize: 14, whiteSpace: 'pre-wrap' }}>{detail.rejectionReason}</p>
        </div>
      )}

      {/* Registry pre-check — informational, loads independently */}
      <RegistryPreCheckBanner associationId={associationId} />

      {/* Required documents */}
      <section style={{ marginBottom: 32 }}>
        <h2 style={{ fontSize: 16, fontWeight: 700, marginBottom: 12 }}>
          {t('verificationDetail.requiredDocs')}
        </h2>
        <div style={{ display: 'flex', flexDirection: 'column', gap: 8 }}>
          {detail.requiredDocuments.map((slot) => (
            <VerificationDocumentRow
              key={slot.docType}
              variant="required"
              associationId={associationId}
              slot={slot}
            />
          ))}
        </div>
      </section>

      {/* Optional documents */}
      <section style={{ marginBottom: 32 }}>
        <h2 style={{ fontSize: 16, fontWeight: 700, marginBottom: 12 }}>
          {t('verificationDetail.optionalDocs')}
        </h2>
        {detail.optionalDocuments.length === 0 ? (
          <p style={{ fontSize: 14, color: 'var(--color-text-2)' }}>
            {t('verificationDetail.noDocs')}
          </p>
        ) : (
          <div style={{ display: 'flex', flexDirection: 'column', gap: 8 }}>
            {detail.optionalDocuments.map((doc) => (
              <VerificationDocumentRow
                key={doc.id}
                variant="optional"
                associationId={associationId}
                doc={doc}
              />
            ))}
          </div>
        )}
      </section>

      {/* Decision panel — approve / reject actions */}
      <section
        style={{
          borderTop: '1px solid var(--color-border)',
          paddingTop: 24,
          marginTop: 8,
        }}
      >
        <VerificationDecisionPanel
          associationId={associationId}
          status={detail.status}
          verifiedAt={detail.verifiedAt}
          rejectionReason={detail.rejectionReason}
          onDecisionMade={(newStatus, reason) =>
            setDetail((prev) =>
              prev
                ? { ...prev, status: newStatus, rejectionReason: reason ?? null }
                : prev,
            )
          }
          onNeedRefetch={loadDetail}
        />
      </section>
    </div>
  );
}
