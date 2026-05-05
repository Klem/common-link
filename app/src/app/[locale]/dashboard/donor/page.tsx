'use client';

import { useTranslations } from 'next-intl';
import { useAuthStore } from '@/stores/authStore';
import { StatCard, EmptyStateCard } from '@/components/dashboard';

export default function DonorDashboardPage() {
  const t = useTranslations('dashboard');
  const user = useAuthStore((s) => s.user);

  const name = user?.displayName?.trim() || user?.email || '';

  return (
    <div>
      <div className="mb-8">
        <h1 className="font-display font-black text-2xl md:text-3xl">{t('title')}</h1>
        <p className="text-text-2 mt-1">{t('greeting', { name })}</p>
      </div>

      <div className="grid grid-cols-1 sm:grid-cols-2 xl:grid-cols-4 gap-4 mb-6">
        <StatCard icon="💰" label={t('donor.stats.totalDonated')} value="0 €" variant="teal" />
        <StatCard icon="🎁" label={t('donor.stats.donationsCount')} value={0} variant="coral" />
        <StatCard icon="📡" label={t('donor.stats.campaignsFollowed')} value={0} variant="indigo" />
        <StatCard icon="⭐" label={t('donor.stats.impactScore')} value="—" variant="amber" />
      </div>

      <div className="flex flex-col gap-6">
        <div className="card card-no-hover">
          <div className="card-header-bar flex items-center justify-between">
            <span className="font-display font-bold text-sm">{t('donor.sections.recentDonations')}</span>
            <span className="text-sm text-text-2">{t('donor.sections.viewAll')}</span>
          </div>
          <div className="card-body">
            <EmptyStateCard
              icon={t('donor.empty.icon')}
              title={t('donor.empty.title')}
              subtitle={t('donor.empty.subtitle')}
              actionLabel={t('donor.empty.cta')}
              actionHref="/dashboard/campaigns"
            />
          </div>
        </div>

        <div className="card card-no-hover">
          <div className="card-header-bar flex items-center justify-between">
            <span className="font-display font-bold text-sm">{t('donor.sections.nftBadges')}</span>
          </div>
          <div className="card-body">
            <EmptyStateCard
              icon={t('donor.empty.icon')}
              title={t('donor.empty.title')}
              subtitle={t('donor.empty.subtitle')}
            />
          </div>
        </div>

        <div className="card card-no-hover">
          <div className="card-header-bar flex items-center justify-between">
            <span className="font-display font-bold text-sm">{t('donor.sections.followedCampaigns')}</span>
            <span className="text-sm text-text-2">{t('donor.sections.viewAll')}</span>
          </div>
          <div className="card-body">
            <EmptyStateCard
              icon={t('donor.empty.icon')}
              title={t('donor.empty.title')}
              subtitle={t('donor.empty.subtitle')}
            />
          </div>
        </div>
      </div>
    </div>
  );
}
