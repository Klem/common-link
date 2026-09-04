import { useTranslations } from 'next-intl';

export function TarifsDonor() {
  const t = useTranslations('tarifs.donor');

  return (
    <section id="tarif-don" className="bg-primary text-white py-20 px-8">
      <div className="max-w-narrow mx-auto grid md:grid-cols-[auto_1fr] gap-10 items-center">
        <div className="font-ui font-black text-[3.5rem] leading-none text-secondary text-center">
          {t('bigNum')}<span className="text-[1.8rem] align-top">{t('bigUnit')}</span>
        </div>
        <div>
          <div className="font-ui text-[0.8rem] font-semibold text-secondary uppercase tracking-wider mb-2">{t('label')}</div>
          <h2 className="font-ui text-[1.7rem] font-extrabold text-white mb-3">{t('title')}</h2>
          <p className="text-white/75 leading-relaxed mb-2">{t('text1')}</p>
          <p className="text-white/75 leading-relaxed mb-5">{t('text2')}</p>
          <div className="inline-block bg-white/10 rounded-full px-5 py-2 text-[0.85rem] font-ui font-semibold text-secondary">
            {t('badge')}
          </div>
        </div>
      </div>
    </section>
  );
}
