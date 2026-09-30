'use client';

import Link from 'next/link';
import { useTranslations, useLocale } from 'next-intl';
import { useAuthStore } from '@/stores/authStore';
import { StatCard, EmptyStateCard, Topbar } from '@/components/dashboard';
import { DonorAssociationCard } from '@/components/donor/DonorAssociationCard';
import { DonationJourneyTimeline } from '@/components/donor/DonationJourneyTimeline';
import { RecentActivityBlock } from '@/components/donor/RecentActivityBlock';
import { RecommendationCard } from '@/components/donor/RecommendationCard';
import { DonationCta } from '@/components/donor/DonationCta';
import { useDonorStats } from '@/hooks/dashboard/useDonorStats';
import { useDonorDonations } from '@/hooks/dashboard/useDonorDonations';
import { useDonorAssociations } from '@/hooks/dashboard/useDonorAssociations';
import { useDonorRecommendations } from '@/hooks/dashboard/useDonorRecommendations';
import { ROUTES } from '@/lib/routes';

function fmtEur(amount: number): string {
  return new Intl.NumberFormat('fr-FR', { style: 'currency', currency: 'EUR' }).format(amount);
}

export default function DonorDashboardPage() {
  const t = useTranslations('dashboard');
  const locale = useLocale();
  const user = useAuthStore((s) => s.user);
  const { stats, isLoading: statsLoading } = useDonorStats();
  const { donationsPage, isLoading: donationsLoading } = useDonorDonations(3);
  const { associations, isLoading: associationsLoading } = useDonorAssociations();
  const { recommendations, isLoading: recommendationsLoading } = useDonorRecommendations();

  const name = user?.displayName?.trim() || user?.email || '';
  const recentDonations = donationsPage?.content ?? [];
  const recentAssociations = associations.slice(0, 3);
  const topRecommendation = recommendations[0] ?? null;

  // Global "Faire un don" CTA: first eligible supported association, falling back to the top
  // recommendation. Never a link that would 404/409 — see DonationCta.
  const eligibleAssociation = associations.find((a) => a.donationUrl !== null);
  const globalDonationTarget = eligibleAssociation
    ? { name: eligibleAssociation.name, url: eligibleAssociation.donationUrl }
    : topRecommendation
      ? { name: topRecommendation.associationName, url: topRecommendation.donationUrl }
      : null;

  return (
    <div>
      <Topbar title={t('title')} />

      <div className="page">
        <div className="page-head">
          <div>
            <h1>{t('title')}</h1>
            <p>{t('greeting', { name })}</p>
          </div>
          {globalDonationTarget && (
            <DonationCta
              associationName={globalDonationTarget.name}
              donationUrl={globalDonationTarget.url}
              className="btn btn-primary"
            />
          )}
        </div>

        <div className="grid grid-cols-1 sm:grid-cols-2 xl:grid-cols-4 gap-4 mb-6">
        <StatCard
          icon="💰"
          label={t('donor.stats.totalDonated')}
          value={statsLoading ? '—' : fmtEur(stats?.totalDonated ?? 0)}
          variant="teal"
        />
        <StatCard
          icon="🎁"
          label={t('donor.stats.donationCount')}
          value={statsLoading ? '—' : (stats?.donationCount ?? 0)}
          variant="coral"
        />
        <StatCard
          icon="🏢"
          label={t('donor.stats.associationCount')}
          value={statsLoading ? '—' : (stats?.associationCount ?? 0)}
          variant="indigo"
        />
        <StatCard
          icon="📋"
          label={t('donor.stats.estimatedTaxReduction')}
          value={statsLoading ? '—' : fmtEur(stats?.estimatedTaxReduction ?? 0)}
          subLabel={t('donor.stats.estimatedTaxReductionHint')}
          variant="amber"
        />
      </div>

      <div className="flex flex-col gap-6">
        <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
          <div className="card card-no-hover">
            <div className="card-h flex items-center justify-between">
              <span className="font-display font-bold text-sm">
                {t('donor.engagement.feed.sectionTitle')}
              </span>
            </div>
            <div className="card-b">
              <RecentActivityBlock />
            </div>
          </div>

          <div className="card card-no-hover">
            <div className="card-h flex items-center justify-between">
              <span className="font-display font-bold text-sm">{t('donor.recommendations.sectionTitle')}</span>
              <Link href={`/${locale}${ROUTES.DONOR_RECOMMENDATIONS}`} className="text-sm text-text-2">
                {t('donor.sections.viewAll')}
              </Link>
            </div>
            <div className="card-b">
              {recommendationsLoading ? (
                <p className="text-sm text-text-2" aria-live="polite">
                  {t('donor.recommendations.loading')}
                </p>
              ) : topRecommendation ? (
                <RecommendationCard recommendation={topRecommendation} />
              ) : (
                <p className="text-sm text-text-2">{t('donor.recommendations.empty.subtitle')}</p>
              )}
            </div>
          </div>
        </div>

        <div className="card card-no-hover">
          <div className="card-h flex items-center justify-between">
            <span className="font-display font-bold text-sm">{t('donor.sections.recentDonations')}</span>
            <Link href={`/${locale}${ROUTES.DONOR_DONATIONS}`} className="text-sm text-text-2">
              {t('donor.sections.viewAll')}
            </Link>
          </div>
          <div className="card-b">
            {donationsLoading ? (
              <p className="text-sm text-text-2" aria-live="polite">
                {t('donor.donations.loading')}
              </p>
            ) : recentDonations.length === 0 ? (
              <EmptyStateCard
                icon={t('donor.empty.icon')}
                title={t('donor.empty.title')}
                subtitle={t('donor.empty.subtitle')}
                actionLabel={t('donor.empty.cta')}
                actionHref="/dashboard/campaigns"
              />
            ) : (
              <ul className="flex flex-col gap-3">
                {recentDonations.map((donation) => (
                  <li key={donation.id} className="flex items-center justify-between text-sm">
                    <span>
                      <Link href={`/${locale}${ROUTES.DONOR_CAMPAIGN_REPORT(donation.campaignId)}`}>
                        {donation.campaignEmoji} {donation.campaignName}
                      </Link>{' '}
                      — {donation.associationName}
                    </span>
                    <span className="font-display font-bold">{fmtEur(donation.amount)}</span>
                  </li>
                ))}
              </ul>
            )}
          </div>
        </div>

        <div className="card card-no-hover">
          <div className="card-h flex items-center justify-between">
            <span className="font-display font-bold text-sm">{t('donor.sections.donationJourney')}</span>
          </div>
          <div className="card-b">
            {donationsLoading ? (
              <p className="text-sm text-text-2" aria-live="polite">
                {t('donor.donations.loading')}
              </p>
            ) : (
              <DonationJourneyTimeline initialDonationId={recentDonations[0]?.id ?? null} />
            )}
          </div>
        </div>

        <div className="card card-no-hover">
          <div className="card-h flex items-center justify-between">
            <span className="font-display font-bold text-sm">{t('donor.sections.myAssociations')}</span>
            <Link href={`/${locale}${ROUTES.DONOR_ASSOCIATIONS}`} className="text-sm text-text-2">
              {t('donor.sections.viewAll')}
            </Link>
          </div>
          <div className="card-b">
            {associationsLoading ? (
              <p className="text-sm text-text-2" aria-live="polite">
                {t('donor.associations.loading')}
              </p>
            ) : recentAssociations.length === 0 ? (
              <EmptyStateCard
                icon={t('donor.empty.icon')}
                title={t('donor.empty.title')}
                subtitle={t('donor.empty.subtitle')}
              />
            ) : (
              <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-4">
                {recentAssociations.map((association) => (
                  <DonorAssociationCard key={association.associationId} association={association} />
                ))}
              </div>
            )}
          </div>
        </div>
      </div>
      </div>
    </div>
  );
}
