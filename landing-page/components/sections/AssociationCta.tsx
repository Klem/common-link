import { useTranslations } from 'next-intl';
import { Link } from '@/i18n/navigation';
import { APP_URL } from '@/lib/constants';

/**
 * Associations closing call to action.
 */
export function AssociationCta() {
  const t = useTranslations('associations.cta');

  return (
    <section style={{ background: "#FDF8F0", padding: "80px 48px" }}>
      <div style={{ maxWidth: "1240px", margin: "0 auto", display: "flex", flexDirection: "column", alignItems: "center", gap: "22px", textAlign: "center" }}>
        <h2 style={{ margin: "0", fontFamily: "'Nunito Sans', sans-serif", fontWeight: "900", fontSize: "36px", lineHeight: "1.2" }}>{t("votreProchaineCollectePeut")}</h2>
        <div style={{ display: "flex", flexWrap: "wrap", justifyContent: "center", gap: "14px" }}>
          <a href={APP_URL} style={{ display: "inline-flex", alignItems: "center", minHeight: "52px", padding: "0 28px", borderRadius: "9999px", background: "#32327D", color: "#FFFFFF", fontWeight: "700", fontSize: "16px", textDecoration: "none" }}>{t("creerMonCompteAssociation")}</a>
          <Link href="/tarifs" style={{ display: "inline-flex", alignItems: "center", minHeight: "52px", padding: "0 28px", borderRadius: "9999px", border: "1.5px solid #32327D", color: "#32327D", fontWeight: "600", fontSize: "16px", textDecoration: "none" }}>{t("voirLesTarifs")}</Link>
        </div>
      </div>
    </section>
  );
}
