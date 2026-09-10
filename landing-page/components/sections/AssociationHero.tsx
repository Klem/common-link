import { useTranslations } from 'next-intl';
import { Link } from '@/i18n/navigation';
import { APP_URL } from '@/lib/constants';

/**
 * Hero de la page « Associations ».
 * Markup, classes et styles inline repris à l'identique de la maquette
 * (`CommonLink UI V2 Julian.html`, page 3 — `p3-associations.html`).
 * Ne pas remplacer par des classes Tailwind.
 */
export function AssociationHero() {
  const t = useTranslations('associations.hero');

  return (
    <section
      style={{
        background: 'var(--deep-indigo)',
        color: 'var(--white)',
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
          <div className="section-label" style={{ color: 'var(--bright-teal)' }}>
            {t('label')}
          </div>
          <h1
            style={{
              fontSize: '48px',
              color: 'var(--white)',
              marginBottom: '20px',
            }}
          >
            {t('title')}{' '}
            <span className="italic-accent" style={{ color: 'var(--soft-amber)' }}>
              {t('titleAccent')}
            </span>
          </h1>
          <p
            style={{
              fontSize: '17px',
              color: 'rgba(255,255,255,0.7)',
              lineHeight: 1.7,
              marginBottom: '36px',
            }}
          >
            {t('text')}
          </p>
          <div className="flex gap-12" style={{ flexWrap: 'wrap' }}>
            <a href={APP_URL} className="btn btn-primary btn-lg">
              {t('ctaCreate')}
            </a>
            <Link href="/tarifs" className="btn btn-outline-white btn-lg">
              {t('ctaTarifs')}
            </Link>
          </div>
          <div
            style={{
              display: 'flex',
              gap: '32px',
              marginTop: '48px',
              paddingTop: '32px',
              borderTop: '1px solid rgba(255,255,255,0.08)',
            }}
          >
            <div>
              <div
                style={{
                  fontFamily: 'var(--font-nunito-sans),sans-serif',
                  fontWeight: 900,
                  fontSize: '32px',
                  color: 'var(--bright-teal)',
                }}
              >
                {t('stat1.num')}
              </div>
              <div style={{ fontSize: '13px', color: 'rgba(255,255,255,0.5)' }}>
                {t('stat1.text')}
              </div>
            </div>
            <div>
              <div
                style={{
                  fontFamily: 'var(--font-nunito-sans),sans-serif',
                  fontWeight: 900,
                  fontSize: '32px',
                  color: 'var(--soft-amber)',
                }}
              >
                {t('stat2.num')}
              </div>
              <div style={{ fontSize: '13px', color: 'rgba(255,255,255,0.5)' }}>
                {t('stat2.text')}
              </div>
            </div>
            <div>
              <div
                style={{
                  fontFamily: 'var(--font-nunito-sans),sans-serif',
                  fontWeight: 900,
                  fontSize: '32px',
                  color: 'var(--warm-coral)',
                }}
              >
                {t('stat3.num')}
              </div>
              <div style={{ fontSize: '13px', color: 'rgba(255,255,255,0.5)' }}>
                {t('stat3.text')}
              </div>
            </div>
          </div>
        </div>
        <div>
          {/* Mock dashboard preview */}
          <div
            style={{
              background: 'rgba(255,255,255,0.06)',
              border: '1px solid rgba(255,255,255,0.1)',
              borderRadius: 'var(--radius-xl)',
              padding: '24px',
            }}
          >
            <div
              style={{
                fontSize: '13px',
                fontWeight: 700,
                color: 'rgba(255,255,255,0.4)',
                textTransform: 'uppercase',
                letterSpacing: '1px',
                marginBottom: '16px',
              }}
            >
              {t('visualLabel')}
            </div>
            <div
              style={{
                display: 'grid',
                gridTemplateColumns: '1fr 1fr',
                gap: '12px',
                marginBottom: '20px',
              }}
            >
              <div
                style={{
                  background: 'rgba(78,205,196,0.12)',
                  borderRadius: 'var(--radius-lg)',
                  padding: '16px',
                }}
              >
                <div
                  style={{
                    fontSize: '22px',
                    fontFamily: 'var(--font-nunito-sans),sans-serif',
                    fontWeight: 900,
                    color: 'var(--bright-teal)',
                  }}
                >
                  {t('statCollected.value')}
                </div>
                <div style={{ fontSize: '12px', color: 'rgba(255,255,255,0.5)' }}>
                  {t('statCollected.label')}
                </div>
              </div>
              <div
                style={{
                  background: 'rgba(255,179,71,0.1)',
                  borderRadius: 'var(--radius-lg)',
                  padding: '16px',
                }}
              >
                <div
                  style={{
                    fontSize: '22px',
                    fontFamily: 'var(--font-nunito-sans),sans-serif',
                    fontWeight: 900,
                    color: 'var(--soft-amber)',
                  }}
                >
                  {t('statDonors.value')}
                </div>
                <div style={{ fontSize: '12px', color: 'rgba(255,255,255,0.5)' }}>
                  {t('statDonors.label')}
                </div>
              </div>
            </div>
            <div
              style={{
                background: 'rgba(255,255,255,0.04)',
                borderRadius: 'var(--radius-lg)',
                padding: '16px',
                marginBottom: '16px',
              }}
            >
              <div
                style={{
                  fontSize: '12px',
                  color: 'rgba(255,255,255,0.4)',
                  marginBottom: '8px',
                }}
              >
                {t('progressLabel')}
              </div>
              <div className="progress-bar">
                <div className="progress-fill progress-teal" style={{ width: '71%' }} />
              </div>
              <div
                style={{
                  display: 'flex',
                  justifyContent: 'space-between',
                  marginTop: '6px',
                  fontSize: '12px',
                }}
              >
                <span style={{ color: 'rgba(255,255,255,0.5)' }}>{t('progressAmount')}</span>
                <span style={{ color: 'var(--bright-teal)', fontWeight: 700 }}>
                  {t('progressPercent')}
                </span>
              </div>
            </div>
            <div
              style={{
                fontSize: '12px',
                color: 'rgba(255,255,255,0.4)',
                marginBottom: '8px',
              }}
            >
              {t('lastExpensesLabel')}
            </div>
            <div style={{ display: 'flex', flexDirection: 'column', gap: '6px' }}>
              <div
                style={{
                  display: 'flex',
                  justifyContent: 'space-between',
                  fontSize: '13px',
                }}
              >
                <span style={{ color: 'rgba(255,255,255,0.7)' }}>{t('expense1.label')}</span>
                <span style={{ color: 'var(--bright-teal)', fontWeight: 700 }}>
                  {t('expense1.amount')}
                </span>
              </div>
              <div
                style={{
                  display: 'flex',
                  justifyContent: 'space-between',
                  fontSize: '13px',
                }}
              >
                <span style={{ color: 'rgba(255,255,255,0.7)' }}>{t('expense2.label')}</span>
                <span style={{ color: 'var(--bright-teal)', fontWeight: 700 }}>
                  {t('expense2.amount')}
                </span>
              </div>
            </div>
          </div>
        </div>
      </div>
    </section>
  );
}
