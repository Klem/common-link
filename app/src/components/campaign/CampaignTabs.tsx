'use client';

import { useTranslations } from 'next-intl';

interface Tab {
  id: string;
  icon: string;
  labelKey: string;
  count?: number;
}

interface CampaignTabsProps {
  activeTab: string;
  onTabChange: (tab: string) => void;
  milestoneCount?: number;
  paymentCount?: number;
  donorCount?: number;
}

export function CampaignTabs({ activeTab, onTabChange, milestoneCount, paymentCount, donorCount }: CampaignTabsProps) {
  const t = useTranslations('dashboard.campaigns');

  const tabs: Tab[] = [
    { id: 'info',       icon: '⚙️', labelKey: 'editor.tabs.info' },
    { id: 'budget',     icon: '💶', labelKey: 'editor.tabs.budget' },
    { id: 'milestones', icon: '🎯', labelKey: 'editor.tabs.milestones', count: milestoneCount },
    { id: 'payments',   icon: '💸', labelKey: 'editor.tabs.payments',   count: paymentCount },
    { id: 'donors',     icon: '👥', labelKey: 'editor.tabs.donors',     count: donorCount },
    { id: 'reporting',  icon: '📊', labelKey: 'editor.tabs.reporting' },
  ];

  return (
    <div className="camp-tabs">
      {tabs.map((tab) => (
        <button
          key={tab.id}
          type="button"
          className={`camp-tab${activeTab === tab.id ? ' active' : ''}`}
          onClick={() => onTabChange(tab.id)}
        >
          {tab.icon} {t(tab.labelKey)}
          {tab.count !== undefined && (
            <span className="count">{tab.count}</span>
          )}
        </button>
      ))}
    </div>
  );
}
