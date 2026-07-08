'use client';

import { useRef } from 'react';
import { useTranslations } from 'next-intl';
import { useVerification } from '@/hooks/dashboard/useVerification';
import { OptionalDocsCard } from './OptionalDocsCard';
import type { VerificationDocType } from '@/types/verification';

const REQUIRED_SLOTS: { docType: VerificationDocType; icon: string; labelKey: string; descKey: string }[] = [
  { docType: 'VERIF_STATUTS', icon: '📜', labelKey: 'verification.docs.statuts.label', descKey: 'verification.docs.statuts.desc' },
  { docType: 'VERIF_RNA_RECEIPT', icon: '🏛️', labelKey: 'verification.docs.rna.label', descKey: 'verification.docs.rna.desc' },
  { docType: 'VERIF_REPRESENTATIVE_ID', icon: '🪪', labelKey: 'verification.docs.idrep.label', descKey: 'verification.docs.idrep.desc' },
];

const ACCEPT = '.pdf,.jpg,.jpeg,.png,.docx';

function formatSize(bytes: number): string {
  if (bytes >= 1024 * 1024) return `${(bytes / 1024 / 1024).toFixed(1)} Mo`;
  return `${Math.round(bytes / 1024)} Ko`;
}

interface VerificationTabProps {
  onGoToVerif?: () => void;
}

export function VerificationTab({ onGoToVerif: _onGoToVerif }: VerificationTabProps) {
  const t = useTranslations('dashboard');
  const { state, optionalDocs, isLoading, uploadRequired, deleteRequired, submitDossier, uploadOptional, deleteOptional } =
    useVerification();

  const fileInputRefs = useRef<Record<string, HTMLInputElement | null>>({});

  if (isLoading || !state) {
    return <div className="card no-hover"><div className="card-b"><p className="profile-loading">{t('association.profile.loading')}</p></div></div>;
  }

  const status = state.status;
  const uploadedCount = state.requiredDocuments.filter((s) => s.uploaded).length;
  const allUploaded = uploadedCount === 3;
  const canUpload = status !== 'PENDING' && status !== 'VERIFIED';
  const canSubmit = allUploaded && (status === 'UNVERIFIED' || status === 'REJECTED');

  // ─── Banner ───────────────────────────────────────────────────────────────

  let bannerClass = 'acc-card partial';
  let bannerTitle = '';
  let bannerSub = '';
  let bannerEta = '';
  let showSubmitBtn = false;
  let submitBtnDisabled = false;

  if (status === 'VERIFIED') {
    bannerClass = 'acc-card';
    bannerTitle = t('verification.banner.verified.title');
    bannerSub = t('verification.banner.verified.sub');
  } else if (status === 'PENDING') {
    bannerTitle = t('verification.banner.pending.title');
    bannerSub = t('verification.banner.pending.sub');
    bannerEta = t('verification.banner.pending.eta');
    showSubmitBtn = true;
    submitBtnDisabled = true;
  } else if (status === 'REJECTED') {
    bannerTitle = t('verification.banner.rejected.title');
    bannerSub = state.rejectionReason ?? t('verification.banner.rejected.defaultReason');
    bannerEta = allUploaded ? t('verification.banner.readyToResubmit') : t('verification.banner.incomplete.eta');
    showSubmitBtn = true;
    submitBtnDisabled = !canSubmit;
  } else if (allUploaded) {
    bannerTitle = t('verification.banner.ready.title');
    bannerSub = t('verification.banner.ready.sub');
    bannerEta = t('verification.banner.ready.eta');
    showSubmitBtn = true;
    submitBtnDisabled = false;
  } else {
    bannerTitle = t('verification.banner.incomplete.title');
    bannerSub = t('verification.banner.incomplete.sub');
    bannerEta = t('verification.banner.incomplete.eta');
    showSubmitBtn = true;
    submitBtnDisabled = true;
  }

  return (
    <>
      {/* ── Banner ──────────────────────────────────────────────────────── */}
      <div className={bannerClass} style={{ marginBottom: 24 }}>
        <div className="acc-card-head">
          <div>
            <div className="acc-card-title">{bannerTitle}</div>
            <div className="acc-card-sub">{bannerSub}</div>
          </div>
        </div>
        {showSubmitBtn && (
          <div style={{ display: 'flex', alignItems: 'center', gap: 12, flexWrap: 'wrap' }}>
            <button
              className="btn btn-primary"
              disabled={submitBtnDisabled}
              style={submitBtnDisabled ? { opacity: 0.4, cursor: 'not-allowed' } : undefined}
              onClick={canSubmit ? submitDossier : undefined}
            >
              {status === 'PENDING' ? (
                <>
                  <span style={{ display: 'inline-block', animation: 'spin .7s linear infinite', verticalAlign: 'middle', marginRight: 6 }}>⏳</span>
                  {t('verification.banner.pending.btn')}
                </>
              ) : (
                t('verification.submit')
              )}
            </button>
            {bannerEta && (
              <span style={{ fontSize: 12, color: 'var(--slate-lavender)' }}>{bannerEta}</span>
            )}
          </div>
        )}
      </div>

      {/* ── Required documents ──────────────────────────────────────────── */}
      <div className="card no-hover" style={{ marginBottom: 20 }}>
        <div className="card-h">
          <h3>{t('verification.requiredDocs.title')}</h3>
          <span style={{ fontSize: 12, color: 'var(--slate-lavender)' }}>
            {uploadedCount}/{state.requiredDocuments.length}
          </span>
        </div>
        <div className="card-b" style={{ padding: 0 }}>
          {REQUIRED_SLOTS.map((slot) => {
            const slotData = state.requiredDocuments.find((d) => d.docType === slot.docType);
            const uploaded = slotData?.uploaded ?? false;

            return (
              <div key={slot.docType} className={`vd-slot${uploaded ? ' uploaded' : ''}`}>
                <div className="vd-slot-ic">{slot.icon}</div>
                <div className="vd-slot-body">
                  <div className="vd-slot-title">
                    {t(slot.labelKey as Parameters<typeof t>[0])}{' '}
                    <span className="vd-slot-required">
                      {uploaded ? `✓ ${t('verification.requiredDocs.uploaded')}` : t('verification.requiredDocs.required')}
                    </span>
                  </div>
                  <div className="vd-slot-desc">{t(slot.descKey as Parameters<typeof t>[0])}</div>
                  {uploaded && slotData?.fileName && (
                    <div className="vd-slot-file">
                      📄 {slotData.fileName}
                      {slotData.sizeBytes != null && (
                        <span style={{ color: 'var(--slate-lavender)', fontWeight: 400 }}>
                          {' · '}{formatSize(slotData.sizeBytes)}
                        </span>
                      )}
                    </div>
                  )}
                </div>
                <div className="vd-slot-actions">
                  <input
                    ref={(el) => { fileInputRefs.current[slot.docType] = el; }}
                    type="file"
                    accept={ACCEPT}
                    style={{ display: 'none' }}
                    onChange={(e) => {
                      const file = e.target.files?.[0];
                      if (file) uploadRequired(slot.docType, file);
                      e.target.value = '';
                    }}
                  />
                  {uploaded ? (
                    <>
                      <button
                        className="vd-btn vd-btn-replace"
                        disabled={!canUpload}
                        onClick={() => canUpload && fileInputRefs.current[slot.docType]?.click()}
                      >
                        {t('verification.requiredDocs.replace')}
                      </button>
                      <button
                        className="vd-btn vd-btn-del"
                        disabled={!canUpload}
                        onClick={() => canUpload && deleteRequired(slot.docType)}
                      >
                        {t('verification.requiredDocs.delete')}
                      </button>
                    </>
                  ) : (
                    <button
                      className="vd-btn vd-btn-up"
                      disabled={!canUpload}
                      onClick={() => canUpload && fileInputRefs.current[slot.docType]?.click()}
                    >
                      📎 {t('verification.requiredDocs.upload')}
                    </button>
                  )}
                </div>
              </div>
            );
          })}
        </div>
      </div>

      {/* ── Optional documents ──────────────────────────────────────────── */}
      <OptionalDocsCard
        docs={optionalDocs}
        onUpload={uploadOptional}
        onDelete={deleteOptional}
      />
    </>
  );
}
