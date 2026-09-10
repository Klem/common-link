import { useTranslations } from 'next-intl';
import { APP_URL } from '@/lib/constants';

export function Campaigns() {
  const t = useTranslations('landing.campaigns');

  return (
    <section className="py-20 px-8 bg-background">
      <div className="max-w-container mx-auto">
        <div className="flex flex-wrap justify-between items-center gap-4 mb-8">
          <div>
            <div className="font-ui text-[0.8rem] font-semibold text-secondary uppercase tracking-wider mb-2">{t('label')}</div>
            <h2 className="font-ui text-[1.7rem] font-extrabold text-foreground-dark">{t('title')}</h2>
          </div>
          <a
            href={APP_URL}
            className="inline-flex items-center gap-2 font-ui font-bold text-primary bg-white border-[1.5px] border-border rounded-full px-6 py-3 text-[0.9rem] hover:border-primary hover:bg-secondary-pale transition-colors"
          >
            {t('ctaAll')} →
          </a>
        </div>

        <div className="bg-white border border-border rounded-xl py-16 px-8 text-center">
          <div className="text-[2.5rem] mb-3">🌱</div>
          <h3 className="font-ui font-bold text-foreground-dark mb-1.5">{t('emptyTitle')}</h3>
          <p className="text-foreground-muted text-[0.9rem]">{t('emptyText')}</p>
        </div>
      </div>
    </section>
  );
}
