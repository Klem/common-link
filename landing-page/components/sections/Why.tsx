import { useTranslations } from 'next-intl';

/**
 * Home « Pourquoi CommonLink » section.
 */
export function Why() {
  const t = useTranslations('landing.why');

  return (
    <section style={{ background: "#FFFFFF", padding: "96px 48px" }}>
      <div style={{ maxWidth: "1240px", margin: "0 auto", display: "grid", gridTemplateColumns: "repeat(auto-fit, minmax(min(100%, 460px), 1fr))", gap: "64px", alignItems: "center" }}>
        <div style={{ display: "flex", flexDirection: "column", gap: "24px" }}>
          <span style={{ fontSize: "13px", fontWeight: "700", letterSpacing: "1.5px", textTransform: "uppercase", color: "#1E7F78" }}>{t("pourquoiCommonlink")}</span>
          <h2 style={{ margin: "0", fontFamily: "'Nunito Sans', sans-serif", fontWeight: "900", fontSize: "40px", lineHeight: "1.15" }}>{t("donnerSansSeDemander")}</h2>
          <p style={{ margin: "0", fontSize: "17px", lineHeight: "1.7", color: "#4A4A66" }}>{t("lesAssociationsReglentLeurs")}</p>
          <ol style={{ listStyle: "none", margin: "0", padding: "0", display: "flex", flexDirection: "column", gap: "22px" }}>
            <li style={{ display: "flex", gap: "18px" }}>
              <span style={{ fontFamily: "'Nunito Sans', sans-serif", fontWeight: "900", fontSize: "20px", color: "#1E7F78", minWidth: "36px" }}>
                01
              </span>
              <div style={{ display: "flex", flexDirection: "column", gap: "4px" }}>
                <strong style={{ fontFamily: "'Nunito Sans', sans-serif", fontSize: "18px" }}>{t("donnezSimplement")}</strong>
                <span style={{ fontSize: "15px", lineHeight: "1.6", color: "#4A4A66" }}>{t("parCarteBancaireEt")}</span>
              </div>
            </li>
            <li style={{ display: "flex", gap: "18px" }}>
              <span style={{ fontFamily: "'Nunito Sans', sans-serif", fontWeight: "900", fontSize: "20px", color: "#1E7F78", minWidth: "36px" }}>
                02
              </span>
              <div style={{ display: "flex", flexDirection: "column", gap: "4px" }}>
                <strong style={{ fontFamily: "'Nunito Sans', sans-serif", fontSize: "18px" }}>{t("suivezImpact")}</strong>
                <span style={{ fontSize: "15px", lineHeight: "1.6", color: "#4A4A66" }}>{t("vosDonsEtCe")}</span>
              </div>
            </li>
            <li style={{ display: "flex", gap: "18px" }}>
              <span style={{ fontFamily: "'Nunito Sans', sans-serif", fontWeight: "900", fontSize: "20px", color: "#1E7F78", minWidth: "36px" }}>
                03
              </span>
              <div style={{ display: "flex", flexDirection: "column", gap: "4px" }}>
                <strong style={{ fontFamily: "'Nunito Sans', sans-serif", fontSize: "18px" }}>{t("verifiezParVousMeme")}</strong>
                <span style={{ fontSize: "15px", lineHeight: "1.6", color: "#4A4A66" }}>{t("chaquePaiementPublieEst")}</span>
              </div>
            </li>
          </ol>
        </div>
        <div style={{ background: "#FDF8F0", border: "1px solid #E6E4F4", borderRadius: "24px", padding: "28px", display: "flex", flexDirection: "column", gap: "14px" }}>
          <div style={{ display: "flex", alignItems: "center", justifyContent: "space-between", gap: "12px", flexWrap: "wrap" }}>
            <div style={{ display: "flex", flexDirection: "column", gap: "2px" }}>
              <span style={{ fontFamily: "'Nunito Sans', sans-serif", fontWeight: "900", fontSize: "18px" }}>{t("paiementsPublies")}</span>
              <span style={{ fontSize: "13px", color: "#4A4A66" }}>{t("cantineSolidaireSouriresSolidaires")}</span>
            </div>
            <span style={{ fontSize: "12px", fontWeight: "600", color: "#4A4A66", background: "#E6E4F4", borderRadius: "9999px", padding: "4px 10px" }}>{t("exemple")}</span>
          </div>
          <div style={{ background: "#FFFFFF", borderRadius: "16px", padding: "16px 18px", display: "flex", alignItems: "center", justifyContent: "space-between", gap: "16px", flexWrap: "wrap" }}>
            <div style={{ display: "flex", flexDirection: "column", gap: "4px" }}>
              <span style={{ fontWeight: "700", fontSize: "15px" }}>{t("achatDeDenreesAlimentaires")}</span>
              <span style={{ fontSize: "13px", color: "#4A4A66" }}>{t("grossisteBioBordeauxJanv")}</span>
              <span style={{ fontFamily: "ui-monospace, Menlo, monospace", fontSize: "12px", color: "#32327D" }}>{t("empreinte")}</span>
            </div>
            <span style={{ fontFamily: "'Nunito Sans', sans-serif", fontWeight: "900", fontSize: "18px" }}>
              1 240 €
            </span>
          </div>
          <div style={{ background: "#FFFFFF", borderRadius: "16px", padding: "16px 18px", display: "flex", alignItems: "center", justifyContent: "space-between", gap: "16px", flexWrap: "wrap" }}>
            <div style={{ display: "flex", flexDirection: "column", gap: "4px" }}>
              <span style={{ fontWeight: "700", fontSize: "15px" }}>{t("locationCamionRefrigere")}</span>
              <span style={{ fontSize: "13px", color: "#4A4A66" }}>{t("steTransportRapideJanv")}</span>
              <span style={{ fontFamily: "ui-monospace, Menlo, monospace", fontSize: "12px", color: "#32327D" }}>{t("empreinteFd")}</span>
            </div>
            <span style={{ fontFamily: "'Nunito Sans', sans-serif", fontWeight: "900", fontSize: "18px" }}>
              320 €
            </span>
          </div>
          <div style={{ background: "#FFFFFF", borderRadius: "16px", padding: "16px 18px", display: "flex", alignItems: "center", justifyContent: "space-between", gap: "16px", flexWrap: "wrap" }}>
            <div style={{ display: "flex", flexDirection: "column", gap: "4px" }}>
              <span style={{ fontWeight: "700", fontSize: "15px" }}>{t("remboursementDeFraisDe")}</span>
              <span style={{ fontSize: "13px", color: "#4A4A66" }}>{t("personnePhysiqueNomNon")}</span>
              <span style={{ fontFamily: "ui-monospace, Menlo, monospace", fontSize: "12px", color: "#32327D" }}>{t("empreinte2")}</span>
            </div>
            <span style={{ fontFamily: "'Nunito Sans', sans-serif", fontWeight: "900", fontSize: "18px" }}>
              45 €
            </span>
          </div>
        </div>
      </div>
    </section>
  );
}
