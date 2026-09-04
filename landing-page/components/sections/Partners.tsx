import Image from 'next/image';
import { useTranslations } from 'next-intl';

export function Partners() {
  const t = useTranslations('landing.partners');
  const items = t.raw('items') as { name: string; role: string; text: string; href: string }[];

  return (
    <section className="py-20 px-8 bg-background text-center">
      <div className="max-w-narrow mx-auto">
        <div className="font-ui text-[0.8rem] font-semibold text-secondary uppercase tracking-wider mb-2">{t('label')}</div>
        <h2 className="font-ui text-[1.6rem] font-extrabold text-foreground-dark mb-2">{t('title')}</h2>
        <p className="text-foreground-muted max-w-[560px] mx-auto mb-9">{t('subtitle')}</p>

        <div className="grid md:grid-cols-2 gap-6 text-left">
          {items.map((item, i) => (
            <a
              key={item.name}
              href={item.href}
              target="_blank"
              rel="noopener noreferrer"
              className="bg-white border border-border rounded-lg p-6 hover:-translate-y-0.5 hover:shadow-md transition-all duration-200"
            >
              {i === 0 ? (
                <div className="font-ui font-extrabold text-[1.3rem] text-foreground-dark mb-2">
                  <span className="text-secondary">ek</span>ads
                </div>
              ) : (
                <Image src="/partners/samson-conseil.png" alt={item.name} width={140} height={40} className="mb-2 h-8 w-auto object-contain" />
              )}
              <div className="text-secondary text-[0.8rem] font-semibold mb-2">{item.role}</div>
              <p className="text-foreground-muted text-[0.85rem] leading-relaxed">{item.text}</p>
            </a>
          ))}
        </div>
        <p className="text-foreground-muted text-[0.8rem] mt-6">{t('note')}</p>
      </div>
    </section>
  );
}
