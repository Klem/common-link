import { useTranslations } from 'next-intl';
import { SectionTitle } from '@/components/ui/SectionTitle';
import { Card } from '@/components/ui/Card';

export function Constat() {
  const t = useTranslations('landing.constat');

  return (
    <section className="py-24">
      <div className="max-w-container mx-auto px-8">
        <SectionTitle>{t('title')}</SectionTitle>
        <div className="grid grid-cols-1 md:grid-cols-2 gap-8">
          <Card variant="default">
            <h3 className="mb-4 text-primary">{t('donors.title')}</h3>
            <p>{t('donors.text')}</p>
          </Card>
          <Card variant="default">
            <h3 className="mb-4 text-primary">{t('associations.title')}</h3>
            <p>{t('associations.text')}</p>
          </Card>
        </div>
      </div>
    </section>
  );
}
