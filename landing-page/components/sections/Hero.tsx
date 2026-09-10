import { useTranslations } from 'next-intl';
import { Button } from '@/components/ui/Button';
import { APP_URL } from '@/lib/constants';

export function Hero() {
  const t = useTranslations('landing.hero');
  const trust = t.raw('trust') as { strong: string; text: string }[];

  return (
    <section className="relative overflow-hidden bg-primary text-white">
      <div className="absolute inset-0 pointer-events-none" style={{ background: 'radial-gradient(ellipse at 70% 50%, rgba(78,205,196,0.12) 0%, transparent 60%)' }} />
      <div className="relative max-w-container mx-auto px-8 py-20 grid lg:grid-cols-2 gap-12 items-center">
        <div>
          <div className="font-ui text-[0.85rem] font-semibold text-secondary mb-4">{t('eyebrow')}</div>
          <h1 className="font-ui text-[2.2rem] md:text-[3rem] font-extrabold text-white leading-tight mb-5">
            {t('title')} <span className="italic-accent text-secondary">{t('titleAccent')}</span>
          </h1>
          <p className="text-white/70 text-[1.1rem] leading-relaxed mb-8 max-w-[480px]">{t('subtitle')}</p>
          <div className="flex flex-wrap gap-4 mb-10">
            <Button href={APP_URL} external size="lg">♥ {t('ctaDonate')}</Button>
            <Button href={APP_URL} external variant="outlineWhite" size="lg">{t('ctaCreate')} →</Button>
          </div>
          <div className="flex flex-wrap gap-6 text-[0.85rem] text-white/80">
            {trust.map((item, i) => (
              <div key={i}>✓ <strong className="text-white">{item.strong}</strong> {item.text}</div>
            ))}
          </div>
        </div>

        <div className="relative hidden lg:block">
          <div className="relative rounded-xl overflow-hidden aspect-[4/3] bg-white/5 border border-white/10 flex items-center justify-center">
            <div className="text-[80px] opacity-30">🤝</div>
            <div className="absolute bottom-0 left-0 right-0 h-32" style={{ background: 'linear-gradient(to top, rgba(50,50,125,0.8), transparent)' }} />
            <div className="absolute bottom-5 left-5 right-5">
              <div className="text-[0.75rem] text-white/70 font-semibold">{t('visualAssoc')}</div>
              <div className="text-[0.9rem] text-white font-bold">{t('visualProject')}</div>
            </div>
          </div>
          <div className="absolute -left-6 top-8 bg-white text-foreground-dark rounded-lg shadow-lg p-4 w-[180px]">
            <div className="font-ui font-extrabold text-[1.3rem] text-foreground-dark">{t('floatAmount')}</div>
            <div className="text-[0.75rem] text-foreground-muted">💚 {t('floatLabel')}</div>
            <div className="flex items-center gap-1 mt-1.5">
              <span className="w-1.5 h-1.5 rounded-full bg-secondary" />
              <span className="text-[0.7rem] text-secondary-light font-semibold">{t('floatRealtime')}</span>
            </div>
          </div>
          <div className="absolute -right-4 bottom-6 bg-white text-foreground-dark rounded-lg shadow-lg p-4 w-[200px]">
            <div className="text-[0.7rem] text-foreground-muted font-semibold mb-1">{t('floatLastExpenseLabel')}</div>
            <div className="text-[0.8rem] font-bold text-foreground-dark">{t('floatLastExpense')}</div>
            <div className="text-[0.7rem] text-secondary-light mt-0.5">{t('floatPublished')}</div>
          </div>
        </div>
      </div>
    </section>
  );
}
