'use client';

import { useEffect } from 'react';
import { useRouter } from 'next/navigation';
import { useLocale, useTranslations } from 'next-intl';
import { useAssociationProfile } from '@/hooks/dashboard/useAssociationProfile';
import { useMoneriumStatus } from '@/hooks/monerium/useMoneriumStatus';
import { useAssociationDashboard } from '@/hooks/dashboard/useAssociationDashboard';
import { useAccStatusStore } from '@/stores/accStatusStore';
import { StatCard } from '@/components/dashboard/StatCard';
import { AccountCompletionCard } from '@/components/dashboard/AccountCompletionCard';
import { DonationsBarChart } from '@/components/dashboard/DonationsBarChart';
import { RecentActivityList } from '@/components/dashboard/RecentActivityList';
import { ROUTES } from '@/lib/routes';

function formatEUR(amount: number): string {
  return new Intl.NumberFormat('fr-FR', { style: 'currency', currency: 'EUR', maximumFractionDigits: 0 }).format(amount);
}

export default function AssociationDashboardPage() {
  const t = useTranslations('dashboard.association.home');
  const locale = useLocale();
  const router = useRouter();

  const { profile } = useAssociationProfile();
  const { connected } = useMoneriumStatus();
  const { stats, isLoading, error } = useAssociationDashboard();
  const setAccStatus = useAccStatusStore((s) => s.setAccStatus);

  const verified = profile?.verified ?? false;
  const bankConnected = connected === true;

  useEffect(() => {
    if (profile !== null && connected !== null) {
      const done = (verified ? 1 : 0) + (bankConnected ? 1 : 0);
      setAccStatus(done, 2, verified, bankConnected);
    }
  }, [profile, connected, verified, bankConnected, setAccStatus]);

  const greeting = profile ? t('greeting', { name: profile.name }) : t('greeting', { name: '…' });

  const EMPTY_STATS = {
    totalRaisedActive: 0,
    activeCampaignCount: 0,
    nextMilestone: null,
    avgProgress: 0,
    donations6Months: Array.from({ length: 6 }, (_, i) => {
      const d = new Date();
      d.setMonth(d.getMonth() - (5 - i));
      return { month: `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}`, amount: 0 };
    }),
    recentActivity: [],
  };

  const displayStats = stats ?? (isLoading ? EMPTY_STATS : EMPTY_STATS);

  return (
    <div className = "page">
      <AccountCompletionCard verified={verified} bankConnected={bankConnected} />

      <div className="page-head">
        <div>
          <h1>{greeting}</h1>
          <p>{t('subtitle')}</p>
        </div>
        <button
          className="btn btn-primary"
          onClick={() => router.push(`/${locale}${ROUTES.ASSOCIATION_CAMPAIGNS}`)}
        >
          {t('newCampaign')}
        </button>
      </div>

      {error && (
        <div className="alert alert-error" style={{ marginBottom: '24px' }}>
          {t('error')}
        </div>
      )}

      <div className="stats">
        <StatCard
          icon="💰"
          label={t('stats.totalRaised')}
          value={isLoading ? '—' : formatEUR(displayStats.totalRaisedActive)}
          variant="teal"
        />
        <StatCard
          icon="📢"
          label={<>{t('stats.activeCampaigns')} <span className="st-see">→ voir</span></>}
          value={isLoading ? '—' : displayStats.activeCampaignCount}
          variant="coral"
          onClick={() => router.push(`/${locale}${ROUTES.ASSOCIATION_CAMPAIGNS}`)}
        />
        <StatCard
          icon="🎯"
          label={t('stats.nextMilestone')}
          value={isLoading ? '—' : displayStats.nextMilestone ? formatEUR(displayStats.nextMilestone.remainingAmount) : '—'}
          subLabel={displayStats.nextMilestone?.label}
          variant="amber"
        />
        <StatCard
          icon="📊"
          label={t('stats.avgProgress')}
          value={isLoading ? '—' : `${Math.round(displayStats.avgProgress * 100)} %`}
          variant="indigo"
        />
      </div>

      <div className="g2">
        <DonationsBarChart data={displayStats.donations6Months} />
        <RecentActivityList items={displayStats.recentActivity} />
      </div>
    </div>
  );
}
