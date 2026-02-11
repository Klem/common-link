import { ReactNode } from 'react';

interface CardProps {
  variant?: 'default' | 'feature' | 'preview' | 'status';
  children: ReactNode;
  icon?: ReactNode;
  className?: string;
}

const baseClasses =
  'bg-white border border-border rounded-lg transition-all duration-[250ms]';

const variantClasses: Record<string, string> = {
  default: 'p-12 hover:-translate-y-0.5 hover:shadow-md',
  feature: 'p-12 text-center hover:-translate-y-[3px] hover:shadow-lg',
  preview:
    'p-8 text-center relative overflow-hidden before:content-[""] before:absolute before:top-0 before:left-0 before:right-0 before:h-[3px] before:bg-gradient-to-r before:from-primary before:to-secondary',
  status: 'p-12',
};

export function Card({
  variant = 'default',
  children,
  icon,
  className = '',
}: CardProps) {
  return (
    <div className={`${baseClasses} ${variantClasses[variant]} ${className}`}>
      {icon && variant === 'feature' && (
        <div className="w-14 h-14 mx-auto mb-6 bg-secondary-pale rounded-md flex items-center justify-center text-primary">
          {icon}
        </div>
      )}
      {icon && variant === 'preview' && (
        <div className="w-14 h-14 mx-auto mb-4 bg-secondary-pale rounded-md flex items-center justify-center text-primary">
          {icon}
        </div>
      )}
      {children}
    </div>
  );
}
