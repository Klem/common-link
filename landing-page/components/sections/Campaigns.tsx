import { useTranslations } from 'next-intl';
import { APP_URL } from '@/lib/constants';

/**
 * Section « Campagnes en cours » de la page d'accueil.
 * Markup et classes repris à l'identique de la maquette (`section.section.bg-cream`
 * + `.empty-state`, `CommonLink UI V2 Julian.html`, page 1). Ne pas remplacer par des
 * classes Tailwind : le CSS de la maquette est la source de vérité.
 */
export function Campaigns() {
  const t = useTranslations('landing.campaigns');

  return (
    <section className="section bg-cream">
      <div className="max-w">
        <div className="flex justify-between items-center mb-32">
          <div>
            <div className="section-label">{t('label')}</div>
            <h2 style={{ fontSize: '36px' }}>{t('title')}</h2>
          </div>
          <a href={APP_URL} className="btn btn-secondary">
            {t('ctaAll')} →
          </a>
        </div>
        <div className="empty-state">
          <div className="empty-emoji">🌱</div>
          <h3>{t('emptyTitle')}</h3>
          <p>{t('emptyText')}</p>
        </div>
      </div>
    </section>
  );
}
