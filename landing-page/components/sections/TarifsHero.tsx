import { useTranslations } from 'next-intl';

/**
 * Hero de la page Tarifs (« Qui paie quoi ») : deux cartes `.tarif-side`
 * pointant vers les ancres `#tarif-don` et `#tarif-asso`.
 * Markup, classes et styles inline repris à l'identique de la maquette
 * (`div.tarif-hero`, `p7-tarifs.html`). Ne pas remplacer par du Tailwind.
 */
export function TarifsHero() {
  const t = useTranslations('tarifs.hero');

  return (
    <div className="tarif-hero">
      <div className="max-w" style={{ maxWidth: '900px', textAlign: 'center' }}>
        <div className="section-label">{t('label')}</div>
        <h1 style={{ fontSize: '46px', margin: '10px 0 14px' }}>{t('title')}</h1>
        <p className="tarif-hero-sub" style={{ margin: '0 auto 34px' }}>
          {t('subtitle')}
        </p>
        <div className="tarif-split">
          <a className="tarif-side don" href="#tarif-don">
            <div className="tarif-side-who">{t('donWho')}</div>
            <div className="tarif-side-num">
              {t('donNum')}
              <span>{t('donUnit')}</span>
            </div>
            <div className="tarif-side-line">{t('donLine')}</div>
          </a>
          <a className="tarif-side asso" href="#tarif-asso">
            <div className="tarif-side-who">{t('assoWho')}</div>
            <div className="tarif-side-num">
              {t('assoNum')}
              <span>{t('assoUnit')}</span>
            </div>
            <div className="tarif-side-line">{t('assoLine')}</div>
          </a>
        </div>
      </div>
    </div>
  );
}
