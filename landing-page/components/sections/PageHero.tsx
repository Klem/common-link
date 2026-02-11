import { ReactNode } from 'react';
import { Badge } from '@/components/ui/Badge';

interface PageHeroProps {
  badge: string;
  title: string;
  description: string;
  children?: ReactNode;
}

export function PageHero({ badge, title, description, children }: PageHeroProps) {
  return (
    <section className="py-24 pb-16 text-center bg-gradient-to-b from-background to-background-alt">
      <div className="max-w-container mx-auto px-8">
        <Badge variant="hero" className="mb-6">
          {badge}
        </Badge>
        <h1 className="max-w-[750px] mx-auto mb-6">{title}</h1>
        <p className="max-w-[600px] mx-auto text-[1.15rem] text-foreground-muted">
          {description}
        </p>
        {children}
      </div>
    </section>
  );
}
