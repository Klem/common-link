import { useTranslations } from 'next-intl';

/** Fonds des pastilles numérotées, dans l'ordre de la maquette. */
const CIRCLE_BACKGROUNDS = [
  'var(--bright-teal)',
  'var(--warm-coral)',
  'var(--deep-indigo)',
];

/**
 * Section « Trois étapes, deux minutes » de la page Donateurs.
 * Markup, classes et styles inline repris à l'identique de la maquette
 * (`p2-donateurs.html`, dernière `<section>`). Ne pas remplacer par du Tailwind.
 */
export function DonorSteps() {
  const t = useTranslations('donors.steps');
  const items = t.raw('items') as { num: string; title: string; text: string }[];

  return (
    <section className="section bg-white">
      <div className="max-w">
        <div style={{ textAlign: 'center', marginBottom: '48px' }}>
          <div className="section-label">{t('label')}</div>
          <h2 style={{ fontSize: '36px' }}>{t('title')}</h2>
        </div>
        <div className="grid-3">
          {items.map((item, i) => (
            <div style={{ textAlign: 'center', padding: '32px 20px' }} key={item.num}>
              <div
                style={{
                  width: '72px',
                  height: '72px',
                  background: CIRCLE_BACKGROUNDS[i],
                  borderRadius: '50%',
                  display: 'flex',
                  alignItems: 'center',
                  justifyContent: 'center',
                  fontSize: '28px',
                  color: 'var(--white)',
                  margin: '0 auto 20px',
                  fontFamily: "'Nunito Sans',sans-serif",
                  fontWeight: 900,
                }}
              >
                {item.num}
              </div>
              <h3 style={{ marginBottom: '8px' }}>{item.title}</h3>
              <p
                style={{
                  fontSize: '14px',
                  color: 'var(--slate-lavender)',
                  lineHeight: 1.7,
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
