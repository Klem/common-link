import { useTranslations } from 'next-intl';
import { Link } from '@/i18n/navigation';

/**
 * Section « Transparence » de la page d'accueil.
 * Markup et classes repris à l'identique de la maquette (`section.transparency-section`,
 * `CommonLink UI V2 Julian.html`, page 1). Ne pas remplacer par des classes Tailwind :
 * le CSS de la maquette est la source de vérité.
 */
export function TransparencySection() {
  const t = useTranslations('landing.transparency');
  const features = t.raw('features') as { icon: string; text: string }[];
  const timeline = t.raw('timeline') as {
    title: string;
    text: string;
    date: string;
    status: string;
  }[];

  return (
    <section className="transparency-section">
      <div className="transparency-inner">
        <div className="transparency-content">
          <div className="section-label">{t('label')}</div>
          <h2>{t('title')}</h2>
          <p>{t('text')}</p>
          <div className="transparency-features">
            {features.map((feature, i) => (
              <div className="transparency-feat" key={i}>
                <div className="transparency-feat-icon">{feature.icon}</div>
                {feature.text}
              </div>
            ))}
          </div>
          <Link href="/donateurs" className="btn btn-primary btn-lg">
            {t('ctaLabel')}
          </Link>
        </div>
        <div className="transparency-timeline-wrap">
          <div
            style={{
              fontSize: '13px',
              fontWeight: 700,
              color: 'rgba(255,255,255,0.4)',
              textTransform: 'uppercase',
              letterSpacing: '1.5px',
              marginBottom: '24px',
            }}
          >
            {t('timelineLabel')}
          </div>
          <div className="transparency-timeline timeline">
            {timeline.map((item, i) => (
              <div className="timeline-item" key={i}>
                <div className={`timeline-dot ${item.status}`}>
                  {item.status === 'done' ? '✓' : '→'}
                </div>
                <h4>{item.title}</h4>
                <p>{item.text}</p>
                <div className="timeline-date">{item.date}</div>
              </div>
            ))}
          </div>
        </div>
      </div>
    </section>
  );
}
