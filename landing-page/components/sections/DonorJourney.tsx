import { useTranslations } from 'next-intl';

export function DonorJourney() {
  const t = useTranslations('donors.journey');
  const steps = t.raw('steps') as { title: string; text: string; date: string; status: string; link?: string }[];

  return (
    <section className="py-20 px-8 bg-white">
      <div className="max-w-narrow mx-auto">
        <div className="font-ui text-[0.8rem] font-semibold text-secondary uppercase tracking-wider mb-2">{t('label')}</div>
        <h2 className="font-ui text-[1.7rem] font-extrabold text-foreground-dark mb-1">{t('title')}</h2>
        <p className="text-foreground-muted mb-8">{t('subtitle')}</p>

        <div className="bg-background border border-border rounded-xl p-6 mb-8">
          <div className="flex flex-wrap justify-between items-center gap-4">
            <div className="flex gap-3 items-center">
              <div className="text-[1.6rem]">{t('projectIcon')}</div>
              <div>
                <h3 className="font-ui font-bold text-foreground-dark text-[0.95rem]">{t('projectTitle')}</h3>
                <p className="text-foreground-muted text-[0.8rem]">{t('projectMeta')}</p>
              </div>
            </div>
            <div className="flex gap-2">
              <a className="font-ui font-semibold text-[0.8rem] text-primary bg-white border border-border rounded-full px-4 py-2 hover:bg-secondary-pale transition-colors cursor-pointer">
                {t('ctaTrace')}
              </a>
              <a className="font-ui font-semibold text-[0.8rem] text-white bg-secondary rounded-full px-4 py-2 hover:bg-secondary-light transition-colors cursor-pointer">
                {t('ctaShare')}
              </a>
            </div>
          </div>
        </div>

        <ol className="space-y-6 border-l-2 border-border pl-6">
          {steps.map((step, i) => (
            <li key={i} className="relative">
              <span
                className={`absolute -left-[31px] top-0 w-5 h-5 rounded-full flex items-center justify-center text-[0.7rem] font-bold ${
                  step.status === 'done' ? 'bg-secondary text-white' : 'bg-background-alt text-foreground-muted'
                }`}
              >
                {step.status === 'done' ? '✓' : '→'}
              </span>
              <h4 className="font-ui font-semibold text-foreground-dark text-[0.95rem]">{step.title}</h4>
              <p className="text-foreground-muted text-[0.85rem]">{step.text}</p>
              <div className="text-foreground-muted text-[0.75rem] mt-0.5">{step.date}</div>
              {step.link && (
                <a className="text-secondary text-[0.8rem] font-semibold cursor-pointer">{step.link}</a>
              )}
            </li>
          ))}
        </ol>
      </div>
    </section>
  );
}
