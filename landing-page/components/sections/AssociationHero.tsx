import { useTranslations } from 'next-intl';
import { Button } from '@/components/ui/Button';
import { APP_URL } from '@/lib/constants';

export function AssociationHero() {
  const t = useTranslations('associations.hero');

  return (
    <section className="bg-primary text-white py-20 px-8">
      <div className="max-w-container mx-auto grid lg:grid-cols-2 gap-16 items-center">
        <div>
          <div className="font-ui text-[0.8rem] font-semibold text-secondary uppercase tracking-wider mb-2">{t('label')}</div>
          <h1 className="font-ui text-[2rem] md:text-[2.7rem] font-extrabold text-white mb-5">
            {t('title')} <span className="italic-accent text-accent">{t('titleAccent')}</span>
          </h1>
          <p className="text-white/70 text-[1.05rem] leading-relaxed mb-9 max-w-[480px]">{t('text')}</p>
          <div className="flex flex-wrap gap-4 mb-12">
            <Button href={APP_URL} external size="lg">{t('ctaCreate')}</Button>
            <Button href="/tarifs" variant="outlineWhite" size="lg">{t('ctaTarifs')}</Button>
          </div>
          <div className="flex gap-8 pt-8 border-t border-white/10">
            <div>
              <div className="font-ui font-extrabold text-[1.8rem] text-secondary">{t('stat1.num')}</div>
              <div className="text-[0.8rem] text-white/50">{t('stat1.text')}</div>
            </div>
            <div>
              <div className="font-ui font-extrabold text-[1.8rem] text-accent">{t('stat2.num')}</div>
              <div className="text-[0.8rem] text-white/50">{t('stat2.text')}</div>
            </div>
            <div>
              <div className="font-ui font-extrabold text-[1.8rem]" style={{ color: '#FF6B5B' }}>{t('stat3.num')}</div>
              <div className="text-[0.8rem] text-white/50">{t('stat3.text')}</div>
            </div>
          </div>
        </div>

        <div className="bg-white/[0.06] border border-white/10 rounded-xl p-6">
          <div className="text-[0.8rem] font-bold text-white/40 uppercase tracking-wide mb-4">{t('visualLabel')}</div>
          <div className="grid grid-cols-2 gap-3 mb-5">
            <div className="rounded-lg p-4" style={{ background: 'rgba(78,205,196,0.12)' }}>
              <div className="font-ui font-extrabold text-[1.4rem] text-secondary">{t('statCollected.value')}</div>
              <div className="text-[0.75rem] text-white/50">{t('statCollected.label')}</div>
            </div>
            <div className="rounded-lg p-4" style={{ background: 'rgba(255,179,71,0.1)' }}>
              <div className="font-ui font-extrabold text-[1.4rem] text-accent">{t('statDonors.value')}</div>
              <div className="text-[0.75rem] text-white/50">{t('statDonors.label')}</div>
            </div>
          </div>
          <div className="bg-white/[0.04] rounded-lg p-4 mb-4">
            <div className="text-[0.75rem] text-white/40 mb-2">{t('progressLabel')}</div>
            <div className="h-1.5 rounded-full bg-white/10 overflow-hidden">
              <div className="h-full rounded-full bg-secondary" style={{ width: t('progressPercent') }} />
            </div>
            <div className="flex justify-between text-[0.75rem] mt-1.5">
              <span className="text-white/50">{t('progressAmount')}</span>
              <span className="text-secondary font-bold">{t('progressPercent')}</span>
            </div>
          </div>
          <div className="text-[0.75rem] text-white/40 mb-2">{t('lastExpensesLabel')}</div>
          <div className="space-y-1.5">
            <div className="flex justify-between text-[0.8rem]">
              <span className="text-white/70">{t('expense1.label')}</span>
              <span className="text-secondary font-bold">{t('expense1.amount')}</span>
            </div>
            <div className="flex justify-between text-[0.8rem]">
              <span className="text-white/70">{t('expense2.label')}</span>
              <span className="text-secondary font-bold">{t('expense2.amount')}</span>
            </div>
          </div>
        </div>
      </div>
    </section>
  );
}
