'use client';

import Link from 'next/link';

interface EmptyStateCardProps {
  icon?: string;
  title: string;
  subtitle: string;
  actionLabel?: string;
  actionHref?: string;
}

export function EmptyStateCard({ icon, title, subtitle, actionLabel, actionHref }: EmptyStateCardProps) {
  return (
    <div className="empty-state">
      {icon && <p className="empty-state-icon">{icon}</p>}
      <h2 className="font-display font-bold text-lg text-text mb-2">{title}</h2>
      <p className="text-text-2 text-sm">{subtitle}</p>
      {actionLabel && actionHref && (
        <Link href={actionHref} className="btn btn-primary mt-6 inline-flex">
          {actionLabel}
        </Link>
      )}
    </div>
  );
}
