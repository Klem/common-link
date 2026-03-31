'use client';

import { useTranslations } from 'next-intl';
import { useAuthStore } from '@/stores/authStore';
import { Topbar, StatCard, EmptyStateCard } from '@/components/dashboard';

const CHECKLIST_ITEMS = [
  { key: 'name', done: true },
  { key: 'siren', done: true },
  { key: 'description', done: false },
  { key: 'contact', done: false },
] as const;

export default function AssociationDashboardPage() {
  const t = useTranslations('dashboard');
  const user = useAuthStore((s) => s.user);

  const subtitle = user?.displayName?.trim() || user?.email || '';

  return (
    <div>
      <Topbar title={t('title')} subtitle={subtitle} />

      <div className="grid grid-cols-4 gap-[13px] mb-[26px]">
        <StatCard
          icon="💶"
          label={t('association.stats.fundsRaised')}
          value="0 €"
        />
        <StatCard
          icon="👥"
          label={t('association.stats.uniqueDonors')}
          value={0}
        />
        <StatCard
          icon="📣"
          label={t('association.stats.activeCampaigns')}
          value={0}
        />
        <StatCard
          icon="📊"
          label={t('association.stats.conversionRate')}
          value="—"
        />
      </div>

      <div className="grid grid-cols-[1fr_320px] gap-[13px]">
        <EmptyStateCard
          icon={t('association.empty.icon')}
          title={t('association.empty.title')}
          subtitle={t('association.empty.subtitle')}
          actionLabel={t('association.empty.cta')}
          actionHref="/dashboard/campaigns/new"
        />

        <div className="bg-bg-2 border border-border rounded-[14px] p-[24px]">
          <p className="font-display font-bold text-[15px] text-text mb-[4px]">
            {t('association.profileCompletion.title')}
          </p>
          <p className="text-[12px] text-text-2 mb-[20px]">
            {t('association.profileCompletion.subtitle')}
          </p>
          <ul className="flex flex-col gap-[12px]">
            {CHECKLIST_ITEMS.map((item) => (
              <li key={item.key} className="flex items-center gap-[10px] text-[13px]">
                <span className="text-[16px] leading-none flex-shrink-0">
                  {item.done ? '✅' : '⬜'}
                </span>
                <span className={item.done ? 'text-text' : 'text-text-2'}>
                  {t(`association.profileCompletion.fields.${item.key}`)}
                </span>
              </li>
            ))}
          </ul>
        </div>
      </div>
    </div>
  );
}
