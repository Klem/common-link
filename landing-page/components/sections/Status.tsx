import { useTranslations } from 'next-intl';
import { SectionTitle } from '@/components/ui/SectionTitle';
import { Badge } from '@/components/ui/Badge';

export function Status() {
  const t = useTranslations('landing.status');

  const badgeVariantMap: Record<string, 'status-pending' | 'status-upcoming'> = {
    'in-progress': 'status-pending',
    upcoming: 'status-upcoming',
  };

  return (
    <section className="py-24">
      <div className="max-w-container mx-auto px-8">
        <SectionTitle>{t('title')}</SectionTitle>
        <div className="grid grid-cols-1 md:grid-cols-3 gap-8">
          {[0, 1, 2].map((i) => (
            <div
              key={i}
              className="bg-white border border-border rounded-lg p-12"
            >
              <h3 className="mb-2">{t(`items.${i}.title`)}</h3>
              <p className="text-foreground-muted">{t(`items.${i}.text`)}</p>
              <Badge
                variant={badgeVariantMap[t(`items.${i}.badgeType`)] || 'status-pending'}
                className="mt-4"
              >
                {t(`items.${i}.badge`)}
              </Badge>
            </div>
          ))}
        </div>
      </div>
    </section>
  );
}
