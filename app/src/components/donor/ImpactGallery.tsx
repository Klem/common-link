'use client';

import { useMemo, useState } from 'react';
import { useTranslations } from 'next-intl';
import { EmptyStateCard } from '@/components/dashboard';
import { CampaignStoryModal } from '@/components/donor/CampaignStoryModal';
import { DonationCta } from '@/components/donor/DonationCta';
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
    () =>
      Array.from(new Set(impacts.map((i) => i.category).filter((c): c is string => c !== null))).sort((a, b) =>
        a.localeCompare(b, 'fr', { sensitivity: 'base' }),
      ),
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
      <section className="narrative-card" aria-labelledby="impact-narrative-h2">
        <div className="narrative-top">
          <h2 id="impact-narrative-h2">{t('cumulative.title')}</h2>
          <span className="tag">{t('cumulative.since')}</span>
        </div>
        <div className="narrative-grid">
          <div className="narrative-item">
            <div className="narrative-val">{statsLoading ? '—' : fmtEur(stats?.totalDonated ?? 0)}</div>
            <div className="narrative-label">{t('cumulative.label')}</div>
          </div>
          <div className="narrative-item">
            <div className="narrative-val">{impacts.length}</div>
            <div className="narrative-label">{t('cumulative.campaignCount')}</div>
          </div>
          <div className="narrative-item">
            <div className="narrative-val">{statsLoading ? '—' : (stats?.associationCount ?? 0)}</div>
            <div className="narrative-label">{t('cumulative.associationCount')}</div>
          </div>
        </div>
        <div className="narrative-bottom">
          <span className="freshness-tag freshness-tag-light">{t('cumulative.tagline')}</span>
        </div>
      </section>

      <div className="imp-toolbar">
        <h3>
          {t('gallery.title')}
          <span className="imp-why" tabIndex={0} role="button" aria-label={t('why.ariaLabel')}>
            ?<span className="imp-why-tip">{t('why.tooltip')}</span>
          </span>
        </h3>
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
      </div>
      <p className="imp-count">{t('gallery.count', { count: filtered.length })}</p>

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
          <ul className="imp-gal">
            {visible.map((impact) => {
              const wording = impactWording(impact);
              return (
                <li key={impact.campaignId} className="imp-tile">
                  {impact.category && (
                    <div className="cause">
                      <span aria-hidden="true">🏷️</span> {impact.category}
                    </div>
                  )}
                  {wording ? (
                    <p className="v text">
                      {t('wordingPrefix')} {withTerminalPunctuation(wording)} {t('wordingSuffix')}
                    </p>
                  ) : (
                    <p className="text-sm text-text-2">{t('notYetPublished')}</p>
                  )}
                  <p className="p" title={`${impact.campaignEmoji} ${impact.campaignName}`}>
                    {impact.campaignEmoji} {impact.campaignName}
                  </p>
                  <p className="p" title={impact.associationName}>
                    {impact.associationName}
                  </p>
                  <div className="imp-tile-actions">
                    <div className="flex items-center gap-2">
                      {impact.storySummary && (
                        <button
                          type="button"
                          className="btn-icon"
                          onClick={() => setStoryTarget(impact)}
                          aria-label={t('story.cta')}
                          title={t('story.cta')}
                        >
                          <span aria-hidden="true">📖</span>
                        </button>
                      )}
                      <button
                        type="button"
                        className="btn-icon"
                        onClick={() => setShareTarget(impact)}
                        aria-label={t('share.cta')}
                        title={t('share.cta')}
                      >
                        <span aria-hidden="true">📤</span>
                      </button>
                    </div>
                    <DonationCta
                      associationName={impact.associationName}
                      donationUrl={impact.donationUrl}
                      className="btn btn-primary btn-sm max-w-full truncate"
                    />
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
