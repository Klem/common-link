'use client';

import { useTranslations } from 'next-intl';
import type { CampaignDto, BudgetSectionDto } from '@/types/campaign';
import { BudgetSide } from '@/types/campaign';

interface PrePublishModalProps {
  campaign: CampaignDto;
  verified: boolean;
  bankConnected: boolean;
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
  verified,
  bankConnected,
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
  const canPublish = allReqOk && bankConnected;

  return (
    <div className="ov on" onClick={(e) => { if (e.target === e.currentTarget) onClose(); }}>
      <div className="mod" style={{ maxWidth: '560px' }}>
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
            {!bankConnected ? (
              <div className="pp-row missing">
                <div className="pp-row-ic">!</div>
                <div className="pp-row-lbl">{t('account.noBank')}</div>
              </div>
            ) : !verified ? (
              <div className="pp-row boost">
                <div className="pp-row-ic">i</div>
                <div className="pp-row-lbl">{t('account.notVerified')}</div>
              </div>
            ) : (
              <div className="pp-row ok">
                <div className="pp-row-ic">🚀</div>
                <div className="pp-row-lbl">{t('account.complete')}</div>
              </div>
            )}
          </div>
        </div>

        <div className="mod-f" style={{ display: 'flex', justifyContent: 'flex-end', gap: '10px' }}>
          <button className="btn btn-secondary" onClick={onClose}>
            {t('continueEditing')}
          </button>
          <button
            className="btn btn-primary"
            onClick={onConfirm}
            disabled={!canPublish}
            style={!canPublish ? { opacity: 0.4, cursor: 'not-allowed' } : undefined}
          >
            {canPublish ? t('confirm') : t('complete')}
          </button>
        </div>
      </div>
    </div>
  );
}
