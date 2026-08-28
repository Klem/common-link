'use client';

import { useEffect, useState } from 'react';
import { useTranslations } from 'next-intl';
import type { CampaignDto, BudgetSectionDto } from '@/types/campaign';
import { BudgetSide } from '@/types/campaign';
import { VerificationStatus } from '@/types/association';
import { LegalDocumentType } from '@/types/legal';
import { BankSetupStatus } from '@/lib/bankSetupStatus';
import { getLegalAcceptanceState } from '@/lib/api/legal';
import { LegalDocumentModal } from '@/components/legal/LegalDocumentModal';
import { LegalLinkButton } from '@/components/legal/LegalLinkButton';

/** Visual variant of an account-status row, mapped to the `.pp-row.*` CSS modifiers. */
interface StatusRowSpec {
  /** `missing` blocks publication, `boost` is informational, `ok` is satisfied. */
  cls: 'missing' | 'boost' | 'ok';
  icon: string;
}

/**
 * One row per Mollie-derived bank status. Only `COMPLETED` allows publishing, but the other four
 * must stay distinguishable: an association in review is not an association that never connected.
 */
const BANK_ROWS: Record<BankSetupStatus, StatusRowSpec> = {
  [BankSetupStatus.NOT_CONNECTED]: { cls: 'missing', icon: '!' },
  [BankSetupStatus.NEEDS_DATA]: { cls: 'missing', icon: '!' },
  [BankSetupStatus.IN_REVIEW]: { cls: 'missing', icon: '⏳' },
  [BankSetupStatus.COMPLETED]: { cls: 'ok', icon: '✓' },
  [BankSetupStatus.BROKEN]: { cls: 'missing', icon: '!' },
};

/**
 * One row per KYC status. Only `VERIFIED` allows publishing: LCB-FT forbids a campaign from
 * collecting donations before the association's KYB dossier is validated, and the backend
 * enforces the same rule in `CampaignService.preparePublish` (rule 8 — every click is replayable).
 */
const VERIF_ROWS: Record<VerificationStatus, StatusRowSpec> = {
  [VerificationStatus.UNVERIFIED]: { cls: 'missing', icon: '!' },
  [VerificationStatus.PENDING]: { cls: 'missing', icon: '⏳' },
  [VerificationStatus.REJECTED]: { cls: 'missing', icon: '!' },
  [VerificationStatus.VERIFIED]: { cls: 'ok', icon: '✓' },
};

interface PrePublishModalProps {
  campaign: CampaignDto;
  /** KYC lifecycle state of the association profile (`association_profiles.verification_status`). */
  verificationStatus: VerificationStatus;
  /** Bank-setup state derived from the Mollie KYC DTO. */
  bankStatus: BankSetupStatus;
  /**
   * False while the Mollie request is still in flight. `bankStatus` then defaults to
   * `NOT_CONNECTED`, so the modal shows a neutral loading row instead of claiming a fully
   * onboarded association has no bank account.
   */
  mollieResolved: boolean;
  /** Mollie hosted-onboarding deep link, used by the `NEEDS_DATA` call-to-action. */
  mollieDashboardUrl: string | null;
  onClose: () => void;
  /** @param cguAccepted Whether to send `cguAccepted: true` on the publish request. */
  onConfirm: (cguAccepted: boolean) => Promise<void>;
}

function sumSide(sections: BudgetSectionDto[], side: BudgetSide): number {
  return sections
    .filter((s) => s.side === side)
    .flatMap((s) => s.items)
    .reduce((acc, item) => acc + item.amount, 0);
}

export function PrePublishModal({
  campaign,
  verificationStatus,
  bankStatus,
  mollieResolved,
  mollieDashboardUrl,
  onClose,
  onConfirm,
}: PrePublishModalProps) {
  const t = useTranslations('dashboard.campaigns.publish');
  const tNav = useTranslations('dashboard.campaigns.editor.tabs');

  // Art. 1740 A CGI proof of acceptance. `cguState === null` while loading; once loaded,
  // `cguState.accepted` means this association already has a standing acceptance of the current
  // CGU version — the checkbox then renders pre-checked and disabled instead of blocking.
  const [attempted, setAttempted] = useState(false);
  const [publishError, setPublishError] = useState<string | null>(null);
  const [cguState, setCguState] = useState<{ accepted: boolean; version: string } | null>(null);
  const [cguChecked, setCguChecked] = useState(false);
  useEffect(() => {
    getLegalAcceptanceState(LegalDocumentType.CGU)
      .then((state) => setCguState({ accepted: state.accepted, version: state.currentVersion }))
      .catch(() => setCguState({ accepted: false, version: '' }));
  }, []);
  const cguAccepted = cguState?.accepted === true || cguChecked;
  const [showCguDoc, setShowCguDoc] = useState(false);

  const expenses = sumSide(campaign.budgetSections, BudgetSide.EXPENSE);
  const revenues = sumSide(campaign.budgetSections, BudgetSide.REVENUE);
  const budgetBalanced = expenses > 0 && revenues > 0 && Math.abs(revenues - expenses) < 1;

  /**
   * Publication blockers. Each predicate is mirrored exactly — tolerance included — by
   * `CampaignService.preparePublish` (rule 8): loosening one side only makes the button enable
   * and the PUT answer 422.
   *
   * `budget` and `impactGoals` used to be mere recommendations. They now block: a campaign is
   * published with a balanced prévisionnel and a stated expected outcome, or it is not published.
   */
  const blockers = [
    { ok: campaign.name.trim().length > 0, labelKey: 'required.name', tab: 'info' },
    { ok: (campaign.description?.trim().length ?? 0) >= 10, labelKey: 'required.description', tab: 'info' },
    { ok: campaign.startDate !== null && campaign.endDate !== null, labelKey: 'required.dates', tab: 'info' },
    { ok: campaign.goal > 0, labelKey: 'required.goal', tab: 'info' },
    { ok: budgetBalanced, labelKey: 'required.budget', tab: 'budget' },
    { ok: (campaign.impactGoals?.trim().length ?? 0) >= 20, labelKey: 'required.impactGoals', tab: 'info' },
  ];

  const boosters = [
    {
      ok: campaign.milestones.length >= 1,
      warn: false,
      labelKey: 'recommended.milestones',
      tab: 'milestones',
    },
    {
      ok: (campaign.reason?.trim().length ?? 0) >= 20,
      warn: false,
      labelKey: 'recommended.reason',
      tab: 'info',
    },
  ];

  const bankReady = mollieResolved && bankStatus === BankSetupStatus.COMPLETED;
  const kybReady = verificationStatus === VerificationStatus.VERIFIED;
  const accountComplete = bankReady && kybReady;

  const handlePublish = async () => {
    setAttempted(true);
    try {
      await onConfirm(cguAccepted);
    } catch {
      setPublishError(t('publishRefused'));
    }
  };

  return (
    <div className="ov on" onClick={(e) => { if (e.target === e.currentTarget) onClose(); }}>
      <div className="mod mod-md">
        <div className="mod-h">
          <h3>{t('title')}</h3>
          <button className="mod-x" onClick={onClose}>✕</button>
        </div>

        <div className="mod-b">
          {/* — Requis — */}
          <div className="pp-section">
            <div className="pp-section-title">{t('required.section')}</div>
            {blockers.map(({ ok, labelKey, tab }) => (
              <div key={labelKey} className={`pp-row ${ok ? 'ok' : 'missing'}`}>
                <div className="pp-row-ic">{ok ? '✓' : '!'}</div>
                <div className="pp-row-lbl">{t(labelKey as Parameters<typeof t>[0])}</div>
                {!ok && (
                  <button
                    className="pp-row-link"
                    onClick={() => { onClose(); }}
                    data-tab={tab}
                  >
                    {t('fill')}
                  </button>
                )}
              </div>
            ))}
          </div>

          {/* — Recommandé — */}
          <div className="pp-section">
            <div className="pp-section-title">{t('recommended.section')}</div>
            {boosters.map(({ ok, warn, labelKey, tab }) => {
              const cls = ok ? 'ok' : 'boost';
              const ic = ok ? '✓' : warn ? '⚠' : '★';
              return (
                <div key={labelKey} className={`pp-row ${cls}`}>
                  <div className="pp-row-ic">{ic}</div>
                  <div className="pp-row-lbl">{t(labelKey as Parameters<typeof t>[0])}</div>
                  {!ok && (
                    <button
                      className="pp-row-link"
                      onClick={() => { onClose(); }}
                      data-tab={tab}
                    >
                      {t('add')}
                    </button>
                  )}
                </div>
              );
            })}
          </div>

          {/* — CGU (art. 1740 A CGI) — */}
          <div className="pp-section">
            <div className="pp-section-title">{t('cgu.section')}</div>
            <div className={`pp-row ${cguAccepted ? 'ok' : 'missing'}`}>
              <input
                type="checkbox"
                id="pp-cgu-checkbox"
                checked={cguAccepted}
                disabled={cguState === null || cguState.accepted}
                onChange={(e) => setCguChecked(e.target.checked)}
              />
              <label htmlFor="pp-cgu-checkbox" className="pp-row-lbl">
                {t('cgu.label')}{' '}
                <LegalLinkButton className="pp-row-link" onClick={() => setShowCguDoc(true)}>
                  {t('cgu.link')}
                </LegalLinkButton>
              </label>
            </div>
          </div>

          {/* — Statut compte — */}
          <div className="pp-section">
            <div className="pp-section-title">{t('account.section')}</div>
            {accountComplete ? (
              <div className="pp-row ok">
                <div className="pp-row-ic">🚀</div>
                <div className="pp-row-lbl">{t('account.complete')}</div>
              </div>
            ) : (
              <>
                {!mollieResolved ? (
                  <div className="pp-row boost">
                    <div className="pp-row-ic">⏳</div>
                    <div className="pp-row-lbl">{t('account.loading')}</div>
                  </div>
                ) : (
                  <div className={`pp-row ${BANK_ROWS[bankStatus].cls}`}>
                    <div className="pp-row-ic">{BANK_ROWS[bankStatus].icon}</div>
                    <div className="pp-row-lbl">{t(`account.bank.${bankStatus}`)}</div>
                    {bankStatus === BankSetupStatus.NEEDS_DATA && mollieDashboardUrl !== null && (
                      <a
                        className="pp-row-link"
                        href={mollieDashboardUrl}
                        target="_blank"
                        rel="noopener noreferrer"
                      >
                        {t('account.completeBank')}
                      </a>
                    )}
                  </div>
                )}
                <div className={`pp-row ${VERIF_ROWS[verificationStatus].cls}`}>
                  <div className="pp-row-ic">{VERIF_ROWS[verificationStatus].icon}</div>
                  <div className="pp-row-lbl">{t(`account.verif.${verificationStatus}`)}</div>
                </div>
              </>
            )}
          </div>
        </div>

        {publishError && (
          <div className="pp-row missing">
            <div className="pp-row-ic">!</div>
            <div className="pp-row-lbl">{publishError}</div>
          </div>
        )}

        <div className="mod-f">
          <button className="btn btn-secondary" onClick={onClose}>
            {t('continueEditing')}
          </button>
          <button
            className="btn btn-primary"
            onClick={handlePublish}
            disabled={attempted}
          >
            {t('confirm')}
          </button>
        </div>
      </div>

      {showCguDoc && (
        <LegalDocumentModal documentType={LegalDocumentType.CGU} onClose={() => setShowCguDoc(false)} />
      )}
    </div>
  );
}
