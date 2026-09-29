'use client';

import Link from 'next/link';
import { useTranslations, useLocale } from 'next-intl';
import { useAuthStore } from '@/stores/authStore';
import { StatCard, EmptyStateCard } from '@/components/dashboard';
import { DonorAssociationCard } from '@/components/donor/DonorAssociationCard';
import { DonationJourneyTimeline } from '@/components/donor/DonationJourneyTimeline';
import { useDonorStats } from '@/hooks/dashboard/useDonorStats';
import { useDonorDonations } from '@/hooks/dashboard/useDonorDonations';
import { useDonorAssociations } from '@/hooks/dashboard/useDonorAssociations';
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

  const name = user?.displayName?.trim() || user?.email || '';
  const recentDonations = donationsPage?.content ?? [];
  const recentAssociations = associations.slice(0, 3);

  return (
    <div>
      <div className="mb-8">
        <h1 className="font-display font-black text-2xl md:text-3xl">{t('title')}</h1>
        <p className="text-text-2 mt-1">{t('greeting', { name })}</p>
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
        <div className="card card-no-hover">
          <div className="card-header-bar flex items-center justify-between">
            <span className="font-display font-bold text-sm">{t('donor.sections.recentDonations')}</span>
            <Link href={`/${locale}${ROUTES.DONOR_DONATIONS}`} className="text-sm text-text-2">
              {t('donor.sections.viewAll')}
            </Link>
          </div>
          <div className="card-body">
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
          <div className="card-header-bar flex items-center justify-between">
            <span className="font-display font-bold text-sm">{t('donor.sections.donationJourney')}</span>
          </div>
          <div className="card-body">
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
          <div className="card-header-bar flex items-center justify-between">
            <span className="font-display font-bold text-sm">{t('donor.sections.myAssociations')}</span>
            <Link href={`/${locale}${ROUTES.DONOR_ASSOCIATIONS}`} className="text-sm text-text-2">
              {t('donor.sections.viewAll')}
            </Link>
          </div>
          <div className="card-body">
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
  );
}
