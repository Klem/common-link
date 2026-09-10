import { useTranslations } from 'next-intl';
import { APP_URL } from '@/lib/constants';

/**
 * Hero de la page d'accueil.
 * Markup et classes repris à l'identique de la maquette (`section.hero-section`,
 * `CommonLink UI V2 Julian.html`, page 1).
 */
export function Hero() {
  const t = useTranslations('landing.hero');
  const trust = t.raw('trust') as { strong: string; text: string }[];

  return (
    <section className="hero-section">
      <div
        style={{
          position: 'absolute',
          inset: 0,
          background:
            'radial-gradient(ellipse at 70% 50%, rgba(78,205,196,0.12) 0%, transparent 60%)',
          pointerEvents: 'none',
        }}
      />
      <div className="hero-inner">
        <div className="hero-content">
          <h1 className="hero-title">
            {t('title')} <span className="accent">{t('titleAccent')}</span>
          </h1>
          <p className="hero-sub">{t('subtitle')}</p>
          <div className="hero-ctas">
            <a href={APP_URL} className="btn btn-primary btn-lg">
              ♥ {t('ctaDonate')}
            </a>
            <a href={APP_URL} className="btn btn-outline-white btn-lg">
              {t('ctaCreate')} →
            </a>
          </div>
          <div className="hero-trust">
            {trust.map((item, i) => (
              <div className="hero-trust-item" key={i}>
                ✓ <strong>{item.strong}</strong> {item.text}
              </div>
            ))}
          </div>
        </div>
        <div className="hero-visual">
          <div className="hero-blob" />
          <div className="hero-photo-frame">
            <div style={{ fontSize: '80px', opacity: 0.3 }}>🤝</div>
            <div
              style={{
                position: 'absolute',
                bottom: 0,
                left: 0,
                right: 0,
                height: '120px',
                background:
                  'linear-gradient(to top, rgba(50,50,125,0.8), transparent)',
              }}
            />
            <div
              style={{ position: 'absolute', bottom: '20px', left: '20px', right: '20px' }}
            >
              <div
                style={{
                  fontSize: '12px',
                  color: 'rgba(255,255,255,0.7)',
                  fontWeight: 600,
                }}
              >
                {t('visualAssoc')}
              </div>
              <div
                style={{
                  fontSize: '14px',
                  color: 'var(--white)',
                  fontWeight: 700,
                }}
              >
                {t('visualProject')}
              </div>
            </div>
          </div>
          <div className="hero-float-card hero-float-1">
            <div className="amount">{t('floatAmount')}</div>
            <div className="label">💚 {t('floatLabel')}</div>
            <div style={{ display: 'flex', gap: '4px', marginTop: '6px' }}>
              <div
                style={{
                  width: '6px',
                  height: '6px',
                  borderRadius: '50%',
                  background: 'var(--bright-teal)',
                }}
              />
              <div
                style={{
                  fontSize: '11px',
                  color: 'var(--teal-dark)',
                  fontWeight: 600,
                }}
              >
                {t('floatRealtime')}
              </div>
            </div>
          </div>
          <div className="hero-float-card hero-float-2">
            <div
              style={{
                fontSize: '11px',
                color: 'var(--slate-lavender)',
                fontWeight: 600,
                marginBottom: '4px',
              }}
            >
              {t('floatLastExpenseLabel')}
            </div>
            <div
              style={{ fontSize: '13px', fontWeight: 700, color: 'var(--ink-navy)' }}
            >
              {t('floatLastExpense')}
            </div>
            <div
              style={{ fontSize: '11px', color: 'var(--teal-dark)', marginTop: '2px' }}
            >
              {t('floatPublished')}
            </div>
          </div>
        </div>
      </div>
    </section>
  );
}
