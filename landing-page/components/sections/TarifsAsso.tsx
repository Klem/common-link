import { useTranslations } from 'next-intl';

/**
 * Section « Pour les associations » de la page Tarifs (ancre `#tarif-asso`).
 * Markup, classes et styles inline repris à l'identique de la maquette
 * (`section.section.bg-cream#tarif-asso`, `p7-tarifs.html`).
 * Le « ✕ » des `.tarif-item` vient du CSS (`::before`) : ne pas l'écrire ici.
 * Ne pas remplacer par du Tailwind.
 */
export function TarifsAsso() {
  const t = useTranslations('tarifs.asso');
  const notPayItems = t.raw('notPayItems') as string[];

  return (
    <section className="section bg-cream" id="tarif-asso">
      <div className="max-w" style={{ maxWidth: '960px' }}>
        <div className="section-label">{t('label')}</div>
        <h2 className="tarif-h2" style={{ marginTop: '8px' }}>
          {t('title')}
        </h2>

        <div className="tarif-flow">
          <div className="tarif-flow-title">{t('flowTitle')}</div>
          <div className="tarif-claim">
            {t('flowClaimPre')}
            <br />
            <strong>{t('flowClaimStrong')}</strong>
          </div>
          <p className="tarif-flow-note">{t('flowNote')}</p>
        </div>

        <h3 style={{ fontSize: '22px', margin: '48px 0 18px' }}>{t('notPayTitle')}</h3>
        <div className="tarif-grid">
          {notPayItems.map((item, i) => (
            <div className="tarif-item" key={i}>
              {item}
            </div>
          ))}
        </div>

        <div className="tarif-duo">
          <div className="tarif-mini">
            <div className="tarif-mini-icon">🔓</div>
            <h3>{t('soloTitle')}</h3>
            <p>{t('soloText')}</p>
          </div>
          <div className="tarif-mini">
            <div className="tarif-mini-icon">🧾</div>
            <h3>{t('billedTitle')}</h3>
            <p>{t('billedText')}</p>
          </div>
        </div>

        <p className="tarif-foot">{t('footNote')}</p>
      </div>
    </section>
  );
}
