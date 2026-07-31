'use client';

import { useTranslations } from 'next-intl';
import type { CampaignDto, BudgetSectionDto } from '@/types/campaign';
import { BudgetSide } from '@/types/campaign';
import { VerificationStatus } from '@/types/association';
import { BankSetupStatus } from '@/lib/bankSetupStatus';

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

/** One row per KYC status. None of them blocks publication — the dossier only gates public listing. */
const VERIF_ROWS: Record<VerificationStatus, StatusRowSpec> = {
  [VerificationStatus.UNVERIFIED]: { cls: 'boost', icon: 'i' },
  [VerificationStatus.PENDING]: { cls: 'boost', icon: '⏳' },
  [VerificationStatus.REJECTED]: { cls: 'boost', icon: '!' },
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
  onConfirm: () => void;
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

  const expenses = sumSide(campaign.budgetSections, BudgetSide.EXPENSE);
  const revenues = sumSide(campaign.budgetSections, BudgetSide.REVENUE);
  const budgetBalanced = expenses > 0 && revenues > 0 && Math.abs(revenues - expenses) < 1;
  const budgetPartial = (expenses > 0 || revenues > 0) && !budgetBalanced;

  const blockers = [
    { ok: campaign.name.trim().length > 0, labelKey: 'required.name', tab: 'info' },
    { ok: (campaign.description?.trim().length ?? 0) >= 10, labelKey: 'required.description', tab: 'info' },
    { ok: campaign.startDate !== null && campaign.endDate !== null, labelKey: 'required.dates', tab: 'info' },
    { ok: campaign.goal > 0, labelKey: 'required.goal', tab: 'info' },
  ];

  const boosters = [
    { ok: budgetBalanced, warn: budgetPartial, labelKey: 'recommended.budget', tab: 'budget' },
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
    {
      ok: (campaign.impactGoals?.trim().length ?? 0) >= 20,
      warn: false,
      labelKey: 'recommended.impactGoals',
      tab: 'info',
    },
  ];

  const allReqOk = blockers.every((b) => b.ok);
  const bankReady = mollieResolved && bankStatus === BankSetupStatus.COMPLETED;
  const canPublish = allReqOk && bankReady;
  const accountComplete = bankReady && verificationStatus === VerificationStatus.VERIFIED;

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

        <div className="mod-f">
          <button className="btn btn-secondary" onClick={onClose}>
            {t('continueEditing')}
          </button>
          <button
            className="btn btn-primary"
            onClick={onConfirm}
            disabled={!canPublish}
          >
            {canPublish ? t('confirm') : t('complete')}
          </button>
        </div>
      </div>
    </div>
  );
}
