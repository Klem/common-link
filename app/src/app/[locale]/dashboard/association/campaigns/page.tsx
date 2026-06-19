'use client';

import { useState } from 'react';
import { useRouter } from 'next/navigation';
import { useLocale, useTranslations } from 'next-intl';
import { Topbar } from '@/components/dashboard';
import { CampaignCard } from '@/components/campaign';
import { useCampaigns } from '@/hooks/campaign/useCampaigns';
import { createCampaign } from '@/lib/api/campaign';
import { ROUTES } from '@/lib/routes';

type Filter = '' | 'LIVE' | 'ENDED' | 'DRAFT';

export default function CampaignsPage() {
  const router = useRouter();
  const locale = useLocale();
  const t = useTranslations('dashboard');
  const { campaigns, isLoading, removeCampaign } = useCampaigns();
  const [activeFilter, setActiveFilter] = useState<Filter>('');

  const filtered = activeFilter
    ? campaigns.filter((c) => c.status === activeFilter)
    : campaigns;

  const handleCreate = async () => {
    const newCampaign = await createCampaign({ name: t('campaigns.defaultName') });
    router.push(`/${locale}${ROUTES.ASSOCIATION_CAMPAIGNS}/${newCampaign.id}`);
  };

  const filters: { key: Filter; label: string }[] = [
    { key: '', label: t('campaigns.filterAll') },
    { key: 'LIVE', label: t('campaigns.filterLive') },
    { key: 'ENDED', label: t('campaigns.filterEnded') },
    { key: 'DRAFT', label: t('campaigns.filterDraft') },
  ];

  return (
    <div className="page">
      <Topbar parent="Dashboard" title={t('campaigns.pageTitle')} />

      <div className="page-head">
        <div>
          <h1>{t('campaigns.pageTitle')}</h1>
          <p>{t('campaigns.pageSubtitle')}</p>
        </div>
        <button className="btn btn-primary" onClick={handleCreate}>
          + {t('campaigns.create')}
        </button>
      </div>

      <div className="camp-filter-bar">
        {filters.map(({ key, label }) => (
          <button
            key={key}
            className={`btn btn-sm ${activeFilter === key ? 'btn-primary' : 'btn-secondary'}`}
            onClick={() => setActiveFilter(key)}
          >
            {label}
          </button>
        ))}
      </div>

      {isLoading && (
        <p style={{ color: 'var(--slate-lavender)', fontSize: '13px', textAlign: 'center', padding: '40px 0' }}>
          {t('campaigns.loading')}
        </p>
      )}

      <div className="camp-grid">
        {!isLoading && filtered.length === 0 && (
          <p style={{ gridColumn: '1 / -1', textAlign: 'center', color: 'var(--slate-lavender)', fontSize: '14px', padding: '48px 0' }}>
            {campaigns.length === 0 ? t('campaigns.empty.title') : t('campaigns.filterAll')}
          </p>
        )}
        {!isLoading && filtered.map((campaign) => (
          <CampaignCard
            key={campaign.id}
            campaign={campaign}
            onDelete={removeCampaign}
          />
        ))}
      </div>
    </div>
  );
}
