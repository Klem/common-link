import { useTranslations } from 'next-intl';

const CIRCLE_COLORS = ['bg-secondary', 'bg-accent', 'bg-primary'];

export function DonorSteps() {
  const t = useTranslations('donors.steps');
  const items = t.raw('items') as { num: string; title: string; text: string }[];

  return (
    <section className="py-20 px-8 bg-background">
      <div className="max-w-container mx-auto text-center">
        <div className="font-ui text-[0.8rem] font-semibold text-secondary uppercase tracking-wider mb-2">{t('label')}</div>
        <h2 className="font-ui text-[1.7rem] font-extrabold text-foreground-dark mb-10">{t('title')}</h2>
        <div className="grid md:grid-cols-3 gap-8">
          {items.map((item, i) => (
            <div key={item.num} className="px-4">
              <div
                className={`w-16 h-16 mx-auto mb-4 rounded-full flex items-center justify-center font-ui font-extrabold text-white text-[1.3rem] ${CIRCLE_COLORS[i % 3]}`}
              >
                {item.num}
              </div>
              <h3 className="font-ui font-bold text-foreground-dark mb-2">{item.title}</h3>
              <p className="text-foreground-muted text-[0.85rem] leading-relaxed">{item.text}</p>
            </div>
          ))}
        </div>
      </div>
    </section>
  );
}
