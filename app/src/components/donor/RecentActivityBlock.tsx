'use client';

import { useTranslations } from 'next-intl';
import { useDonorFeed } from '@/hooks/dashboard/useDonorFeed';
import { DonorFeedItemType, type DonorFeedItemDto } from '@/types/donor';

function fmtDate(iso: string): string {
  return new Intl.DateTimeFormat('fr-FR', { day: '2-digit', month: 'short' }).format(new Date(iso));
}

const ICON_BY_TYPE: Record<DonorFeedItemDto['type'], string> = {
  [DonorFeedItemType.PAYOUT_CONFIRMED]: '💸',
  [DonorFeedItemType.MILESTONE_REACHED]: '🎯',
  [DonorFeedItemType.CAMPAIGN_COMPLETED]: '🏁',
};

/**
 * "Depuis votre dernière visite" home block (L15, restricted to the in-app feed this sprint — the
 * monthly email is chantier CD6). Read-only display; `useDonorFeed` marks the feed as seen once
 * fetched.
 */
export function RecentActivityBlock() {
  const t = useTranslations('dashboard.donor.engagement.feed');
  const { feed, isLoading } = useDonorFeed();

  if (isLoading) {
    return (
      <p className="text-sm text-text-2" aria-live="polite">
        {t('loading')}
      </p>
    );
  }

  if (feed.length === 0) {
    return <p className="text-sm text-text-2">{t('empty')}</p>;
  }

  return (
    <ul className="flex flex-col gap-3">
      {feed.map((item, i) => (
        <li key={`${item.campaignId}-${item.type}-${i}`} className="flex items-start gap-2 text-sm">
          <span aria-hidden="true">{ICON_BY_TYPE[item.type]}</span>
          <span className="flex-1">{item.label}</span>
          <time dateTime={item.occurredAt} className="text-xs text-text-2 whitespace-nowrap">
            {fmtDate(item.occurredAt)}
          </time>
        </li>
      ))}
    </ul>
  );
}
