'use client';

import { useRouter } from 'next/navigation';
import { useLocale, useTranslations } from 'next-intl';
import { ROUTES } from '@/lib/routes';
import type { CampaignSummaryDto } from '@/types/campaign';
import { CampaignStatus } from '@/types/campaign';

/** Props for {@link CampaignCard}. */
interface CampaignCardProps {
  /** Campaign summary data to display. */
  campaign: CampaignSummaryDto;
  /** Callback invoked when the delete button is clicked. */
  onDelete: (id: string) => void;
}

/** Formats a number as a EUR currency string using French locale. */
function formatEur(amount: number): string {
  return new Intl.NumberFormat('fr-FR', { style: 'currency', currency: 'EUR' }).format(amount);
}

const STATUS_BADGE: Record<CampaignStatus, string> = {
  [CampaignStatus.LIVE]:  'badge badge-active',
  [CampaignStatus.ENDED]: 'badge badge-error',
  [CampaignStatus.DRAFT]: 'badge badge-neutral',
};

const STATUS_I18N: Record<CampaignStatus, string> = {
  [CampaignStatus.LIVE]:  'campaigns.status.live',
  [CampaignStatus.ENDED]: 'campaigns.status.ended',
  [CampaignStatus.DRAFT]: 'campaigns.status.draft',
};

/**
 * Card displaying a campaign summary in the association campaign list.
 *
 * Clicking anywhere on the card navigates to the campaign editor.
 * The delete button (top-right) triggers the onDelete callback without navigation.
 */
export function CampaignCard({ campaign, onDelete }: CampaignCardProps) {
  const router = useRouter();
  const locale = useLocale();
  const t = useTranslations('dashboard');

  const progress = campaign.goal > 0 ? Math.min((campaign.raised / campaign.goal) * 100, 100) : 0;

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
      className="card relative cursor-pointer"
    >
      <div className="card-body">
        {/* Delete button */}
        <button
          onClick={handleDelete}
          aria-label={t('campaigns.delete')}
          className="absolute top-[14px] right-[14px] w-[24px] h-[24px] rounded-md bg-red/8 text-red text-[12px] flex items-center justify-center hover:bg-red/20 transition"
        >
          🗑
        </button>

        {/* Top row: emoji + name + status pill */}
        <div className="flex items-center gap-[10px] pr-[32px]">
          <span className="text-[28px] leading-none flex-shrink-0">{campaign.emoji}</span>
          <span className="font-display font-bold text-[16px] text-text truncate flex-1">
            {campaign.name}
          </span>
          <span className={`flex-shrink-0 ${STATUS_BADGE[campaign.status]}`}>
            {t(STATUS_I18N[campaign.status])}
          </span>
        </div>

        {/* Description */}
        {campaign.description && (
          <p className="text-[12.5px] text-text-2 line-clamp-2 mt-[8px]">{campaign.description}</p>
        )}

        {/* Progress bar */}
        <div className="progress-bar mt-[12px]">
          <div className="progress-fill teal" style={{ width: `${progress}%` }} />
        </div>

        {/* Bottom row: raised / goal + milestones */}
        <div className="flex justify-between text-[12px] text-text-2 mt-[8px]">
          <span>
            {formatEur(campaign.raised)} / {formatEur(campaign.goal)}
          </span>
          <span>
            {campaign.milestoneCount} {t('campaigns.milestones')}
          </span>
        </div>
      </div>
    </div>
  );
}
