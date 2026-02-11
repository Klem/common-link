import { useTranslations } from 'next-intl';
import { Badge } from '@/components/ui/Badge';
import { Button } from '@/components/ui/Button';

export function Hero() {
  const t = useTranslations('landing');

  return (
    <section className="py-24 pb-16 text-center">
      <div className="max-w-container mx-auto px-8">
        <Badge variant="hero">{t('hero.badge')}</Badge>
        <h1 className="max-w-[700px] mx-auto mt-6 mb-6">
          {t('hero.title')}
        </h1>
        <p className="text-[1.15rem] text-foreground-muted max-w-[540px] mx-auto mb-8">
          {t('hero.subtitle')}
        </p>
        <Button variant="primary" href="#comment-ca-marche">
          {t('hero.cta')}
        </Button>
        <p className="font-ui text-[0.85rem] text-foreground-muted mt-6">
          {t('hero.launch')}
        </p>
      </div>

      {/* Journal card visual */}
      <div className="max-w-[360px] mx-auto mt-16">
        <div className="bg-white border border-border rounded-lg overflow-hidden shadow-lg">
          <div className="font-ui text-[0.85rem] font-semibold text-foreground-dark px-6 py-4 border-b border-border-light">
            {t('journal.title')}
          </div>
          {[0, 1, 2].map((i) => (
            <div
              key={i}
              className="flex justify-between items-center px-6 py-4 border-b border-border-light"
            >
              <span
                className={`font-ui text-[0.75rem] font-semibold px-3 py-1 rounded-xl ${
                  t(`journal.rows.${i}.badge`) === 'En cours' ||
                  t(`journal.rows.${i}.badge`) === 'Pending'
                    ? 'text-accent bg-accent/[0.12]'
                    : 'text-success bg-success/10'
                }`}
              >
                {t(`journal.rows.${i}.badge`)}
              </span>
              <span className="font-ui font-bold text-foreground-dark">
                {t(`journal.rows.${i}.amount`)}
              </span>
            </div>
          ))}
          <div className="flex justify-between items-center px-6 py-4 font-ui text-[0.9rem] text-foreground-muted">
            <span>{t('journal.total')}</span>
            <strong className="text-primary text-[1.1rem]">
              {t('journal.totalAmount')}
            </strong>
          </div>
        </div>
      </div>
    </section>
  );
}
