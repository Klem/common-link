import { useTranslations } from 'next-intl';
import { Button } from '@/components/ui/Button';
import { APP_URL } from '@/lib/constants';

export function AssoTeaser() {
  const t = useTranslations('landing.asso');
  const steps = t.raw('steps') as { num: string; title: string; text: string }[];

  return (
    <section className="py-20 px-8 bg-white">
      <div className="max-w-container mx-auto grid lg:grid-cols-2 gap-12 items-center">
        <div>
          <div className="font-ui text-[0.8rem] font-semibold text-secondary uppercase tracking-wider mb-2">{t('label')}</div>
          <h2 className="font-ui text-[1.8rem] font-extrabold text-foreground-dark mb-3">{t('title')}</h2>
          <p className="text-foreground-muted leading-relaxed mb-8 max-w-[460px]">{t('text')}</p>

          <div className="space-y-6 mb-8">
            {steps.map((step) => (
              <div key={step.num} className="flex gap-4">
                <div className="w-8 h-8 flex-shrink-0 rounded-full bg-secondary-pale text-secondary-light font-ui font-extrabold flex items-center justify-center text-[0.9rem]">
                  {step.num}
                </div>
                <div>
                  <h4 className="font-ui font-bold text-foreground-dark text-[0.95rem]">{step.title}</h4>
                  <p className="text-foreground-muted text-[0.85rem] leading-relaxed">{step.text}</p>
                </div>
              </div>
            ))}
          </div>

          <div className="flex flex-wrap gap-4">
            <Button href={APP_URL} external variant="accent">{t('ctaCreate')}</Button>
            <Button href="/tarifs" variant="ghost">{t('ctaTarifs')}</Button>
          </div>
        </div>

        <div className="bg-primary rounded-xl p-6">
          <div className="flex items-center gap-2 mb-4">
            <span className="w-2.5 h-2.5 rounded-full" style={{ background: '#FF6B5B' }} />
            <span className="w-2.5 h-2.5 rounded-full" style={{ background: '#FFB347' }} />
            <span className="w-2.5 h-2.5 rounded-full" style={{ background: '#34C759' }} />
            <span className="flex-1 text-center text-[0.75rem] text-white/40">{t('visualLabel')}</span>
          </div>
          <div className="text-[0.8rem] font-bold text-white/50 uppercase tracking-wide mb-4">{t('monthLabel')}</div>
          <div className="space-y-2 mb-4">
            <div className="flex justify-between text-[0.9rem]">
              <span className="text-white/70">{t('statCollected')}</span>
              <span className="font-bold text-secondary">{t('statCollectedValue')}</span>
            </div>
            <div className="flex justify-between text-[0.9rem]">
              <span className="text-white/70">{t('statNewDonors')}</span>
              <span className="font-bold text-white">{t('statNewDonorsValue')}</span>
            </div>
            <div className="flex justify-between text-[0.9rem]">
              <span className="text-white/70">{t('statExpenses')}</span>
              <span className="font-bold text-white">{t('statExpensesValue')}</span>
            </div>
          </div>
          <div className="mt-4">
            <div className="text-[0.75rem] text-white/50 mb-2">{t('progressLabel')}</div>
            <div className="h-1.5 rounded-full bg-white/10 overflow-hidden">
              <div className="h-full rounded-full bg-secondary" style={{ width: t('progressPercent') }} />
            </div>
            <div className="flex justify-between text-[0.75rem] mt-1">
              <span className="text-white/50">{t('progressAmount')}</span>
              <span className="text-secondary font-bold">{t('progressPercent')}</span>
            </div>
          </div>
        </div>
      </div>
    </section>
  );
}
