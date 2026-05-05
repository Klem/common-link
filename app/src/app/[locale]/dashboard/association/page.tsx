'use client';

import Link from 'next/link';
import { useTranslations } from 'next-intl';
import { useAuthStore } from '@/stores/authStore';
import { StatCard, EmptyStateCard } from '@/components/dashboard';
import { ROUTES } from '@/lib/routes';

export default function AssociationDashboardPage() {
  const t = useTranslations('dashboard');
  const user = useAuthStore((s) => s.user);

  const subtitle = user?.displayName?.trim() || user?.email || '';

  return (
    <div>
      {/* Page header */}
      <div className="flex flex-col md:flex-row md:items-center md:justify-between gap-4 mb-8">
        <div>
          <h1 className="font-display font-black text-2xl md:text-3xl">{t('title')}</h1>
          {subtitle && <p className="text-text-2 mt-1">{subtitle}</p>}
        </div>
        <Link href={ROUTES.ASSOCIATION_CAMPAIGNS} className="btn btn-primary btn-md self-start md:self-auto">
          + {t('association.newCampaign')}
        </Link>
      </div>

      {/* KPI grid */}
      <div className="grid grid-cols-1 sm:grid-cols-2 xl:grid-cols-4 gap-4 mb-6">
        <StatCard icon="💶" label={t('association.stats.fundsRaised')} value="0 €" />
        <StatCard icon="👥" label={t('association.stats.uniqueDonors')} value={0} />
        <StatCard icon="📣" label={t('association.stats.activeCampaigns')} value={0} />
        <StatCard icon="📊" label={t('association.stats.conversionRate')} value="—" />
      </div>

      {/* Active campaigns */}
      <div className="card card-no-hover mb-6">
        <div className="card-header-bar">
          <span className="font-display font-bold text-sm">{t('association.sections.activeCampaigns')}</span>
          <Link href={ROUTES.ASSOCIATION_CAMPAIGNS} className="btn btn-ghost btn-xs">
            + {t('association.newCampaign')}
          </Link>
        </div>
        <div className="card-body">
          <EmptyStateCard
            icon={t('association.empty.icon')}
            title={t('association.empty.title')}
            subtitle={t('association.empty.subtitle')}
            actionLabel={t('association.empty.cta')}
            actionHref={ROUTES.ASSOCIATION_CAMPAIGNS}
          />
        </div>
      </div>

      {/* Recent activity */}
      <div className="card card-no-hover">
        <div className="card-header-bar">
          <span className="font-display font-bold text-sm">{t('association.sections.recentActivity')}</span>
        </div>
        <div className="card-body">
          <p className="text-sm text-text-2">{t('association.recentActivity.empty')}</p>
        </div>
      </div>
    </div>
  );
}
