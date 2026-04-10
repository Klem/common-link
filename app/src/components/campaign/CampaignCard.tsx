'use client';

import { useRouter } from 'next/navigation';
import { useLocale, useTranslations } from 'next-intl';
import { ROUTES } from '@/lib/routes';
import type { CampaignSummaryDto, CampaignStatus } from '@/types/campaign';

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

/** Returns Tailwind classes and label for a given campaign status pill. */
function statusPill(status: CampaignStatus, t: (key: string) => string): { classes: string; label: string } {
  switch (status) {
    case 'LIVE':
      return {
        classes: 'bg-green/[12%] border border-green/25 text-green',
        label: `● ${t('campaigns.status.live')}`,
      };
    case 'ENDED':
      return {
        classes: 'bg-red/10 text-red',
        label: `✕ ${t('campaigns.status.ended')}`,
      };
    default:
      return {
        classes: 'bg-muted/20 text-text-2',
        label: `⚙️ ${t('campaigns.status.draft')}`,
      };
  }
}

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

  const pill = statusPill(campaign.status, t);
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
      className="bg-bg-2 border border-border rounded-xl p-[20px] hover:border-green/30 transition cursor-pointer relative"
    >
      {/* Delete button */}
      <button
        onClick={handleDelete}
        aria-label={t('campaigns.delete')}
        className="absolute top-[14px] right-[14px] w-[24px] h-[24px] rounded-md bg-red/8 text-red text-[12px] flex items-center justify-center hover:bg-red/20 transition"
      >
        ✕
      </button>

      {/* Top row: emoji + name + status pill */}
      <div className="flex items-center gap-[10px] pr-[32px]">
        <span className="text-[28px] leading-none flex-shrink-0">{campaign.emoji}</span>
        <span className="font-display font-bold text-[16px] text-text truncate flex-1">
          {campaign.name}
        </span>
        <span className={`flex-shrink-0 px-[8px] py-[3px] rounded-full text-[11px] font-semibold ${pill.classes}`}>
          {pill.label}
        </span>
      </div>

      {/* Description */}
      {campaign.description && (
        <p className="text-[12.5px] text-text-2 line-clamp-2 mt-[8px]">{campaign.description}</p>
      )}

      {/* Progress bar */}
      <div className="h-[5px] bg-bg-3 rounded-full overflow-hidden mt-[12px]">
        <div
          className="h-full bg-gradient-to-r from-green to-cyan rounded-full transition-all duration-300"
          style={{ width: `${progress}%` }}
        />
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
  );
}
