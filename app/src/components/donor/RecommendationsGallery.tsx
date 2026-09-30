'use client';

import { useMemo, useState } from 'react';
import { useTranslations } from 'next-intl';
import { EmptyStateCard } from '@/components/dashboard';
import { RecommendationCard } from '@/components/donor/RecommendationCard';
import { useDonorRecommendations } from '@/hooks/dashboard/useDonorRecommendations';

/** "Projets recommandés" page content (D8, option A): simple category filter over the list. */
export function RecommendationsGallery() {
  const t = useTranslations('dashboard.donor.recommendations');
  const { recommendations, isLoading, error } = useDonorRecommendations();
  const [category, setCategory] = useState<string | null>(null);

  const categories = useMemo(
    () =>
      Array.from(new Set(recommendations.map((r) => r.category).filter((c): c is string => c !== null))).sort(),
    [recommendations],
  );
  const filtered = useMemo(
    () => (category === null ? recommendations : recommendations.filter((r) => r.category === category)),
    [recommendations, category],
  );

  if (isLoading) {
    return (
      <p className="text-sm text-text-2" aria-live="polite">
        {t('loading')}
      </p>
    );
  }

  if (error) {
    return <EmptyStateCard icon="⚠️" title={t('error')} subtitle={t('errorSubtitle')} />;
  }

  if (recommendations.length === 0) {
    return <EmptyStateCard icon={t('empty.icon')} title={t('empty.title')} subtitle={t('empty.subtitle')} />;
  }

  return (
    <div className="flex flex-col gap-6">
      {categories.length > 0 && (
        <div className="flex flex-wrap gap-2" role="group" aria-label={t('filter.label')}>
          <button
            type="button"
            className={`btn btn-sm ${category === null ? 'btn-primary' : 'btn-ghost'}`}
            aria-pressed={category === null}
            onClick={() => setCategory(null)}
          >
            {t('filter.all')}
          </button>
          {categories.map((c) => (
            <button
              key={c}
              type="button"
              className={`btn btn-sm ${category === c ? 'btn-primary' : 'btn-ghost'}`}
              aria-pressed={category === c}
              onClick={() => setCategory(c)}
            >
              {c}
            </button>
          ))}
        </div>
      )}

      <ul className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-4">
        {filtered.map((recommendation) => (
          <li key={recommendation.campaignId}>
            <RecommendationCard recommendation={recommendation} />
          </li>
        ))}
      </ul>
    </div>
  );
}
