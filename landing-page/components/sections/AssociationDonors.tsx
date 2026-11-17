import { useTranslations } from 'next-intl';

/**
 * Associations « De nouveaux donateurs » section.
 */
export function AssociationDonors() {
  const t = useTranslations('associations.donors');

  return (
    <section id="associations-donateurs" style={{ background: "#FFFFFF", padding: "96px 48px" }}>
      <div style={{ maxWidth: "1240px", margin: "0 auto", display: "grid", gridTemplateColumns: "repeat(auto-fit, minmax(min(100%, 460px), 1fr))", gap: "56px", alignItems: "center" }}>
        <div style={{ display: "flex", flexDirection: "column", gap: "18px" }}>
          <span style={{ fontSize: "13px", fontWeight: "700", letterSpacing: "1.5px", textTransform: "uppercase", color: "#A86A00" }}>{t("deNouveauxDonateurs")}</span>
          <h2 style={{ margin: "0", fontFamily: "'Nunito Sans', sans-serif", fontWeight: "900", fontSize: "40px", lineHeight: "1.15" }}>{t("touchezCeuxQuiHesitent")}</h2>
          <div style={{ display: "flex", alignItems: "center", gap: "20px", padding: "22px 24px", borderRadius: "20px", background: "rgba(255,179,71,0.14)" }}>
            <span style={{ fontFamily: "'Nunito Sans', sans-serif", fontWeight: "900", fontSize: "56px", lineHeight: "1", color: "#A86A00", whiteSpace: "nowrap" }}>
              65 %
            </span>
            <span style={{ fontSize: "16px", lineHeight: "1.5" }}>{t("lePremierFreinAu")}</span>
          </div>
          <span style={{ fontSize: "12px", color: "#4A4A66" }}>{t("sourceBarometreDeLa")}</span>
          <p style={{ margin: "0", fontSize: "17px", lineHeight: "1.7", color: "#4A4A66" }}>{t("surCommonlinkVosDonateurs")}</p>
        </div>
        <div style={{ background: "#FFFFFF", border: "1px solid #ECEAF5", borderRadius: "28px", boxShadow: "0 24px 60px rgba(23,23,68,0.12)", overflow: "hidden" }}>
          <div style={{ padding: "20px 26px", borderBottom: "1px solid #ECEAF5", display: "flex", alignItems: "center", justifyContent: "space-between", gap: "12px" }}>
            <strong style={{ fontFamily: "'Nunito Sans', sans-serif", fontWeight: "800", fontSize: "17px" }}>{t("ceQueVoitVotre")}</strong>
            <span style={{ fontSize: "12px", fontWeight: "600", color: "#4A4A66", background: "#E6E4F4", borderRadius: "9999px", padding: "4px 10px" }}>{t("exemple")}</span>
          </div>
          <div style={{ padding: "24px 26px", display: "flex", alignItems: "center", gap: "24px", flexWrap: "wrap" }}>
            <svg width="150" height="150" viewBox="0 0 170 170" role="img" aria-label={t("unDonDeReparti")} style={{ flex: "none" }}>
              <circle cx="85" cy="85" r="64" fill="none" stroke="#ECEAF5" strokeWidth="30" />
              <circle cx="85" cy="85" r="64" fill="none" stroke="#4ECDC4" strokeWidth="30" strokeDasharray="181 402" transform="rotate(-90 85 85)" />
              <circle cx="85" cy="85" r="64" fill="none" stroke="#FF6B5B" strokeWidth="30" strokeDasharray="141 402" strokeDashoffset="-181" transform="rotate(-90 85 85)" />
              <circle cx="85" cy="85" r="64" fill="none" stroke="#FFB347" strokeWidth="30" strokeDasharray="80 402" strokeDashoffset="-322" transform="rotate(-90 85 85)" />
              <text x="85" y="84" textAnchor="middle" fontFamily="Nunito Sans, sans-serif" fontWeight="900" fontSize="22" fill="#1E9E95">
                11,77 €
              </text>
              <text x="85" y="104" textAnchor="middle" fontFamily="Inter, sans-serif" fontSize="11" fill="#4A4A66">{t("toutUtilise")}</text>
            </svg>
            <div style={{ flex: "1", minWidth: "200px", display: "flex", flexDirection: "column", gap: "10px", fontSize: "14px" }}>
              <div style={{ display: "flex", alignItems: "center", gap: "10px" }}>
                <span style={{ width: "10px", height: "10px", flex: "none", borderRadius: "50%", background: "#4ECDC4" }} />
                <span style={{ flex: "1" }}>{t("achatDeDenrees")}</span>
                <strong style={{ whiteSpace: "nowrap" }}>
                  5,32 €
                </strong>
              </div>
              <div style={{ display: "flex", alignItems: "center", gap: "10px" }}>
                <span style={{ width: "10px", height: "10px", flex: "none", borderRadius: "50%", background: "#FF6B5B" }} />
                <span style={{ flex: "1" }}>{t("locationCamion")}</span>
                <strong style={{ whiteSpace: "nowrap" }}>
                  4,15 €
                </strong>
              </div>
              <div style={{ display: "flex", alignItems: "center", gap: "10px" }}>
                <span style={{ width: "10px", height: "10px", flex: "none", borderRadius: "50%", background: "#FFB347" }} />
                <span style={{ flex: "1" }}>{t("remboursementDeFrais")}</span>
                <strong style={{ whiteSpace: "nowrap" }}>
                  2,30 €
                </strong>
              </div>
            </div>
          </div>
        </div>
      </div>
    </section>
  );
}
