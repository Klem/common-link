import { useTranslations } from 'next-intl';

export function TarifsAsso() {
  const t = useTranslations('tarifs.asso');
  const notPayItems = t.raw('notPayItems') as string[];

  return (
    <section id="tarif-asso" className="py-20 px-8 bg-white">
      <div className="max-w-narrow mx-auto">
        <div className="bg-background border border-border rounded-xl p-8 text-center mb-12">
          <div className="text-foreground-muted text-[0.85rem] font-ui font-semibold uppercase tracking-wide mb-3">
            {t('flowTitle')}
          </div>
          <div className="font-ui text-[1.3rem] text-foreground-dark leading-snug">
            {t('flowClaimPre')} <strong className="text-secondary-light">{t('flowClaimStrong')}</strong>
          </div>
          <p className="text-foreground-muted text-[0.85rem] mt-3">{t('flowNote')}</p>
        </div>

        <h2 className="font-ui text-[1.4rem] font-extrabold text-foreground-dark text-center mb-6">{t('notPayTitle')}</h2>
        <div className="grid grid-cols-2 md:grid-cols-3 gap-3 mb-2">
          {notPayItems.map((item, i) => (
            <div key={i} className="bg-background rounded-lg px-4 py-3 text-center text-[0.85rem] font-ui font-semibold text-foreground-dark">
              ✕ {item}
            </div>
          ))}
        </div>
        <p className="text-foreground-muted text-[0.8rem] text-center mb-12">{t('subNote')}</p>

        <div className="grid md:grid-cols-2 gap-6 mb-6">
          <div className="border border-border rounded-lg p-6">
            <div className="text-[1.4rem] mb-2">🔓</div>
            <h3 className="font-ui font-bold text-foreground-dark mb-1.5">{t('soloTitle')}</h3>
            <p className="text-foreground-muted text-[0.85rem] leading-relaxed">{t('soloText')}</p>
          </div>
          <div className="border border-border rounded-lg p-6">
            <div className="text-[1.4rem] mb-2">🧾</div>
            <h3 className="font-ui font-bold text-foreground-dark mb-1.5">{t('billedTitle')}</h3>
            <p className="text-foreground-muted text-[0.85rem] leading-relaxed">{t('billedText')}</p>
          </div>
        </div>
        <p className="text-foreground-muted text-[0.75rem] text-center">{t('footNote')}</p>
      </div>
    </section>
  );
}
