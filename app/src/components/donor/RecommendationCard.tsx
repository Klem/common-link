'use client';

import { useTranslations } from 'next-intl';
import { apiUrl } from '@/lib/api';
import { DonationCta } from '@/components/donor/DonationCta';
import type { DonorRecommendationDto } from '@/types/donor';

interface Props {
  recommendation: DonorRecommendationDto;
}

function fmtEur(amount: number): string {
  return new Intl.NumberFormat('fr-FR', { style: 'currency', currency: 'EUR' }).format(amount);
}

/** One recommended-project card (D8, option A) — used by the "Pour vous" block and the dedicated page. */
export function RecommendationCard({ recommendation }: Props) {
  const t = useTranslations('dashboard.donor.recommendations');
  const pct = recommendation.goal > 0 ? Math.round((recommendation.raised / recommendation.goal) * 100) : 0;

  return (
    <div className="card card-no-hover">
      <div className="card-b flex flex-col gap-3">
        <div className="flex items-center gap-3">
          {recommendation.coverImage ? (
            // eslint-disable-next-line @next/next/no-img-element -- served by the API, not by Next
            <img src={apiUrl(recommendation.coverImage)} alt="" className="avatar avatar-md object-cover" />
          ) : (
            <span className="avatar avatar-md" aria-hidden="true">
              {recommendation.campaignEmoji}
            </span>
          )}
          <div>
            <p className="font-display font-bold text-sm">{recommendation.campaignName}</p>
            <p className="text-xs text-text-2">{recommendation.associationName}</p>
          </div>
        </div>

        {recommendation.category && <span className="badge badge-info self-start">{recommendation.category}</span>}

        {recommendation.matchedCategory && (
          <p className="text-xs text-text-2">{t('reason', { category: recommendation.matchedCategory })}</p>
        )}

        <p className="text-sm">
          {fmtEur(recommendation.raised)} / {fmtEur(recommendation.goal)} ({pct}%)
        </p>

        <DonationCta
          associationName={recommendation.associationName}
          donationUrl={recommendation.donationUrl}
          className="btn btn-primary btn-sm self-start"
        />
      </div>
    </div>
  );
}
