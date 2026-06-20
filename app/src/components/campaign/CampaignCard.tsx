'use client';

import { useRouter } from 'next/navigation';
import { useLocale, useTranslations } from 'next-intl';
import { ROUTES } from '@/lib/routes';
import type { CampaignSummaryDto } from '@/types/campaign';
import { CampaignStatus } from '@/types/campaign';

interface CampaignCardProps {
  campaign: CampaignSummaryDto;
  onDelete: (id: string) => void;
}

function formatEur(amount: number): string {
  return new Intl.NumberFormat('fr-FR', { style: 'currency', currency: 'EUR' }).format(amount);
}

const STATUS_BADGE_CLASS: Record<CampaignStatus, string> = {
  [CampaignStatus.LIVE]:    'badge badge-active',
  [CampaignStatus.PRIVATE]: 'badge badge-draft',
  [CampaignStatus.ENDED]:   'badge badge-ended',
  [CampaignStatus.DRAFT]:   'badge badge-draft',
};

const STATUS_BADGE_I18N: Record<CampaignStatus, string> = {
  [CampaignStatus.LIVE]:    'campaigns.badge.live',
  [CampaignStatus.PRIVATE]: 'campaigns.badge.draft',
  [CampaignStatus.ENDED]:   'campaigns.badge.ended',
  [CampaignStatus.DRAFT]:   'campaigns.badge.draft',
};

export function CampaignCard({ campaign, onDelete }: CampaignCardProps) {
  const router = useRouter();
  const locale = useLocale();
  const t = useTranslations('dashboard');

  const pct = campaign.goal > 0
    ? Math.round((campaign.raised / campaign.goal) * 100)
    : 0;
  const overfund = pct > 100;
  const isDraft = campaign.status === CampaignStatus.DRAFT;
  const isEnded = campaign.status === CampaignStatus.ENDED;
  const showNoBudget = isDraft && campaign.goal === 0;

  const barColor = isEnded || overfund ? 'var(--soft-amber)' : undefined;

  const handleCardClick = () => {
    router.push(`/${locale}${ROUTES.ASSOCIATION_CAMPAIGNS}/${campaign.id}`);
  };

  const handleDelete = (e: React.MouseEvent) => {
    e.stopPropagation();
    if (window.confirm(t('campaigns.deleteConfirm'))) {
      onDelete(campaign.id);
    }
  };

  return (
    <div
      role="button"
      tabIndex={0}
      onClick={handleCardClick}
      onKeyDown={(e) => e.key === 'Enter' && handleCardClick()}
      className="camp-card"
    >
      {/* Image / placeholder */}
      <div className="camp-card-img">
        <span className="camp-card-img-emoji">{campaign.emoji}</span>
        <div className="camp-card-badge-row">
          <span className={STATUS_BADGE_CLASS[campaign.status]}>
            {t(STATUS_BADGE_I18N[campaign.status])}
          </span>
        </div>
      </div>

      <div className="camp-card-body">
        <h3>{campaign.name}</h3>

        {campaign.description && (
          <p className="camp-card-desc">{campaign.description}</p>
        )}

        {/* Progress */}
        {showNoBudget ? (
          <p style={{ fontSize: '12px', color: 'var(--slate-lavender)', marginBottom: '12px' }}>
            {t('campaigns.draftNoBudget')}
          </p>
        ) : (
          <div className="camp-card-progress">
            <div className="pbar" style={{ marginBottom: '6px' }}>
              <div
                className="pfill"
                style={{ width: `${Math.min(pct, 100)}%`, ...(barColor ? { background: barColor } : {}) }}
              />
            </div>
            <div style={{ display: 'flex', justifyContent: 'space-between', fontSize: '13px' }}>
              <strong>{formatEur(campaign.raised)} / {campaign.goal > 0 ? formatEur(campaign.goal) : '—'}</strong>
              <span style={{ color: overfund ? 'var(--soft-amber)' : 'var(--slate-lavender)', fontWeight: overfund ? 700 : 400 }}>
                {pct}%{overfund ? ' 🎉' : ''}
              </span>
            </div>
          </div>
        )}

        {/* Stats */}
        <div className="camp-card-stats">
          <span>🎯 {campaign.milestoneCount} {t('campaigns.milestones')}</span>
        </div>

        {/* Actions */}
        <div className="camp-card-actions">
          {isDraft && (
            <button className="btn btn-sm btn-primary" style={{ flex: 1 }} onClick={(e) => { e.stopPropagation(); handleCardClick(); }}>
              {t('campaigns.actionContinue')}
            </button>
          )}
          {isEnded && (
            <button className="btn btn-sm btn-secondary" style={{ flex: 1 }} onClick={(e) => { e.stopPropagation(); handleCardClick(); }}>
              {t('campaigns.actionReport')}
            </button>
          )}
          {campaign.status === CampaignStatus.LIVE && (
            <>
              <button className="btn btn-sm btn-primary" style={{ flex: 1 }} onClick={(e) => { e.stopPropagation(); handleCardClick(); }}>
                {t('campaigns.actionManage')}
              </button>
              <button className="btn btn-sm btn-secondary" onClick={(e) => e.stopPropagation()}>
                {t('campaigns.actionShare')}
              </button>
            </>
          )}
          <button
            className="btn btn-sm btn-ghost"
            onClick={handleDelete}
            aria-label={t('campaigns.delete')}
            title={t('campaigns.delete')}
          >
            🗑
          </button>
        </div>
      </div>
    </div>
  );
}
