import { useTranslations } from 'next-intl';
import { Button } from '@/components/ui/Button';

export function TransparencySection() {
  const t = useTranslations('landing.transparency');
  const features = t.raw('features') as string[];
  const timeline = t.raw('timeline') as { title: string; text: string; date: string; status: string }[];

  return (
    <section className="bg-primary text-white py-20 px-8">
      <div className="max-w-container mx-auto grid lg:grid-cols-2 gap-12">
        <div>
          <div className="font-ui text-[0.8rem] font-semibold text-secondary uppercase tracking-wider mb-2">{t('label')}</div>
          <h2 className="font-ui text-[1.8rem] md:text-[2.2rem] font-extrabold text-white mb-4">{t('title')}</h2>
          <p className="text-white/70 leading-relaxed mb-6">{t('text')}</p>
          <ul className="space-y-3 mb-8">
            {features.map((f, i) => (
              <li key={i} className="flex items-start gap-3 text-[0.9rem] text-white/85">
                <span className="text-secondary flex-shrink-0">●</span>
                {f}
              </li>
            ))}
          </ul>
          <Button href="/donateurs" size="lg">{t('ctaLabel')}</Button>
        </div>

        <div>
          <div className="text-[0.8rem] font-bold text-white/40 uppercase tracking-wider mb-6">{t('timelineLabel')}</div>
          <div className="space-y-6 border-l-2 border-white/10 pl-6">
            {timeline.map((item, i) => (
              <div key={i} className="relative">
                <span
                  className={`absolute -left-[31px] top-0 w-5 h-5 rounded-full flex items-center justify-center text-[0.7rem] font-bold ${
                    item.status === 'done' ? 'bg-secondary text-primary' : 'bg-white/20 text-white'
                  }`}
                >
                  {item.status === 'done' ? '✓' : '→'}
                </span>
                <h4 className="font-ui font-semibold text-white text-[0.95rem]">{item.title}</h4>
                <p className="text-white/60 text-[0.85rem]">{item.text}</p>
                <div className="text-white/40 text-[0.75rem] mt-0.5">{item.date}</div>
              </div>
            ))}
          </div>
        </div>
      </div>
    </section>
  );
}
