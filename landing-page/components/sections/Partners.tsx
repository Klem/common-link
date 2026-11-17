import { useTranslations } from 'next-intl';

/**
 * Home « Nos partenaires » section.
 */
export function Partners() {
  const t = useTranslations('landing.partners');

  return (
    <section style={{ background: "#FDF8F0", padding: "96px 48px" }}>
      <div style={{ maxWidth: "1240px", margin: "0 auto", display: "flex", flexDirection: "column", gap: "32px" }}>
        <div style={{ display: "flex", flexDirection: "column", gap: "12px", maxWidth: "720px" }}>
          <span style={{ fontSize: "13px", fontWeight: "700", letterSpacing: "1.5px", textTransform: "uppercase", color: "#1E7F78" }}>{t("nosPartenaires")}</span>
          <h2 style={{ margin: "0", fontFamily: "'Nunito Sans', sans-serif", fontWeight: "900", fontSize: "40px" }}>{t("ilsConnaissentLeMonde")}</h2>
          <p style={{ margin: "0", fontSize: "17px", lineHeight: "1.7", color: "#4A4A66" }}>{t("nousAvanconsAvecDes")}</p>
        </div>
        <div style={{ display: "grid", gridTemplateColumns: "repeat(auto-fit, minmax(min(100%, 420px), 1fr))", gap: "24px" }}>
          <a href="https://www.ekads.fr/" style={{ background: "#FFFFFF", border: "1px solid #E6E4F4", borderRadius: "20px", padding: "28px", display: "flex", flexDirection: "column", gap: "8px", textDecoration: "none", color: "#171744" }}>
            <strong style={{ fontFamily: "'Nunito Sans', sans-serif", fontSize: "20px" }}>{t("ekads")}</strong>
            <span style={{ fontSize: "13px", fontWeight: "700", color: "#1E7F78" }}>{t("visibiliteEnLigne")}</span>
            <span style={{ fontSize: "15px", lineHeight: "1.6", color: "#4A4A66" }}>{t("agenceDedieeAuxAssociations")}</span>
          </a>
          <a href="https://www.samson-conseil.org/" style={{ background: "#FFFFFF", border: "1px solid #E6E4F4", borderRadius: "20px", padding: "28px", display: "flex", flexDirection: "column", gap: "8px", textDecoration: "none", color: "#171744" }}>
            <strong style={{ fontFamily: "'Nunito Sans', sans-serif", fontSize: "20px" }}>{t("samsonConseilEtFormation")}</strong>
            <span style={{ fontSize: "13px", fontWeight: "700", color: "#1E7F78" }}>{t("structurationEtGouvernance")}</span>
            <span style={{ fontSize: "15px", lineHeight: "1.6", color: "#4A4A66" }}>{t("cabinetDeConseilEt")}</span>
          </a>
        </div>
        <p style={{ margin: "0", fontSize: "14px", color: "#4A4A66" }}>{t("nosPartenairesInterviennentNi")}</p>
      </div>
    </section>
  );
}
