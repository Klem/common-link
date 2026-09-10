import { useTranslations } from 'next-intl';

/**
 * Section « Nos partenaires » de la page d'accueil.
 * Markup et classes repris à l'identique de la maquette (`section.section.bg-cream`
 * + `.partners`, `CommonLink UI V2 Julian.html`, page 1). Les logos sont des balises
 * `<img>` simples (et non `next/image`) pour conserver le markup de la maquette.
 * Ne pas remplacer par des classes Tailwind : le CSS de la maquette est la source de vérité.
 */
export function Partners() {
  const t = useTranslations('landing.partners');
  const items = t.raw('items') as { name: string; role: string; text: string; href: string }[];
  const logos = ['/partners/ekads.png', '/partners/samson-conseil.png'];

  return (
    <section className="section bg-cream">
      <div className="max-w" style={{ maxWidth: '940px', textAlign: 'center' }}>
        <div className="section-label">{t('label')}</div>
        <h2 style={{ fontSize: '32px', marginBottom: '10px' }}>{t('title')}</h2>
        <p style={{ color: 'var(--slate-lavender)', maxWidth: '560px', margin: '0 auto 36px' }}>
          {t('subtitle')}
        </p>
        <div className="partners">
          {items.map((item, i) => (
            <a
              className="partner-card"
              href={item.href}
              target="_blank"
              rel="noopener"
              key={item.href}
            >
              <div className="partner-logo">
                {/* eslint-disable-next-line @next/next/no-img-element */}
                <img src={logos[i]} alt={item.name} />
              </div>
              <div className="partner-role">{item.role}</div>
              <p>{item.text}</p>
            </a>
          ))}
        </div>
        <p className="partners-note">{t('note')}</p>
      </div>
    </section>
  );
}
