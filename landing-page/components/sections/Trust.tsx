import { useTranslations } from 'next-intl';

/**
 * Section « Confiance & Sécurité » de la page d'accueil.
 * Markup et classes repris à l'identique de la maquette (`section.section.bg-cream`
 * + `.trust-grid`, `CommonLink UI V2 Julian.html`, page 1). Ne pas remplacer par des
 * classes Tailwind : le CSS de la maquette est la source de vérité.
 */
export function Trust() {
  const t = useTranslations('landing.trust');
  const items = t.raw('items') as { icon: string; title: string; text: string }[];

  return (
    <section className="section bg-cream">
      <div className="max-w" style={{ textAlign: 'center' }}>
        <div className="section-label">{t('label')}</div>
        <h2 style={{ fontSize: '36px', marginBottom: '48px' }}>{t('title')}</h2>
        <div className="trust-grid">
          {items.map((item, i) => (
            <div className="trust-card" key={i}>
              <div className="trust-icon">{item.icon}</div>
              <h3>{item.title}</h3>
              <p>{item.text}</p>
            </div>
          ))}
        </div>
      </div>
    </section>
  );
}
