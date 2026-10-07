'use client';

import Link from 'next/link';
import { useLocale, useTranslations } from 'next-intl';
import { useDonorFeed } from '@/hooks/dashboard/useDonorFeed';
import { DonorFeedItemType, type DonorFeedItemDto } from '@/types/donor';
import { ROUTES } from '@/lib/routes';

function fmtDate(iso: string): string {
  return new Intl.DateTimeFormat('fr-FR', { day: '2-digit', month: 'short' }).format(new Date(iso));
}

const ICON_BY_TYPE: Record<DonorFeedItemDto['type'], string> = {
  [DonorFeedItemType.PAYOUT_CONFIRMED]: '💸',
  [DonorFeedItemType.MILESTONE_REACHED]: '🎯',
  [DonorFeedItemType.CAMPAIGN_COMPLETED]: '🏁',
};

/** Types shown in the "new proof" column vs. the "still to do" column (sprint 5, L18, §2.1). */
const PROOF_TYPES: DonorFeedItemDto['type'][] = [DonorFeedItemType.PAYOUT_CONFIRMED, DonorFeedItemType.MILESTONE_REACHED];

function FeedColumn({
  items,
  emptyLabel,
  locale,
}: {
  items: DonorFeedItemDto[];
  emptyLabel: string;
  locale: string;
}) {
  if (items.length === 0) {
    return <p className="since-empty">{emptyLabel}</p>;
  }
  return (
    <div className="since-items">
      {items.map((item, i) => (
        <Link
          key={`${item.campaignId}-${item.type}-${i}`}
          href={`/${locale}${ROUTES.DONOR_CAMPAIGN_REPORT(item.campaignId)}`}
          className="since-item"
        >
          <span className="since-item-icon" aria-hidden="true">{ICON_BY_TYPE[item.type]}</span>
          <span className="since-item-body">
            <strong>{item.campaignName}</strong>
            <span>{item.label}</span>
          </span>
          <time dateTime={item.occurredAt} className="since-item-arrow" aria-hidden="true">
            →
          </time>
        </Link>
      ))}
    </div>
  );
}

/**
 * "Depuis votre dernière visite" home block (L15, restricted to the in-app feed this sprint — the
 * monthly email is chantier CD6). Read-only display; `useDonorFeed` marks the feed as seen once
 * fetched. Split into two columns per the maquette (sprint 5, L18): new proof (payouts confirmed,
 * milestones reached) vs. still to consult (campaigns completed, bilan not yet opened).
 */
export function RecentActivityBlock() {
  const t = useTranslations('dashboard.donor.engagement.feed');
  const locale = useLocale();
  const { feed, isLoading } = useDonorFeed();

  if (isLoading) {
    return (
      <p className="text-sm text-text-2" aria-live="polite">
        {t('loading')}
      </p>
    );
  }

  const proofs = feed.filter((item) => PROOF_TYPES.includes(item.type));
  const actions = feed.filter((item) => !PROOF_TYPES.includes(item.type));

  return (
    <section className="since-grid" aria-label={t('sectionTitle')}>
      <div className="since-block proofs-block">
        <div className="since-head">
          <span className="since-pulse" aria-hidden="true" />
          <h2>{t('proofsTitle')}</h2>
        </div>
        <FeedColumn items={proofs} emptyLabel={t('empty')} locale={locale} />
      </div>
      <div className="since-block actions-block">
        <div className="since-head">
          <span className="since-pulse" aria-hidden="true" />
          <h2>{t('actionsTitle')}</h2>
        </div>
        <FeedColumn items={actions} emptyLabel={t('empty')} locale={locale} />
      </div>
    </section>
  );
}
