import { useTranslations } from 'next-intl';
import { Link } from '@/i18n/navigation';

/**
 * Section « Pour les associations » de la page d'accueil.
 * Markup et classes repris à l'identique de la maquette (`section.section.bg-white`
 * + `.asso-section-inner`, `CommonLink UI V2 Julian.html`, page 1). Ne pas remplacer
 * par des classes Tailwind : le CSS de la maquette est la source de vérité.
 */
export function AssoTeaser() {
  const t = useTranslations('landing.asso');
  const steps = t.raw('steps') as { num: string; title: string; text: string }[];

  return (
    <section className="section bg-white">
      <div className="max-w">
        <div className="asso-section-inner">
          <div>
            <div className="section-label">{t('label')}</div>
            <h2 style={{ fontSize: '36px', marginBottom: '16px' }}>{t('title')}</h2>
            <p
              style={{
                color: 'var(--slate-lavender)',
                lineHeight: 1.7,
                maxWidth: '460px',
                marginBottom: '8px',
              }}
            >
              {t('text')}
            </p>
            <div className="asso-steps">
              {steps.map((step) => (
                <div className="asso-step" key={step.num}>
                  <div className="asso-step-num">{step.num}</div>
                  <div>
                    <h4>{step.title}</h4>
                    <p>{step.text}</p>
                  </div>
                </div>
              ))}
            </div>
            <div className="mt-32">
              <Link href="/associations" className="btn btn-indigo btn-lg">
                {t('ctaCreate')}
              </Link>{' '}
              <Link href="/tarifs" className="btn btn-ghost btn-lg">
                {t('ctaTarifs')}
              </Link>
            </div>
          </div>
          <div className="asso-visual">
            <div className="asso-visual-header">
              <div className="dot" style={{ background: '#FF6B5B' }} />
              <div className="dot" style={{ background: '#FFB347' }} />
              <div className="dot" style={{ background: '#34C759' }} />
              <div
                style={{
                  flex: 1,
                  textAlign: 'center',
                  fontSize: '12px',
                  color: 'rgba(255,255,255,0.4)',
                }}
              >
                {t('visualLabel')}
              </div>
            </div>
            <div className="asso-visual-body">
              <div
                style={{
                  fontSize: '13px',
                  fontWeight: 700,
                  color: 'var(--slate-lavender)',
                  marginBottom: '16px',
                  textTransform: 'uppercase',
                  letterSpacing: '1px',
                }}
              >
                {t('monthLabel')}
              </div>
              <div className="asso-mini-stat">
                <span className="label">{t('statCollected')}</span>
                <span className="value" style={{ color: 'var(--bright-teal)' }}>
                  {t('statCollectedValue')}
                </span>
              </div>
              <div className="asso-mini-stat">
                <span className="label">{t('statNewDonors')}</span>
                <span className="value">{t('statNewDonorsValue')}</span>
              </div>
              <div className="asso-mini-stat">
                <span className="label">{t('statExpenses')}</span>
                <span className="value" style={{ color: 'var(--deep-indigo)' }}>
                  {t('statExpensesValue')}
                </span>
              </div>
              <div className="mt-16">
                <div
                  style={{
                    fontSize: '12px',
                    color: 'var(--slate-lavender)',
                    marginBottom: '8px',
                  }}
                >
                  {t('progressLabel')}
                </div>
                <div className="progress-bar">
                  <div
                    className="progress-fill progress-teal"
                    style={{ width: t('progressPercent') }}
                  />
                </div>
                <div
                  style={{
                    display: 'flex',
                    justifyContent: 'space-between',
                    fontSize: '12px',
                    marginTop: '4px',
                  }}
                >
                  <span className="text-muted">{t('progressAmount')}</span>
                  <span className="fw-bold" style={{ color: 'var(--teal-dark)' }}>
                    {t('progressPercent')}
                  </span>
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>
    </section>
  );
}
