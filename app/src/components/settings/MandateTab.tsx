'use client';

import { useRef, useState } from 'react';
import { useTranslations } from 'next-intl';
import type { MandateStateDto, MandateDocType, SignMandateRequest, MandateEligibility } from '@/types/mandate';

const MANDATE_SLOTS: { docType: MandateDocType; icon: string; labelKey: string; descKey: string }[] = [
  {
    docType: 'MANDATE_STATUTS',
    icon: '📄',
    labelKey: 'association.profile.mandate.docs.statuts.label',
    descKey: 'association.profile.mandate.docs.statuts.desc',
  },
  {
    docType: 'MANDATE_RESCRIT',
    icon: '📄',
    labelKey: 'association.profile.mandate.docs.rescrit.label',
    descKey: 'association.profile.mandate.docs.rescrit.desc',
  },
];

const ELIGIBILITY_OPTIONS: { value: MandateEligibility; labelKey: string; descKey: string }[] = [
  {
    value: 'OIG_66',
    labelKey: 'association.profile.mandate.eligibility.oig66.label',
    descKey: 'association.profile.mandate.eligibility.oig66.desc',
  },
  {
    value: 'OIG_75_COLUCHE',
    labelKey: 'association.profile.mandate.eligibility.oig75.label',
    descKey: 'association.profile.mandate.eligibility.oig75.desc',
  },
  {
    value: 'PUBLIC_UTILITY_66',
    labelKey: 'association.profile.mandate.eligibility.public66.label',
    descKey: 'association.profile.mandate.eligibility.public66.desc',
  },
];

const ACCEPT = '.pdf,.jpg,.jpeg,.png,.docx';

// Temporarily hides the mandate "Pièces justificatives" (supporting documents) step and drops
// the doc-upload precondition for signing. Set back to true to restore the step.
// NOTE: when restoring, also revert the step3 title number "2." → "3." in messages/{fr,en}.json
// (association.profile.mandate.step3.title). The backend doc guard in MandateService.signMandate
// was removed in the same change and must be restored alongside this flag.
const SHOW_MANDATE_DOCS = false;

function formatSize(bytes: number): string {
  if (bytes >= 1024 * 1024) return `${(bytes / 1024 / 1024).toFixed(1)} Mo`;
  return `${Math.round(bytes / 1024)} Ko`;
}

function formatDate(iso: string | null | undefined): string {
  if (!iso) return '—';
  return new Date(iso).toLocaleDateString('fr-FR', {
    day: '2-digit',
    month: 'long',
    year: 'numeric',
  });
}

interface MandateTabProps {
  state: MandateStateDto | null;
  isLoading: boolean;
  onGoToVerif: () => void;
  onUploadDoc: (docType: MandateDocType, file: File) => Promise<void>;
  onDeleteDoc: (docType: MandateDocType) => Promise<void>;
  onSign: (request: SignMandateRequest) => Promise<void>;
  onRevoke: () => Promise<void>;
  onDownloadPdf: () => Promise<void>;
  /** Nom du signataire habilité — imprimé sur les reçus fiscaux. Absent ⇒ signature bloquée. */
  signerName?: string | null;
  /** Fonction du signataire habilité — imprimée sur les reçus fiscaux. Absente ⇒ signature bloquée. */
  signerRole?: string | null;
}

export function MandateTab({
  state,
  isLoading,
  onGoToVerif,
  onUploadDoc,
  onDeleteDoc,
  onSign,
  onRevoke,
  onDownloadPdf,
  signerName,
  signerRole,
}: MandateTabProps) {
  const t = useTranslations('dashboard');
  const fileInputRefs = useRef<Record<string, HTMLInputElement | null>>({});

  const [selectedEligibility, setSelectedEligibility] = useState<MandateEligibility | null>(null);
  const [accepted, setAccepted] = useState(false);
  const [showRevokeModal, setShowRevokeModal] = useState(false);
  const [showSignerWarning, setShowSignerWarning] = useState(false);
  const [isSigning, setIsSigning] = useState(false);
  const [isRevoking, setIsRevoking] = useState(false);

  if (isLoading || !state) {
    return (
      <div className="card no-hover">
        <div className="card-b">
          <p className="profile-loading">{t('association.profile.loading')}</p>
        </div>
      </div>
    );
  }

  const uploadedCount = state.mandateDocs.filter((d) => d.uploaded).length;
  const canSign =
    selectedEligibility !== null && (!SHOW_MANDATE_DOCS || uploadedCount === 2) && accepted && !state.signed;
  const canUpload = !state.signed;

  const eligibilityLabel = (e: MandateEligibility | null | undefined): string => {
    const opt = ELIGIBILITY_OPTIONS.find((o) => o.value === e);
    return opt ? t(opt.labelKey as Parameters<typeof t>[0]) : '—';
  };

  const missingSignerName = !signerName?.trim();
  const missingSignerRole = !signerRole?.trim();

  const handleSign = async () => {
    if (!canSign || !selectedEligibility) return;
    // Le mandat autorise CommonLink à émettre des reçus fiscaux au nom du signataire habilité :
    // sans nom ni fonction, les reçus seraient invalides. Garde volontairement placée ici et non
    // dans `canSign` — un bouton `disabled` ne pourrait pas ouvrir la modale d'alerte.
    // Miroir de la garde backend dans `MandateService.signMandate`.
    if (missingSignerName || missingSignerRole) {
      setShowSignerWarning(true);
      return;
    }
    setIsSigning(true);
    await onSign({ eligibility: selectedEligibility, accepted: true });
    setIsSigning(false);
    setAccepted(false);
    setSelectedEligibility(null);
  };

  const handleRevoke = async () => {
    setIsRevoking(true);
    await onRevoke();
    setIsRevoking(false);
    setShowRevokeModal(false);
  };

  return (
    <>
      {/* ── Bandeau pédagogique ───────────────────────────────────────────── */}
      <div className="acc-card" style={{ marginBottom: 20 }}>
        <div className="acc-card-head">
          <div>
            <div className="acc-card-title">
              🧾 {t('association.profile.mandate.pedagogic.title')}
            </div>
            <div className="acc-card-sub">
              {t('association.profile.mandate.pedagogic.sub')}
            </div>
          </div>
        </div>
      </div>

      {/* ── Vue : bloqué (association non vérifiée) ────────────────────────── */}
      {state.blocked && (
        <div
          className="card no-hover"
          style={{
            marginBottom: 20,
            borderColor: 'rgba(255,179,71,.3)',
            background: 'linear-gradient(135deg,var(--white) 0%,rgba(255,179,71,.05) 100%)',
          }}
        >
          <div className="card-b">
            <div className="pp-row missing" style={{ marginBottom: 14 }}>
              <div className="pp-row-ic">!</div>
              <div className="pp-row-lbl">{t('association.profile.mandate.blocked.message')}</div>
            </div>
            <button className="btn btn-secondary btn-sm" onClick={onGoToVerif}>
              {t('association.profile.mandate.blocked.cta')}
            </button>
          </div>
        </div>
      )}

      {/* ── Vue : non signé (3 étapes) ────────────────────────────────────── */}
      {!state.blocked && !state.signed && (
        <>
          {/* Étape 1 — Éligibilité */}
          <div className="card no-hover" style={{ marginBottom: 16 }}>
            <div className="card-h">
              <h3>{t('association.profile.mandate.step1.title')}</h3>
              <span className={`set-tab-badge${selectedEligibility ? ' ok' : ''}`}>
                {selectedEligibility
                  ? t('association.profile.mandate.step1.declared')
                  : t('association.profile.mandate.step1.toDeclare')}
              </span>
            </div>
            <div className="card-b">
              <p style={{ fontSize: 14, color: 'var(--slate-lavender)', lineHeight: 1.6, marginBottom: 14 }}>
                {t('association.profile.mandate.step1.intro')}
              </p>
              <div style={{ display: 'flex', flexDirection: 'column', gap: 10 }}>
                {ELIGIBILITY_OPTIONS.map((opt) => (
                  <label
                    key={opt.value}
                    style={{
                      display: 'flex',
                      alignItems: 'flex-start',
                      gap: 10,
                      padding: 14,
                      border: `1px solid ${selectedEligibility === opt.value ? 'var(--bright-teal)' : 'var(--mist-lavender)'}`,
                      borderRadius: 'var(--radius-md)',
                      cursor: 'pointer',
                    }}
                  >
                    <input
                      type="radio"
                      name="elig-status"
                      value={opt.value}
                      style={{ marginTop: 3 }}
                      checked={selectedEligibility === opt.value}
                      onChange={() => setSelectedEligibility(opt.value)}
                    />
                    <div>
                      <strong style={{ fontSize: 14 }}>
                        {t(opt.labelKey as Parameters<typeof t>[0])}
                      </strong>
                      <div style={{ fontSize: 12, color: 'var(--slate-lavender)', marginTop: 2 }}>
                        {t(opt.descKey as Parameters<typeof t>[0])}
                      </div>
                    </div>
                  </label>
                ))}
              </div>
            </div>
          </div>

          {/* Étape 2 — Pièces justificatives (masquée temporairement — voir SHOW_MANDATE_DOCS) */}
          {SHOW_MANDATE_DOCS && (
          <div className="card no-hover" style={{ marginBottom: 16 }}>
            <div className="card-h">
              <h3>{t('association.profile.mandate.step2.title')}</h3>
              <span style={{ fontSize: 12, color: 'var(--slate-lavender)' }}>
                {uploadedCount}/2 {t('association.profile.mandate.step2.provided')}
              </span>
            </div>
            <div className="card-b" style={{ padding: 0 }}>
              {MANDATE_SLOTS.map((slot, idx) => {
                const slotData = state.mandateDocs.find((d) => d.docType === slot.docType);
                const uploaded = slotData?.uploaded ?? false;

                return (
                  <div
                    key={slot.docType}
                    style={{
                      padding: '16px 22px',
                      borderBottom: idx < MANDATE_SLOTS.length - 1 ? '1px solid var(--mist-lavender)' : undefined,
                      display: 'flex',
                      alignItems: 'center',
                      gap: 14,
                    }}
                  >
                    <div
                      style={{
                        width: 36,
                        height: 36,
                        borderRadius: '50%',
                        background: uploaded ? 'rgba(78,205,196,.15)' : 'var(--mist-lavender)',
                        display: 'flex',
                        alignItems: 'center',
                        justifyContent: 'center',
                        fontSize: 14,
                        flexShrink: 0,
                      }}
                    >
                      {uploaded ? '✓' : slot.icon}
                    </div>
                    <div style={{ flex: 1 }}>
                      <div style={{ fontWeight: 600, fontSize: 14 }}>
                        {t(slot.labelKey as Parameters<typeof t>[0])}
                      </div>
                      {uploaded && slotData?.fileName ? (
                        <div style={{ fontSize: 12, color: 'var(--slate-lavender)' }}>
                          {slotData.fileName}
                          {slotData.sizeBytes != null && (
                            <> · {formatSize(slotData.sizeBytes)}</>
                          )}
                        </div>
                      ) : (
                        <div style={{ fontSize: 12, color: 'var(--slate-lavender)' }}>
                          {t(slot.descKey as Parameters<typeof t>[0])}
                        </div>
                      )}
                    </div>
                    <input
                      ref={(el) => { fileInputRefs.current[slot.docType] = el; }}
                      type="file"
                      accept={ACCEPT}
                      style={{ display: 'none' }}
                      onChange={(e) => {
                        const file = e.target.files?.[0];
                        if (file) onUploadDoc(slot.docType, file);
                        e.target.value = '';
                      }}
                    />
                    <div style={{ display: 'flex', gap: 8 }}>
                      {uploaded && (
                        <button
                          className="btn btn-secondary btn-sm"
                          disabled={!canUpload}
                          onClick={() => canUpload && onDeleteDoc(slot.docType)}
                        >
                          {t('association.profile.mandate.docs.delete')}
                        </button>
                      )}
                      <button
                        className="btn btn-secondary btn-sm"
                        disabled={!canUpload}
                        onClick={() => canUpload && fileInputRefs.current[slot.docType]?.click()}
                      >
                        {uploaded
                          ? t('association.profile.mandate.docs.replace')
                          : t('association.profile.mandate.docs.upload')}
                      </button>
                    </div>
                  </div>
                );
              })}
            </div>
          </div>
          )}

          {/* Étape 3 — Signature */}
          <div className="card no-hover" style={{ marginBottom: 16 }}>
            <div className="card-h">
              <h3>{t('association.profile.mandate.step3.title')}</h3>
            </div>
            <div className="card-b">
              <div
                style={{
                  background: 'var(--soft-cream)',
                  borderRadius: 'var(--radius-lg)',
                  padding: 18,
                  marginBottom: 16,
                  fontSize: 13,
                  lineHeight: 1.65,
                  color: 'var(--ink-navy)',
                  maxHeight: 180,
                  overflowY: 'auto',
                }}
              >
                <p style={{ margin: '0 0 10px 0' }}>
                  <strong>{t('association.profile.mandate.legalText.object.title')}</strong>{' '}
                  {t('association.profile.mandate.legalText.object.body')}
                </p>
                <p style={{ margin: '0 0 10px 0' }}>
                  <strong>{t('association.profile.mandate.legalText.responsibility.title')}</strong>{' '}
                  {t('association.profile.mandate.legalText.responsibility.body')}
                </p>
                <p style={{ margin: '0 0 10px 0' }}>
                  <strong>{t('association.profile.mandate.legalText.retention.title')}</strong>{' '}
                  {t('association.profile.mandate.legalText.retention.body')}
                </p>
                <p style={{ margin: 0 }}>
                  <strong>{t('association.profile.mandate.legalText.revocation.title')}</strong>{' '}
                  {t('association.profile.mandate.legalText.revocation.body')}
                </p>
              </div>
              <label
                style={{ display: 'flex', alignItems: 'flex-start', gap: 10, cursor: 'pointer', marginBottom: 14 }}
              >
                <input
                  type="checkbox"
                  style={{ marginTop: 3 }}
                  checked={accepted}
                  onChange={(e) => setAccepted(e.target.checked)}
                />
                <span style={{ fontSize: 13, lineHeight: 1.5 }}>
                  {t('association.profile.mandate.step3.acceptLabel')}
                </span>
              </label>
              <button
                className="btn btn-primary"
                disabled={!canSign || isSigning}
                style={!canSign ? { opacity: 0.4, cursor: 'not-allowed' } : undefined}
                onClick={handleSign}
              >
                ✍️ {t('association.profile.mandate.step3.signBtn')}
              </button>
              <span style={{ fontSize: 12, color: 'var(--slate-lavender)', marginLeft: 10 }}>
                {t('association.profile.mandate.step3.timestampNote')}
              </span>
            </div>
          </div>
        </>
      )}

      {/* ── Vue : signé ───────────────────────────────────────────────────── */}
      {state.signed && (
        <>
          <div
            className="acc-card"
            style={{
              marginBottom: 20,
              background: 'linear-gradient(135deg,var(--white) 0%,rgba(78,205,196,.06) 100%)',
              borderColor: 'rgba(78,205,196,.3)',
            }}
          >
            <div className="acc-card-head">
              <div>
                <div className="acc-card-title">
                  ✓ {t('association.profile.mandate.signed.title')}
                </div>
                <div className="acc-card-sub">
                  {t('association.profile.mandate.signed.sub')}
                </div>
              </div>
            </div>
          </div>

          <div className="card no-hover" style={{ marginBottom: 16 }}>
            <div className="card-h">
              <h3>{t('association.profile.mandate.signed.detailsTitle')}</h3>
            </div>
            <div className="card-b" style={{ padding: 0 }}>
              {[
                { labelKey: 'association.profile.mandate.signed.reference', value: state.reference ?? '—' },
                { labelKey: 'association.profile.mandate.signed.signedAt', value: formatDate(state.signedAt) },
                { labelKey: 'association.profile.mandate.signed.eligibility', value: eligibilityLabel(state.eligibility) },
                { labelKey: 'association.profile.mandate.signed.retention', value: t('association.profile.mandate.signed.retentionValue') },
              ].map((row, idx, arr) => (
                <div
                  key={row.labelKey}
                  style={{
                    padding: '14px 22px',
                    borderBottom: idx < arr.length - 1 ? '1px solid var(--mist-lavender)' : undefined,
                    display: 'flex',
                    justifyContent: 'space-between',
                    alignItems: 'center',
                  }}
                >
                  <span style={{ fontSize: 13, color: 'var(--slate-lavender)' }}>
                    {t(row.labelKey as Parameters<typeof t>[0])}
                  </span>
                  <span style={{ fontFamily: row.labelKey.includes('reference') ? 'monospace' : undefined, fontSize: 13, fontWeight: 700, color: 'var(--ink-navy)' }}>
                    {row.value}
                  </span>
                </div>
              ))}
            </div>
          </div>

          <div style={{ display: 'flex', gap: 10, flexWrap: 'wrap' }}>
            <button className="btn btn-secondary btn-sm" onClick={onDownloadPdf}>
              📥 {t('association.profile.mandate.signed.downloadPdf')}
            </button>
            <button
              className="btn btn-secondary btn-sm"
              onClick={() => setShowRevokeModal(true)}
              style={{ color: 'var(--warm-coral)', marginLeft: 'auto' }}
            >
              {t('association.profile.mandate.signed.revokeBtn')}
            </button>
          </div>
        </>
      )}

      {/* ── Modal d'alerte : signataire habilité manquant ─────────────────── */}
      {showSignerWarning && (
        <div className="ov" onClick={() => setShowSignerWarning(false)}>
          <div className="mod" onClick={(e) => e.stopPropagation()}>
            <div className="mod-h">
              <h3>{t('association.profile.mandate.signerWarning.title')}</h3>
              <button className="mod-x" onClick={() => setShowSignerWarning(false)}>✕</button>
            </div>
            <div className="mod-b">
              {missingSignerName && (
                <p className="alert alert-warning alert-spaced">
                  {t('association.profile.mandate.signerWarning.missingSignerName')}
                </p>
              )}
              {missingSignerRole && (
                <p className="alert alert-warning alert-spaced">
                  {t('association.profile.mandate.signerWarning.missingSignerRole')}
                </p>
              )}
              <div style={{ display: 'flex', justifyContent: 'flex-end' }}>
                <button
                  className="btn btn-primary btn-sm"
                  onClick={() => setShowSignerWarning(false)}
                >
                  {t('association.profile.mandate.signerWarning.close')}
                </button>
              </div>
            </div>
          </div>
        </div>
      )}

      {/* ── Modal de confirmation de révocation ──────────────────────────── */}
      {showRevokeModal && (
        <div className="ov" onClick={() => setShowRevokeModal(false)}>
          <div className="mod" onClick={(e) => e.stopPropagation()}>
            <div className="mod-h">
              <h3>{t('association.profile.mandate.revokeModal.title')}</h3>
              <button className="mod-x" onClick={() => setShowRevokeModal(false)}>✕</button>
            </div>
            <div className="mod-b">
              <p style={{ fontSize: 14, lineHeight: 1.6, marginBottom: 20 }}>
                {t('association.profile.mandate.revokeModal.body')}
              </p>
              <div style={{ display: 'flex', gap: 10, justifyContent: 'flex-end' }}>
                <button
                  className="btn btn-secondary btn-sm"
                  onClick={() => setShowRevokeModal(false)}
                >
                  {t('association.profile.mandate.revokeModal.cancel')}
                </button>
                <button
                  className="btn btn-primary btn-sm"
                  style={{ background: 'var(--warm-coral)', borderColor: 'var(--warm-coral)' }}
                  disabled={isRevoking}
                  onClick={handleRevoke}
                >
                  {t('association.profile.mandate.revokeModal.confirm')}
                </button>
              </div>
            </div>
          </div>
        </div>
      )}
    </>
  );
}
