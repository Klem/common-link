import { ReactNode } from 'react';

interface BadgeProps {
  variant?: 'hero' | 'status-verified' | 'status-pending' | 'status-upcoming' | 'feature';
  children: ReactNode;
  className?: string;
}

const variantClasses: Record<string, string> = {
  hero: 'bg-secondary-pale text-secondary text-[0.8rem] px-4 py-1.5 rounded-[20px] tracking-wide',
  'status-verified': 'bg-success/10 text-success text-[0.8rem] px-3 py-1 rounded-[16px]',
  'status-pending': 'bg-accent/[0.12] text-accent text-[0.8rem] px-3 py-1 rounded-[16px]',
  'status-upcoming': 'bg-secondary-pale text-secondary text-[0.8rem] px-3 py-1 rounded-[16px]',
  feature: 'bg-secondary-pale text-secondary text-[0.75rem] px-2.5 py-0.5 rounded-lg',
};

export function Badge({
  variant = 'hero',
  children,
  className = '',
}: BadgeProps) {
  return (
    <span
      className={`inline-block font-ui font-semibold ${variantClasses[variant]} ${className}`}
    >
      {children}
    </span>
  );
}
