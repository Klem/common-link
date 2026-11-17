import { useTranslations } from 'next-intl';

/**
 * Donors « Comment ça marche » section: three steps.
 */
export function DonorSteps() {
  const t = useTranslations('donors.steps');

  return (
    <section style={{ background: "#FFFFFF", padding: "96px 48px" }}>
      <div style={{ maxWidth: "1240px", margin: "0 auto", display: "flex", flexDirection: "column", gap: "48px" }}>
        <div style={{ display: "flex", flexDirection: "column", gap: "12px", alignItems: "center", textAlign: "center" }}>
          <span style={{ fontSize: "13px", fontWeight: "700", letterSpacing: "1.5px", textTransform: "uppercase", color: "#1E7F78" }}>{t("commentCaMarche")}</span>
          <h2 style={{ margin: "0", fontFamily: "'Nunito Sans', sans-serif", fontWeight: "900", fontSize: "40px" }}>{t("troisEtapesDeuxMinutes")}</h2>
        </div>
        <div style={{ display: "grid", gridTemplateColumns: "repeat(auto-fit, minmax(min(100%, 300px), 1fr))", gap: "32px" }}>
          <div style={{ display: "flex", flexDirection: "column", gap: "12px" }}>
            <span style={{ fontFamily: "'Nunito Sans', sans-serif", fontWeight: "900", fontSize: "64px", lineHeight: "1", color: "#32327D" }}>
              1
            </span>
            <strong style={{ fontFamily: "'Nunito Sans', sans-serif", fontSize: "20px" }}>{t("choisissezVotreProjet")}</strong>
            <span style={{ fontSize: "15px", lineHeight: "1.65", color: "#4A4A66" }}>{t("parcourezLesCampagnesEn")}</span>
          </div>
          <div style={{ display: "flex", flexDirection: "column", gap: "12px" }}>
            <span style={{ fontFamily: "'Nunito Sans', sans-serif", fontWeight: "900", fontSize: "64px", lineHeight: "1", color: "#32327D" }}>
              2
            </span>
            <strong style={{ fontFamily: "'Nunito Sans', sans-serif", fontSize: "20px" }}>{t("faitesVotreDon")}</strong>
            <span style={{ fontSize: "15px", lineHeight: "1.65", color: "#4A4A66" }}>{t("vousChoisissezLeMontant")}</span>
          </div>
          <div style={{ display: "flex", flexDirection: "column", gap: "12px" }}>
            <span style={{ fontFamily: "'Nunito Sans', sans-serif", fontWeight: "900", fontSize: "64px", lineHeight: "1", color: "#32327D" }}>
              3
            </span>
            <strong style={{ fontFamily: "'Nunito Sans', sans-serif", fontSize: "20px" }}>{t("suivezImpact")}</strong>
            <span style={{ fontSize: "15px", lineHeight: "1.65", color: "#4A4A66" }}>{t("votreEspaceVousMontre")}</span>
          </div>
        </div>
      </div>
    </section>
  );
}
