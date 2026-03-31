'use client';

import { useTranslations } from 'next-intl';
import { useAuthStore } from '@/stores/authStore';
import { Topbar, StatCard, EmptyStateCard } from '@/components/dashboard';

export default function DonorDashboardPage() {
  const t = useTranslations('dashboard');
  const user = useAuthStore((s) => s.user);

  const name = user?.displayName?.trim() || user?.email || '';
  const subtitle = t('greeting', { name });

  return (
    <div>
      <Topbar title={t('title')} subtitle={subtitle} />

      <div className="grid grid-cols-4 gap-[13px] mb-[26px]">
        <StatCard
          icon="💰"
          label={t('donor.stats.totalDonated')}
          value="0 €"
        />
        <StatCard
          icon="🎁"
          label={t('donor.stats.donationsCount')}
          value={0}
        />
        <StatCard
          icon="📡"
          label={t('donor.stats.campaignsFollowed')}
          value={0}
        />
        <StatCard
          icon="⭐"
          label={t('donor.stats.impactScore')}
          value="—"
        />
      </div>

      <EmptyStateCard
        icon={t('donor.empty.icon')}
        title={t('donor.empty.title')}
        subtitle={t('donor.empty.subtitle')}
        actionLabel={t('donor.empty.cta')}
        actionHref="/dashboard/campaigns"
      />
    </div>
  );
}
