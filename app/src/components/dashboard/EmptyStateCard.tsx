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
    <div className="bg-bg-2 border border-border rounded-[14px] p-[40px] flex flex-col items-center text-center">
      {icon && <p className="text-[48px] mb-4 leading-none">{icon}</p>}
      <h2 className="font-display font-bold text-[18px] text-text mb-[8px]">{title}</h2>
      <p className="text-[13px] text-text-2 max-w-[420px]">{subtitle}</p>
      {actionLabel && actionHref && (
        <Link
          href={actionHref}
          className="mt-[24px] bg-green text-black font-display font-bold text-[13px] px-[20px] py-[10px] rounded-md hover:opacity-90 transition-opacity"
        >
          {actionLabel}
        </Link>
      )}
    </div>
  );
}
