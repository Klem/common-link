import { useTranslations } from 'next-intl';

export function Why() {
  const t = useTranslations('landing.why');
  const items = t.raw('items') as { icon: string; title: string; text: string }[];

  return (
    <section className="py-20 px-8 bg-white">
      <div className="max-w-container mx-auto text-center">
        <div className="font-ui text-[0.8rem] font-semibold text-secondary uppercase tracking-wider mb-2">{t('label')}</div>
        <h2 className="font-ui text-[1.8rem] md:text-[2.4rem] font-extrabold text-foreground-dark max-w-[540px] mx-auto mb-4">
          {t('title')} <span className="text-secondary">{t('titleAccent')}</span>
        </h2>
        <p className="text-foreground-muted max-w-[520px] mx-auto mb-12">{t('subtitle')}</p>

        <div className="grid md:grid-cols-3 gap-8">
          {items.map((item, i) => (
            <div key={i}>
              <div className="w-14 h-14 mx-auto mb-4 rounded-lg bg-secondary-pale flex items-center justify-center text-[1.5rem]">
                {item.icon}
              </div>
              <h3 className="font-ui font-bold text-foreground-dark mb-2">{item.title}</h3>
              <p className="text-foreground-muted text-[0.9rem] leading-relaxed">{item.text}</p>
            </div>
          ))}
        </div>
      </div>
    </section>
  );
}
