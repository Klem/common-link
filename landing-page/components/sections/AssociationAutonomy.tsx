import { useTranslations } from 'next-intl';

/**
 * Associations « Plus d'autonomie » section.
 */
export function AssociationAutonomy() {
  const t = useTranslations('associations.autonomy');

  return (
    <section id="associations-autonomie" style={{ background: "#32327D", color: "#FFFFFF", padding: "96px 48px" }}>
      <div style={{ maxWidth: "1240px", margin: "0 auto", display: "flex", flexDirection: "column", gap: "40px" }}>
        <div style={{ display: "flex", flexDirection: "column", gap: "12px", maxWidth: "720px" }}>
          <span style={{ fontSize: "13px", fontWeight: "700", letterSpacing: "1.5px", textTransform: "uppercase", color: "#4ECDC4" }}>{t("plusAutonomie")}</span>
          <h2 style={{ margin: "0", fontFamily: "'Nunito Sans', sans-serif", fontWeight: "900", fontSize: "40px", lineHeight: "1.15" }}>{t("desDonsQuiVous")}</h2>
          <p style={{ margin: "0", fontSize: "17px", lineHeight: "1.7", color: "rgba(255,255,255,0.84)" }}>{t("desDonsPrivesQui")}</p>
        </div>
        <div style={{ display: "grid", gridTemplateColumns: "repeat(auto-fit, minmax(min(100%, 260px), 1fr))", gap: "20px" }}>
          <div style={{ background: "rgba(255,255,255,0.08)", borderRadius: "20px", padding: "24px", display: "flex", flexDirection: "column", gap: "10px" }}>
            <span style={{ fontFamily: "'Nunito Sans', sans-serif", fontWeight: "900", fontSize: "18px", color: "#4ECDC4" }}>{t("acquisDesReception")}</span>
            <span style={{ fontSize: "15px", lineHeight: "1.6", color: "rgba(255,255,255,0.86)" }}>{t("lesDonsSontVotre")}</span>
          </div>
          <div style={{ background: "rgba(255,255,255,0.08)", borderRadius: "20px", padding: "24px", display: "flex", flexDirection: "column", gap: "10px" }}>
            <span style={{ fontFamily: "'Nunito Sans', sans-serif", fontWeight: "900", fontSize: "18px", color: "#FFB347" }}>{t("sansSeuilAtteindre")}</span>
            <span style={{ fontSize: "15px", lineHeight: "1.6", color: "rgba(255,255,255,0.86)" }}>{t("vousDisposezDesDons")}</span>
          </div>
          <div style={{ background: "rgba(255,255,255,0.08)", borderRadius: "20px", padding: "24px", display: "flex", flexDirection: "column", gap: "10px" }}>
            <span style={{ fontFamily: "'Nunito Sans', sans-serif", fontWeight: "900", fontSize: "18px", color: "#FF8A7D" }}>{t("surVotreCompte")}</span>
            <span style={{ fontSize: "15px", lineHeight: "1.6", color: "rgba(255,255,255,0.86)" }}>{t("lesFondsVontSur")}</span>
          </div>
          <div style={{ background: "rgba(255,255,255,0.08)", borderRadius: "20px", padding: "24px", display: "flex", flexDirection: "column", gap: "10px" }}>
            <span style={{ fontFamily: "'Nunito Sans', sans-serif", fontWeight: "900", fontSize: "18px", color: "#C9C5F0" }}>{t("bientotLeDonMensuel")}</span>
            <span style={{ fontSize: "15px", lineHeight: "1.6", color: "rgba(255,255,255,0.86)" }}>{t("desDonsReguliersPour")}</span>
          </div>
        </div>
      </div>
    </section>
  );
}
