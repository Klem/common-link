import { ReactNode } from 'react';
import { Link } from '@/i18n/navigation';

interface ButtonProps {
  variant?: 'primary' | 'secondary' | 'accent' | 'outlineWhite' | 'ghost';
  size?: 'default' | 'lg' | 'sm';
  children: ReactNode;
  onClick?: () => void;
  href?: string;
  external?: boolean;
  type?: 'button' | 'submit';
  disabled?: boolean;
  className?: string;
}

const variantClasses: Record<string, string> = {
  primary:
    'bg-secondary text-white hover:bg-secondary-light hover:-translate-y-px hover:shadow-md',
  secondary:
    'bg-white text-primary border-[1.5px] border-border hover:border-primary hover:bg-secondary-pale',
  accent:
    'bg-primary text-white hover:bg-primary-light hover:-translate-y-px hover:shadow-md',
  outlineWhite:
    'bg-transparent text-white border-[1.5px] border-white/30 hover:border-white/60 hover:bg-white/10',
  ghost: 'bg-transparent text-primary hover:bg-secondary-pale',
};

const sizeClasses: Record<string, string> = {
  default: 'px-7 py-3.5 text-[0.95rem]',
  lg: 'px-9 py-[1.1rem] text-[1.05rem]',
  sm: 'px-4 py-2 text-[0.85rem]',
};

export function Button({
  variant = 'primary',
  size = 'default',
  children,
  onClick,
  href,
  external = false,
  type = 'button',
  disabled = false,
  className = '',
}: ButtonProps) {
  const classes = `inline-flex items-center justify-center gap-2 font-ui font-bold rounded-full border-none cursor-pointer transition-all duration-[250ms] leading-none ${variantClasses[variant]} ${sizeClasses[size]} ${disabled ? 'opacity-50 cursor-not-allowed' : ''} ${className}`;

  if (href && external) {
    return (
      <a href={href} className={classes}>
        {children}
      </a>
    );
  }

  if (href) {
    return (
      <Link href={href} className={classes}>
        {children}
      </Link>
    );
  }

  return (
    <button
      type={type}
      onClick={onClick}
      disabled={disabled}
      className={classes}
    >
      {children}
    </button>
  );
}
