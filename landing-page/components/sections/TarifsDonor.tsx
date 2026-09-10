import { useTranslations } from 'next-intl';

/**
 * Section « Pour les donateurs » de la page Tarifs (ancre `#tarif-don`).
 * Markup, classes et styles inline repris à l'identique de la maquette
 * (`section.section.bg-white#tarif-don`, `p7-tarifs.html`).
 * Ne pas remplacer par du Tailwind.
 */
export function TarifsDonor() {
  const t = useTranslations('tarifs.donor');

  return (
    <section className="section bg-white" id="tarif-don">
      <div className="max-w" style={{ maxWidth: '900px' }}>
        <div className="section-label">{t('label')}</div>
        <h2 className="tarif-h2" style={{ marginTop: '8px' }}>
          {t('title')}
        </h2>
        <p>{t('text1')}</p>
        <p>{t('text2')}</p>
        <div className="tarif-claim-box">
          <div className="tarif-claim">
            {t('claimPre')}
            <br />
            <strong>{t('claimStrong')}</strong>
          </div>
        </div>
      </div>
    </section>
  );
}
