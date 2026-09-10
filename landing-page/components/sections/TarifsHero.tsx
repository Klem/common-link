import { useTranslations } from 'next-intl';

export function TarifsHero() {
  const t = useTranslations('tarifs.hero');

  return (
    <section className="bg-background py-20 px-8">
      <div className="max-w-container mx-auto grid lg:grid-cols-2 gap-12 items-center">
        <div>
          <div className="font-ui text-[0.8rem] font-semibold text-secondary uppercase tracking-wider mb-2">{t('label')}</div>
          <h1 className="font-ui text-[2rem] md:text-[2.6rem] font-extrabold text-foreground-dark mb-4">
            {t('title')}
            <br />
            <span className="italic-accent text-secondary">{t('titleAccent')}</span>
          </h1>
          <p className="text-foreground-muted text-[1.05rem] leading-relaxed mb-6 max-w-[440px]">{t('subtitle')}</p>
          <div className="flex gap-6 text-[0.9rem] font-ui font-semibold">
            <a href="#tarif-asso" className="text-primary hover:text-secondary transition-colors">{t('switchAsso')}</a>
            <a href="#tarif-don" className="text-primary hover:text-secondary transition-colors">{t('switchDonor')}</a>
          </div>
        </div>

        <div className="bg-primary text-white rounded-xl p-10 text-center">
          <div className="font-ui font-black text-[4rem] leading-none text-secondary">
            {t('bigNum')}<span className="text-[2.2rem] align-top">{t('bigUnit')}</span>
          </div>
          <div className="text-white/50 text-[0.85rem] mt-1 mb-5">{t('bigSub')}</div>
          <div className="h-px bg-white/10 mb-5" />
          <p className="text-white/80 text-[0.95rem] leading-relaxed">
            {t('bigLine1')}
            <br />
            <span className="text-white/50 text-[0.8rem]">{t('bigLine2')}</span>
          </p>
        </div>
      </div>
    </section>
  );
}
