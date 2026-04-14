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
}

/**
 * Tab bar for the campaign editor.
 *
 * Renders 3 tabs for Sprint 4.1: Infos, Budget prév., Milestones.
 * Active tab is highlighted with a background and shadow.
 */
export function CampaignTabs({ activeTab, onTabChange, milestoneCount }: CampaignTabsProps) {
  const t = useTranslations('dashboard.campaigns');

  const tabs: Tab[] = [
    { id: 'info', icon: '⚙️', labelKey: 'editor.tabs.info' },
    { id: 'budget', icon: '💶', labelKey: 'editor.tabs.budget' },
    { id: 'milestones', icon: '🎯', labelKey: 'editor.tabs.milestones', count: milestoneCount },
  ];

  return (
    <div
      className="flex gap-[4px] rounded-[14px] border border-[var(--color-border)] p-[4px] mb-[24px] overflow-x-auto"
      style={{ background: 'var(--color-bg-2)' }}
    >
      {tabs.map((tab) => (
        <button
          key={tab.id}
          type="button"
          onClick={() => onTabChange(tab.id)}
          className={`
            flex items-center gap-[6px] px-[18px] py-[10px] rounded-[9px] text-[13px] font-semibold
            whitespace-nowrap cursor-pointer transition-all
            ${
              activeTab === tab.id
                ? 'text-[var(--color-text)] shadow-md'
                : 'text-[var(--color-text-2)] hover:text-[var(--color-text)] hover:bg-[var(--color-muted)]/20'
            }
          `}
          style={activeTab === tab.id ? { background: 'var(--color-bg-3)' } : undefined}
        >
          <span>{tab.icon}</span>
          <span>{t(tab.labelKey)}</span>
          {tab.count !== undefined && (
            <span
              className="text-[11px] font-bold px-[6px] py-[1px] rounded-full"
              style={{ background: 'var(--color-bg)', color: 'var(--color-text-2)' }}
            >
              {tab.count}
            </span>
          )}
        </button>
      ))}
    </div>
  );
}
