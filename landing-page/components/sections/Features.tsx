import { useTranslations } from 'next-intl';
import { SectionTitle } from '@/components/ui/SectionTitle';
import { Card } from '@/components/ui/Card';
import { Badge } from '@/components/ui/Badge';

const icons = [
  // Eye icon — Transparence
  <svg key="eye" width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
    <path d="M1 12s4-8 11-8 11 8 11 8-4 8-11 8-11-8-11-8z" />
    <circle cx="12" cy="12" r="3" />
  </svg>,
  // Document icon — Preuves
  <svg key="doc" width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
    <path d="M14 2H6a2 2 0 00-2 2v16a2 2 0 002 2h12a2 2 0 002-2V8z" />
    <polyline points="14 2 14 8 20 8" />
    <line x1="16" y1="13" x2="8" y2="13" />
    <line x1="16" y1="17" x2="8" y2="17" />
  </svg>,
  // Check icon — Simplicité
  <svg key="check" width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
    <polyline points="20 6 9 17 4 12" />
  </svg>,
];

export function Features() {
  const t = useTranslations('landing.features');

  return (
    <section className="py-24 bg-background-alt">
      <div className="max-w-container mx-auto px-8">
        <SectionTitle>{t('title')}</SectionTitle>
        <div className="grid grid-cols-1 md:grid-cols-3 gap-8">
          {[0, 1, 2].map((i) => (
            <Card key={i} variant="feature" icon={icons[i]}>
              <h3 className="mb-2">{t(`items.${i}.title`)}</h3>
              <p className="text-foreground-muted mx-auto">{t(`items.${i}.text`)}</p>
              <Badge variant="feature" className="mt-4">
                {t(`items.${i}.tag`)}
              </Badge>
            </Card>
          ))}
        </div>
      </div>
    </section>
  );
}
