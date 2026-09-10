import { useTranslations } from 'next-intl';

/**
 * Section « Fonctionnalités » de la page Associations.
 * Markup, classes et styles inline repris à l'identique de la maquette
 * (`CommonLink UI V2 Julian.html`, page 3 — `p3-associations.html`).
 * Le hover des cartes vient de `.card:hover` : ne pas remplacer par du Tailwind.
 */
export function AssociationFeatures() {
  const t = useTranslations('associations.features');
  const items = t.raw('items') as { icon: string; title: string; text: string }[];

  return (
    <section className="section bg-cream">
      <div className="max-w">
        <div style={{ textAlign: 'center', marginBottom: '48px' }}>
          <div className="section-label">{t('label')}</div>
          <h2 style={{ fontSize: '36px' }}>{t('title')}</h2>
        </div>
        <div className="grid-3">
          {items.map((item, i) => (
            <div className="card" style={{ padding: '28px' }} key={i}>
              <div style={{ fontSize: '28px', marginBottom: '16px' }}>{item.icon}</div>
              <h3 style={{ fontSize: '18px', marginBottom: '8px' }}>{item.title}</h3>
              <p
                style={{
                  fontSize: '14px',
                  color: 'var(--slate-lavender)',
                  lineHeight: 1.6,
                }}
              >
                {item.text}
              </p>
            </div>
          ))}
        </div>
      </div>
    </section>
  );
}
