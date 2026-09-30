'use client';

import { useMemo, useState } from 'react';
import { useTranslations } from 'next-intl';
import { StatCard, EmptyStateCard } from '@/components/dashboard';
import { CampaignStoryModal } from '@/components/donor/CampaignStoryModal';
import { ShareImpactModal } from '@/components/donor/ShareImpactModal';
import { useDonorImpacts } from '@/hooks/dashboard/useDonorImpacts';
import { useDonorStats } from '@/hooks/dashboard/useDonorStats';
import { impactWording, withTerminalPunctuation } from '@/lib/impactWording';
import type { DonorImpactDto } from '@/types/donor';

const PAGE_SIZE = 9;

function fmtEur(amount: number): string {
  return new Intl.NumberFormat('fr-FR', { style: 'currency', currency: 'EUR' }).format(amount);
}

/**
 * "Impact de mes dons" gallery: cumulative contribution (reuses `DonorStatsDto.totalDonated`, no
 * new call), cards filterable by cause, client-side "Voir plus" pagination (donor campaign counts
 * stay low, no need for a server-paginated endpoint).
 */
export function ImpactGallery() {
  const t = useTranslations('dashboard.donor.impact');
  const { impacts, isLoading, error } = useDonorImpacts();
  const { stats, isLoading: statsLoading } = useDonorStats();
  const [category, setCategory] = useState<string | null>(null);
  const [visibleCount, setVisibleCount] = useState(PAGE_SIZE);
  const [shareTarget, setShareTarget] = useState<DonorImpactDto | null>(null);
  const [storyTarget, setStoryTarget] = useState<DonorImpactDto | null>(null);

  const categories = useMemo(
    () => Array.from(new Set(impacts.map((i) => i.category).filter((c): c is string => c !== null))).sort(),
    [impacts],
  );
  const filtered = useMemo(
    () => (category === null ? impacts : impacts.filter((i) => i.category === category)),
    [impacts, category],
  );
  const visible = filtered.slice(0, visibleCount);

  function selectCategory(next: string | null): void {
    setCategory(next);
    setVisibleCount(PAGE_SIZE);
  }

  return (
    <div className="flex flex-col gap-6">
      <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
        <StatCard
          icon="💚"
          label={t('cumulative.label')}
          value={statsLoading ? '—' : fmtEur(stats?.totalDonated ?? 0)}
          variant="teal"
        />
        <StatCard icon="🌍" label={t('cumulative.campaignCount')} value={impacts.length} variant="indigo" />
      </div>

      {impacts.length > 0 && (
        <div className="flex flex-wrap gap-2" role="group" aria-label={t('filter.label')}>
          <button
            type="button"
            className={`btn btn-sm ${category === null ? 'btn-primary' : 'btn-ghost'}`}
            aria-pressed={category === null}
            onClick={() => selectCategory(null)}
          >
            {t('filter.all')}
          </button>
          {categories.map((c) => (
            <button
              key={c}
              type="button"
              className={`btn btn-sm ${category === c ? 'btn-primary' : 'btn-ghost'}`}
              aria-pressed={category === c}
              onClick={() => selectCategory(c)}
            >
              {c}
            </button>
          ))}
        </div>
      )}

      {isLoading ? (
        <p className="text-sm text-text-2" aria-live="polite">
          {t('loading')}
        </p>
      ) : error ? (
        <EmptyStateCard icon="⚠️" title={t('error')} subtitle={t('errorSubtitle')} />
      ) : filtered.length === 0 ? (
        <EmptyStateCard icon={t('empty.icon')} title={t('empty.title')} subtitle={t('empty.subtitle')} />
      ) : (
        <>
          <ul className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-4">
            {visible.map((impact) => {
              const wording = impactWording(impact);
              return (
                <li key={impact.campaignId} className="card card-no-hover">
                  <div className="card-b flex flex-col gap-3">
                    <div className="flex items-center justify-between">
                      <span className="font-display font-bold text-sm">
                        {impact.campaignEmoji} {impact.campaignName}
                      </span>
                      {impact.category && <span className="badge badge-info">{impact.category}</span>}
                    </div>
                    <div className="text-xs text-text-2">{impact.associationName}</div>
                    {wording ? (
                      <p className="text-sm">
                        {t('wordingPrefix')} {withTerminalPunctuation(wording)} {t('wordingSuffix')}
                      </p>
                    ) : (
                      <p className="text-sm text-text-2">{t('notYetPublished')}</p>
                    )}
                    <div className="flex flex-wrap gap-2">
                      {impact.storySummary && (
                        <button
                          type="button"
                          className="btn btn-ghost btn-sm self-start"
                          onClick={() => setStoryTarget(impact)}
                        >
                          {t('story.cta')}
                        </button>
                      )}
                      <button
                        type="button"
                        className="btn btn-ghost btn-sm self-start"
                        onClick={() => setShareTarget(impact)}
                      >
                        {t('share.cta')}
                      </button>
                    </div>
                  </div>
                </li>
              );
            })}
          </ul>

          {visibleCount < filtered.length && (
            <button
              type="button"
              className="btn btn-secondary self-center"
              onClick={() => setVisibleCount((n) => n + PAGE_SIZE)}
            >
              {t('showMore')}
            </button>
          )}
        </>
      )}

      <ShareImpactModal impact={shareTarget} onClose={() => setShareTarget(null)} />
      <CampaignStoryModal campaignId={storyTarget?.campaignId ?? null} onClose={() => setStoryTarget(null)} />
    </div>
  );
}
