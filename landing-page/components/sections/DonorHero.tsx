import { useTranslations } from 'next-intl';
import { Button } from '@/components/ui/Button';
import { APP_URL } from '@/lib/constants';

export function DonorHero() {
  const t = useTranslations('donors.hero');
  const items = t.raw('items') as { icon: string; title: string; text: string }[];

  return (
    <section className="bg-background py-20 px-8">
      <div className="max-w-container mx-auto grid lg:grid-cols-2 gap-16 items-center">
        <div>
          <div className="font-ui text-[0.8rem] font-semibold text-secondary uppercase tracking-wider mb-2">{t('label')}</div>
          <h1 className="font-ui text-[2rem] md:text-[2.7rem] font-extrabold text-foreground-dark mb-5">
            {t('title')} <span className="italic-accent text-secondary">{t('titleAccent')}</span>
          </h1>
          <p className="text-foreground-muted text-[1.05rem] leading-relaxed mb-8">{t('text')}</p>

          <div className="flex flex-col gap-4 mb-10">
            {items.map((item, i) => (
              <div key={i} className="flex gap-3 items-center">
                <div className="w-10 h-10 rounded-md bg-secondary-pale flex items-center justify-center text-[1.1rem] flex-shrink-0">
                  {item.icon}
                </div>
                <div>
                  <div className="font-ui font-bold text-[0.95rem] text-foreground-dark">{item.title}</div>
                  <div className="text-[0.8rem] text-foreground-muted">{item.text}</div>
                </div>
              </div>
            ))}
          </div>
          <Button href={APP_URL} external size="lg">{t('cta')}</Button>
        </div>

        <div className="bg-white border border-border rounded-xl shadow-lg p-7">
          <div className="flex items-center gap-4 mb-6">
            <div className="w-14 h-14 rounded-full bg-secondary text-white font-ui font-extrabold flex items-center justify-center text-[1.1rem]">
              ML
            </div>
            <div>
              <div className="font-ui font-extrabold text-foreground-dark">{t('profileName')}</div>
              <div className="text-[0.8rem] text-foreground-muted">{t('profileSince')}</div>
            </div>
          </div>
          <div className="grid grid-cols-2 gap-3 mb-5">
            <div className="bg-background-alt rounded-lg p-4">
              <div className="text-[0.7rem] uppercase tracking-wide text-foreground-muted mb-2">{t('statDonsLabel')}</div>
              <div className="font-ui font-extrabold text-[1.4rem] text-foreground-dark">{t('statDonsValue')}</div>
              <div className="text-[0.75rem] text-secondary-light font-semibold">{t('statDonsChange')}</div>
            </div>
            <div className="bg-background-alt rounded-lg p-4">
              <div className="text-[0.7rem] uppercase tracking-wide text-foreground-muted mb-2">{t('statAssosLabel')}</div>
              <div className="font-ui font-extrabold text-[1.4rem] text-foreground-dark">{t('statAssosValue')}</div>
              <div className="text-[0.75rem] text-foreground-muted">{t('statAssosText')}</div>
            </div>
          </div>
          <div className="rounded-lg p-4 mb-5" style={{ background: 'rgba(78,205,196,0.06)', border: '1px solid rgba(78,205,196,0.2)' }}>
            <div className="text-[0.8rem] font-bold text-secondary-light mb-1">{t('taxTitle')}</div>
            <div className="font-ui font-extrabold text-[1.6rem] text-foreground-dark">{t('taxAmount')}</div>
            <div className="text-[0.75rem] text-foreground-muted">{t('taxNote')}</div>
          </div>
          <div className="text-[0.8rem] font-bold text-foreground-muted mb-2">{t('lastDonsLabel')}</div>
          <div className="space-y-2">
            <div className="flex justify-between items-center text-[0.85rem]">
              <span>{t('don1.name')}</span>
              <span className="font-bold">{t('don1.amount')}</span>
            </div>
            <div className="flex justify-between items-center text-[0.85rem]">
              <span>{t('don2.name')}</span>
              <span className="font-bold">{t('don2.amount')}</span>
            </div>
          </div>
        </div>
      </div>
    </section>
  );
}
