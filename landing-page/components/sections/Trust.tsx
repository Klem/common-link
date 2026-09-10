import { useTranslations } from 'next-intl';

export function Trust() {
  const t = useTranslations('landing.trust');
  const items = t.raw('items') as { icon: string; title: string; text: string }[];

  return (
    <section className="py-20 px-8 bg-background text-center">
      <div className="max-w-container mx-auto">
        <div className="font-ui text-[0.8rem] font-semibold text-secondary uppercase tracking-wider mb-2">{t('label')}</div>
        <h2 className="font-ui text-[1.7rem] font-extrabold text-foreground-dark mb-10">{t('title')}</h2>
        <div className="grid md:grid-cols-3 gap-6">
          {items.map((item, i) => (
            <div key={i} className="bg-white border border-border rounded-lg p-8">
              <div className="text-[1.8rem] mb-3">{item.icon}</div>
              <h3 className="font-ui font-bold text-foreground-dark mb-2">{item.title}</h3>
              <p className="text-foreground-muted text-[0.9rem] leading-relaxed">{item.text}</p>
            </div>
          ))}
        </div>
      </div>
    </section>
  );
}
