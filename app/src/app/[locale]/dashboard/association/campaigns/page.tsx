'use client';

import { useRouter } from 'next/navigation';
import { useLocale, useTranslations } from 'next-intl';
import { Topbar, StatCard } from '@/components/dashboard';
import { CampaignCard } from '@/components/campaign';
import { useCampaigns } from '@/hooks/campaign/useCampaigns';
import { createCampaign } from '@/lib/api/campaign';
import { ROUTES } from '@/lib/routes';
import { CampaignStatus } from '@/types/campaign';

/** Formats a number as a EUR currency string using French locale. */
function formatEur(amount: number): string {
  return new Intl.NumberFormat('fr-FR', { style: 'currency', currency: 'EUR' }).format(amount);
}

/**
 * Association campaign list page.
 *
 * Displays stats, a grid of campaign cards, and an empty state when no campaigns exist.
 * Creating a campaign calls the API immediately and redirects to the campaign editor.
 */
export default function CampaignsPage() {
  const router = useRouter();
  const locale = useLocale();
  const t = useTranslations('dashboard');
  const { campaigns, isLoading, removeCampaign } = useCampaigns();

  const liveCampaigns = campaigns.filter((c) => c.status === CampaignStatus.LIVE).length;
  const totalRaised = campaigns.reduce((sum, c) => sum + c.raised, 0);
  const totalGoal = campaigns.reduce((sum, c) => sum + c.goal, 0);

  const handleCreate = async () => {
    const newCampaign = await createCampaign({ name: t('campaigns.defaultName') });
    router.push(`/${locale}${ROUTES.ASSOCIATION_CAMPAIGNS}/${newCampaign.id}`);
  };

  return (
    <div>
      <Topbar
        title={t('campaigns.pageTitle')}
        subtitle={t('campaigns.pageSubtitle')}
      />

      {/* Stats row */}
      <div className="grid grid-cols-4 gap-[13px] mb-[26px]">
        <StatCard icon="📣" label={t('campaigns.stats.total')} value={campaigns.length} />
        <StatCard icon="🔴" label={t('campaigns.stats.live')} value={liveCampaigns} />
        <StatCard icon="💶" label={t('campaigns.stats.raised')} value={formatEur(totalRaised)} />
        <StatCard icon="🎯" label={t('campaigns.stats.goal')} value={formatEur(totalGoal)} />
      </div>

      {/* Header row: title + create button */}
      <div className="flex items-center justify-between mb-[16px]">
        <p className="font-display font-bold text-[15px] text-text">
          {t('campaigns.pageTitle')}
        </p>
        <button
          onClick={handleCreate}
          className="bg-green text-black font-display font-bold text-[13px] rounded-xl px-5 py-3 hover:opacity-90 transition-opacity"
        >
          {t('campaigns.create')}
        </button>
      </div>

      {/* Loading */}
      {isLoading && (
        <p className="text-[13px] text-text-2 text-center py-[40px]">{t('campaigns.loading')}</p>
      )}

      {/* Empty state */}
      {!isLoading && campaigns.length === 0 && (
        <div className="bg-bg-2 border border-border rounded-[14px] p-[40px] flex flex-col items-center text-center">
          <p className="text-[48px] mb-4 leading-none">{t('campaigns.empty.icon')}</p>
          <h2 className="font-display font-bold text-[18px] text-text mb-[8px]">
            {t('campaigns.empty.title')}
          </h2>
          <p className="text-[13px] text-text-2 max-w-[420px]">{t('campaigns.empty.subtitle')}</p>
          <button
            onClick={handleCreate}
            className="mt-[24px] bg-green text-black font-display font-bold text-[13px] px-[20px] py-[10px] rounded-md hover:opacity-90 transition-opacity"
          >
            {t('campaigns.empty.cta')}
          </button>
        </div>
      )}

      {/* Campaign grid */}
      {!isLoading && campaigns.length > 0 && (
        <div className="grid grid-cols-2 gap-[14px]">
          {campaigns.map((campaign) => (
            <CampaignCard
              key={campaign.id}
              campaign={campaign}
              onDelete={removeCampaign}
            />
          ))}
        </div>
      )}
    </div>
  );
}
