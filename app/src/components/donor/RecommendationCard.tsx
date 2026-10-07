'use client';

import { useTranslations } from 'next-intl';
import { apiUrl } from '@/lib/api';
import { DonationCta } from '@/components/donor/DonationCta';
import { useCauseLabel } from '@/lib/campaignCause';
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
  const causeLabel = useCauseLabel();
  const pct = recommendation.goal > 0 ? Math.round((recommendation.raised / recommendation.goal) * 100) : 0;

  return (
    <article className="project-card">
      <div className="project-card-img">
        {recommendation.coverImage ? (
          // eslint-disable-next-line @next/next/no-img-element -- served by the API, not by Next
          <img src={apiUrl(recommendation.coverImage)} alt="" className="w-full h-full object-cover" />
        ) : (
          <span aria-hidden="true">{recommendation.campaignEmoji}</span>
        )}
        {recommendation.category && <span className="project-card-badge">{causeLabel(recommendation.category)}</span>}
      </div>

      <div className="project-card-body">
        <div className="project-card-asso">{recommendation.associationName}</div>
        <h3 className="project-card-title">{recommendation.campaignName}</h3>

        {recommendation.matchedCategory && (
          <p className="text-xs text-text-2 mb-2">{t('reason', { category: causeLabel(recommendation.matchedCategory) })}</p>
        )}

        <div
          className="project-card-bar"
          role="progressbar"
          aria-valuenow={pct}
          aria-valuemin={0}
          aria-valuemax={100}
        >
          <div className="project-card-fill" style={{ width: `${pct}%` }} />
        </div>
        <div className="project-card-stats">
          <span>
            <strong>{fmtEur(recommendation.raised)}</strong> / {fmtEur(recommendation.goal)}
          </span>
          <span>{pct}%</span>
        </div>

        <DonationCta
          associationName={recommendation.associationName}
          donationUrl={recommendation.donationUrl}
          className="btn btn-sm btn-primary self-start mt-3"
        />
      </div>
    </article>
  );
}
