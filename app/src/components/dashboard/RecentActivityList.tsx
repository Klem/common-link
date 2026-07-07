'use client';

import { useTranslations } from 'next-intl';
import { ActivityType, type ActivityItem } from '@/types/association';

interface RecentActivityListProps {
  items: ActivityItem[];
}

function getActivityIcon(type: ActivityType): { icon: string; cls: string } {
  switch (type) {
    case ActivityType.DONATION:
      return { icon: '♥', cls: 'g' };
    case ActivityType.PAYMENT:
      return { icon: '📋', cls: 'a' };
    case ActivityType.MILESTONE_REACHED:
      return { icon: '🎯', cls: 'i' };
  }
}

function formatRelativeTime(occurredAt: string): string {
  const diff = Date.now() - new Date(occurredAt).getTime();
  const minutes = Math.floor(diff / 60_000);
  if (minutes < 60) return `Il y a ${minutes} min`;
  const hours = Math.floor(minutes / 60);
  if (hours < 24) return `Il y a ${hours}h`;
  const days = Math.floor(hours / 24);
  return `Il y a ${days}j`;
}

/**
 * Recent activity feed for the association dashboard home page.
 * Displays up to 10 events (donations, payments, milestone hits).
 */
export function RecentActivityList({ items }: RecentActivityListProps) {
  const t = useTranslations('dashboard.association.home.activity');

  if (items.length === 0) {
    return (
      <div className="card no-hover">
        <div className="card-h">
          <h3>{t('title')}</h3>
        </div>
        <div className="card-b">
          <p className="empty-text">{t('empty')}</p>
        </div>
      </div>
    );
  }

  return (
    <div className="card no-hover">
      <div className="card-h">
        <h3>{t('title')}</h3>
      </div>
      <div className="card-b">
        {items.map((item, idx) => {
          const { icon, cls } = getActivityIcon(item.type);
          return (
            <div key={idx} className="act">
              <div className={`act-ic ${cls}`}>{icon}</div>
              <div>
                <div className="act-txt">{item.label}</div>
                <div className="act-time">{formatRelativeTime(item.occurredAt)}</div>
              </div>
            </div>
          );
        })}
      </div>
    </div>
  );
}
