import { useTranslations } from 'next-intl';

/**
 * Section « Pourquoi CommonLink » de la page d'accueil.
 * Markup et classes repris à l'identique de la maquette (`section.section.bg-white`
 * + `.why-grid`, `CommonLink UI V2 Julian.html`, page 1). Ne pas remplacer par des
 * classes Tailwind : le CSS de la maquette est la source de vérité.
 */
export function Why() {
  const t = useTranslations('landing.why');
  const items = t.raw('items') as { icon: string; title: string; text: string }[];
  const iconClasses = ['stat-icon-teal', 'stat-icon-coral', 'stat-icon-indigo'];

  return (
    <section className="section bg-white">
      <div className="max-w">
        <div style={{ textAlign: 'center' }}>
          <div className="section-label">{t('label')}</div>
          <h2 style={{ fontSize: '40px', maxWidth: '540px', margin: '0 auto 16px' }}>
            {t('title')} <span style={{ color: 'var(--bright-teal)' }}>{t('titleAccent')}</span>
          </h2>
          <p style={{ color: 'var(--slate-lavender)', maxWidth: '520px', margin: '0 auto' }}>
            {t('subtitle')}
          </p>
        </div>
        <div className="why-grid">
          {items.map((item, i) => (
            <div className="why-card" key={i}>
              <div className={`why-icon ${iconClasses[i]}`}>{item.icon}</div>
              <h3>{item.title}</h3>
              <p>{item.text}</p>
            </div>
          ))}
        </div>
      </div>
    </section>
  );
}
