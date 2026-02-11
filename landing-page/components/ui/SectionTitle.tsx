import { ReactNode } from 'react';

interface SectionTitleProps {
  children: ReactNode;
  className?: string;
}

export function SectionTitle({ children, className = '' }: SectionTitleProps) {
  return (
    <h2
      className={`text-center mb-16 relative font-ui text-foreground-dark after:content-[''] after:block after:w-12 after:h-[3px] after:bg-accent after:mx-auto after:mt-4 after:rounded-sm ${className}`}
    >
      {children}
    </h2>
  );
}
