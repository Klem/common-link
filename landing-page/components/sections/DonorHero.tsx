import { useTranslations } from 'next-intl';
import { Link } from '@/i18n/navigation';

/** Fonds des pastilles d'icône, dans l'ordre de la maquette. */
const ITEM_BACKGROUNDS = [
  'rgba(78,205,196,0.12)',
  'rgba(255,107,91,0.1)',
  'rgba(50,50,125,0.08)',
];

/**
 * Hero de la page « Donateurs ».
 * Markup, classes et styles inline repris à l'identique de la maquette
 * (`p2-donateurs.html`, première `<section>`). Ne pas remplacer par du Tailwind.
 */
export function DonorHero() {
  const t = useTranslations('donors.hero');
  const items = t.raw('items') as { icon: string; title: string; text: string }[];

  return (
    <section
      style={{
        background:
          'linear-gradient(135deg,var(--soft-cream) 0%,rgba(78,205,196,0.08) 100%)',
        padding: '80px 48px',
      }}
    >
      <div
        className="max-w"
        style={{
          display: 'grid',
          gridTemplateColumns: '1fr 1fr',
          gap: '64px',
          alignItems: 'center',
        }}
      >
        <div>
          <div className="section-label">{t('label')}</div>
          <h1 style={{ fontSize: '48px', marginBottom: '20px' }}>
            {t('title')}{' '}
            <span className="italic-accent" style={{ color: 'var(--bright-teal)' }}>
              {t('titleAccent')}
            </span>
          </h1>
          <p
            style={{
              fontSize: '17px',
              color: 'var(--slate-lavender)',
              lineHeight: 1.7,
              marginBottom: '32px',
            }}
          >
            {t('text')}
          </p>
          <div
            style={{
              display: 'flex',
              flexDirection: 'column',
              gap: '16px',
              marginBottom: '40px',
            }}
          >
            {items.map((item, i) => (
              <div className="flex gap-12 items-center" key={i}>
                <div
                  style={{
                    width: '40px',
                    height: '40px',
                    borderRadius: 'var(--radius-md)',
                    background: ITEM_BACKGROUNDS[i],
                    display: 'flex',
                    alignItems: 'center',
                    justifyContent: 'center',
                  }}
                >
                  {item.icon}
                </div>
                <div>
                  <div style={{ fontWeight: 700, fontSize: '15px' }}>{item.title}</div>
                  <div style={{ fontSize: '13px', color: 'var(--slate-lavender)' }}>
                    {item.text}
                  </div>
                </div>
              </div>
            ))}
          </div>
          <Link href="/donateurs" className="btn btn-primary btn-lg">
            {t('cta')}
          </Link>
        </div>
        <div>
          {/* Donor profile mockup */}
          <div className="card" style={{ padding: '28px' }}>
            <div className="flex items-center gap-16 mb-24">
              <div className="avatar av-lg av-teal">ML</div>
              <div>
                <div style={{ fontSize: '18px', fontWeight: 800 }}>{t('profileName')}</div>
                <div style={{ fontSize: '13px', color: 'var(--slate-lavender)' }}>
                  {t('profileSince')}
                </div>
              </div>
            </div>
            <div className="grid-2">
              <div className="stat-card">
                <div
                  style={{
                    fontSize: '11px',
                    textTransform: 'uppercase',
                    letterSpacing: '1px',
                    color: 'var(--slate-lavender)',
                    marginBottom: '8px',
                  }}
                >
                  {t('statDonsLabel')}
                </div>
                <div className="stat-value" style={{ fontSize: '24px' }}>
                  {t('statDonsValue')}
                </div>
                <div
                  style={{ fontSize: '12px', color: 'var(--teal-dark)', fontWeight: 600 }}
                >
                  {t('statDonsChange')}
                </div>
              </div>
              <div className="stat-card">
                <div
                  style={{
                    fontSize: '11px',
                    textTransform: 'uppercase',
                    letterSpacing: '1px',
                    color: 'var(--slate-lavender)',
                    marginBottom: '8px',
                  }}
                >
                  {t('statAssosLabel')}
                </div>
                <div className="stat-value" style={{ fontSize: '24px' }}>
                  {t('statAssosValue')}
                </div>
                <div style={{ fontSize: '12px', color: 'var(--slate-lavender)' }}>
                  {t('statAssosText')}
                </div>
              </div>
            </div>
            <div
              style={{
                background: 'rgba(78,205,196,0.06)',
                border: '1px solid rgba(78,205,196,0.2)',
                borderRadius: 'var(--radius-lg)',
                padding: '16px',
                marginBottom: '16px',
              }}
            >
              <div
                style={{
                  fontSize: '13px',
                  fontWeight: 700,
                  color: 'var(--teal-dark)',
                  marginBottom: '4px',
                }}
              >
                {t('taxTitle')}
              </div>
              <div
                style={{
                  fontFamily: "'Nunito Sans',sans-serif",
                  fontWeight: 900,
                  fontSize: '28px',
                  color: 'var(--deep-indigo)',
                }}
              >
                {t('taxAmount')}
              </div>
              <div style={{ fontSize: '12px', color: 'var(--slate-lavender)' }}>
                {t('taxNote')}
              </div>
            </div>
            <div
              style={{
                fontSize: '13px',
                fontWeight: 700,
                color: 'var(--slate-lavender)',
                marginBottom: '10px',
              }}
            >
              {t('lastDonsLabel')}
            </div>
            <div style={{ display: 'flex', flexDirection: 'column', gap: '8px' }}>
              <div className="flex justify-between items-center">
                <div className="flex gap-8 items-center">
                  <div className="avatar av-sm av-teal" style={{ fontSize: '10px' }}>
                    SS
                  </div>
                  <div style={{ fontSize: '13px' }}>{t('don1.name')}</div>
                </div>
                <div style={{ fontWeight: 700, fontSize: '14px' }}>{t('don1.amount')}</div>
              </div>
              <div className="flex justify-between items-center">
                <div className="flex gap-8 items-center">
                  <div className="avatar av-sm av-coral" style={{ fontSize: '10px' }}>
                    LE
                  </div>
                  <div style={{ fontSize: '13px' }}>{t('don2.name')}</div>
                </div>
                <div style={{ fontWeight: 700, fontSize: '14px' }}>{t('don2.amount')}</div>
              </div>
            </div>
          </div>
        </div>
      </div>
    </section>
  );
}
