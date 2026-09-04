import { useTranslations } from 'next-intl';

export function AssociationFeatures() {
  const t = useTranslations('associations.features');
  const items = t.raw('items') as { icon: string; title: string; text: string }[];

  return (
    <section className="py-20 px-8 bg-background">
      <div className="max-w-container mx-auto text-center">
        <div className="font-ui text-[0.8rem] font-semibold text-secondary uppercase tracking-wider mb-2">{t('label')}</div>
        <h2 className="font-ui text-[1.7rem] font-extrabold text-foreground-dark mb-10">{t('title')}</h2>
        <div className="grid md:grid-cols-3 gap-6 text-left">
          {items.map((item, i) => (
            <div key={i} className="bg-white border border-border rounded-lg p-7">
              <div className="text-[1.6rem] mb-3">{item.icon}</div>
              <h3 className="font-ui font-bold text-foreground-dark mb-2 text-[1rem]">{item.title}</h3>
              <p className="text-foreground-muted text-[0.85rem] leading-relaxed">{item.text}</p>
            </div>
          ))}
        </div>
      </div>
    </section>
  );
}
