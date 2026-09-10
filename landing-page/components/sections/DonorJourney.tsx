import { useTranslations } from 'next-intl';

/**
 * Section « Le parcours de votre don » de la page Donateurs.
 * Markup, classes et styles inline repris à l'identique de la maquette
 * (`p2-donateurs.html`, section `.journey`). Ne pas remplacer par du Tailwind.
 */
export function DonorJourney() {
  const t = useTranslations('donors.journey');
  const steps = t.raw('steps') as {
    title: string;
    text: string;
    date: string;
    status: string;
    link?: string;
  }[];

  return (
    <section className="section bg-cream">
      <div className="max-w" style={{ maxWidth: '940px' }}>
        <div className="section-label">{t('label')}</div>
        <h2 style={{ fontSize: '32px', marginBottom: '8px' }}>{t('title')}</h2>
        <p style={{ color: 'var(--slate-lavender)', marginBottom: '28px' }}>
          {t('subtitle')}
        </p>

        <div className="journey">
          <div className="journey-head">
            <div className="journey-icon">{t('projectIcon')}</div>
            <div>
              <h3>{t('projectTitle')}</h3>
              <p>{t('projectMeta')}</p>
            </div>
            <div className="journey-actions">
              <a className="btn btn-ghost btn-sm">{t('ctaTrace')}</a>
              <a className="btn btn-primary btn-sm">{t('ctaShare')}</a>
            </div>
          </div>

          <ol className="journey-steps">
            {steps.map((step, i) => (
              <li className={step.status} key={i}>
                <h4>{step.title}</h4>
                <p>{step.text}</p>
                <span className="journey-when">{step.date}</span>
                {step.link && <a className="journey-link">{step.link}</a>}
              </li>
            ))}
          </ol>
        </div>
      </div>
    </section>
  );
}
