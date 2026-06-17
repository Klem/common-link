'use client';

import { useTranslations } from 'next-intl';

interface Tab {
  id: string;
  icon: string;
  labelKey: string;
  count?: number;
}

interface CampaignTabsProps {
  /** Currently active tab id. */
  activeTab: string;
  /** Called when the user clicks a tab. */
  onTabChange: (tab: string) => void;
  /** Number of milestones to display on the milestones tab badge. */
  milestoneCount?: number;
  /** Total number of payouts to display on the payments tab badge. */
  paymentCount?: number;
  /** Total number of donors to display on the donors tab badge. */
  donorCount?: number;
}

/**
 * Tab bar for the campaign editor.
 *
 * Renders tabs for Infos, Budget prév., Milestones, Paiements, and Donateurs.
 * Active tab is highlighted with a background and shadow.
 */
export function CampaignTabs({ activeTab, onTabChange, milestoneCount, paymentCount, donorCount }: CampaignTabsProps) {
  const t = useTranslations('dashboard.campaigns');

  const tabs: Tab[] = [
    { id: 'info', icon: '📋', labelKey: 'editor.tabs.info' },
    { id: 'budget', icon: '💰', labelKey: 'editor.tabs.budget' },
    { id: 'milestones', icon: '🏆', labelKey: 'editor.tabs.milestones', count: milestoneCount },
    { id: 'payments', icon: '📤', labelKey: 'editor.tabs.payments', count: paymentCount },
    { id: 'donors', icon: '👥', labelKey: 'editor.tabs.donors', count: donorCount },
  ];

  return (
    <div className="flex gap-[4px] rounded-[14px] border border-[var(--color-border)] bg-[var(--color-bg-2)] p-[4px] mb-[24px] overflow-x-auto">
      {tabs.map((tab) => (
        <button
          key={tab.id}
          type="button"
          onClick={() => onTabChange(tab.id)}
          className={`tab-button flex items-center gap-[6px] whitespace-nowrap${activeTab === tab.id ? ' active' : ''}`}
        >
          <span>{tab.icon}</span>
          <span>{t(tab.labelKey)}</span>
          {tab.count !== undefined && (
            <span className="text-[11px] font-bold px-[6px] py-[1px] rounded-full bg-[var(--color-bg)] text-[var(--color-text-2)]">
              {tab.count}
            </span>
          )}
        </button>
      ))}
    </div>
  );
}
