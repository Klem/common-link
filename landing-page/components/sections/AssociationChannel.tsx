import { useTranslations } from 'next-intl';

/**
 * Associations « Un nouveau canal de collecte » section.
 */
export function AssociationChannel() {
  const t = useTranslations('associations.channel');

  return (
    <section id="associations-canal" style={{ background: "#FDF8F0", padding: "96px 48px" }}>
      <div style={{ maxWidth: "1240px", margin: "0 auto", display: "grid", gridTemplateColumns: "repeat(auto-fit, minmax(min(100%, 440px), 1fr))", gap: "56px", alignItems: "center" }}>
        <div style={{ display: "flex", flexDirection: "column", gap: "18px" }}>
          <span style={{ fontSize: "13px", fontWeight: "700", letterSpacing: "1.5px", textTransform: "uppercase", color: "#D9473A" }}>{t("unNouveauCanalDe")}</span>
          <h2 style={{ margin: "0", fontFamily: "'Nunito Sans', sans-serif", fontWeight: "900", fontSize: "44px", lineHeight: "1.1" }}>{t("votrePageDeCollecte")}</h2>
          <p style={{ margin: "0", fontSize: "18px", lineHeight: "1.6", color: "#4A4A66" }}>{t("commonlinkLaGenerePartir")}</p>
          <div style={{ display: "flex", flexDirection: "column", gap: "12px", paddingTop: "6px" }}>
            <span style={{ display: "flex", alignItems: "center", gap: "12px", fontSize: "17px", fontWeight: "600" }}>
              <span aria-hidden="true" style={{ width: "28px", height: "28px", flex: "none", borderRadius: "50%", background: "rgba(255,107,91,0.16)", display: "flex", alignItems: "center", justifyContent: "center" }}>
                <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="#D9473A" strokeWidth="3" strokeLinecap="round" strokeLinejoin="round">
                  <path d="M5 12l5 5L19 7" />
                </svg>
              </span>
              {t("genereePartirDeVotre")}
            </span>
            <span style={{ display: "flex", alignItems: "center", gap: "12px", fontSize: "17px", fontWeight: "600" }}>
              <span aria-hidden="true" style={{ width: "28px", height: "28px", flex: "none", borderRadius: "50%", background: "rgba(255,107,91,0.16)", display: "flex", alignItems: "center", justifyContent: "center" }}>
                <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="#D9473A" strokeWidth="3" strokeLinecap="round" strokeLinejoin="round">
                  <path d="M5 12l5 5L19 7" />
                </svg>
              </span>
              {t("unLienPartagerPartout")}
            </span>
            <span style={{ display: "flex", alignItems: "center", gap: "12px", fontSize: "17px", fontWeight: "600" }}>
              <span aria-hidden="true" style={{ width: "28px", height: "28px", flex: "none", borderRadius: "50%", background: "rgba(255,107,91,0.16)", display: "flex", alignItems: "center", justifyContent: "center" }}>
                <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="#D9473A" strokeWidth="3" strokeLinecap="round" strokeLinejoin="round">
                  <path d="M5 12l5 5L19 7" />
                </svg>
              </span>
              {t("integrableVotreSite")}
            </span>
          </div>
        </div>
        <div style={{ background: "#FFFFFF", borderRadius: "20px", boxShadow: "0 30px 70px rgba(23,23,68,0.18)", overflow: "hidden", border: "1px solid #ECEAF5" }}>
          <div style={{ display: "flex", alignItems: "center", gap: "12px", padding: "12px 16px", background: "#F4F2FA", borderBottom: "1px solid #ECEAF5" }}>
            <span aria-hidden="true" style={{ display: "flex", gap: "6px" }}>
              <span style={{ width: "10px", height: "10px", borderRadius: "50%", background: "#FF6B5B" }} />
              <span style={{ width: "10px", height: "10px", borderRadius: "50%", background: "#FFB347" }} />
              <span style={{ width: "10px", height: "10px", borderRadius: "50%", background: "#4ECDC4" }} />
            </span>
            <span style={{ flex: "1", fontSize: "12px", color: "#4A4A66", background: "#FFFFFF", borderRadius: "9999px", padding: "5px 12px" }}>{t("commonLinkOrgLp")}</span>
          </div>
          <div style={{ padding: "28px 30px 30px", display: "flex", flexDirection: "column", gap: "16px" }}>
            <div style={{ display: "flex", alignItems: "center", gap: "10px" }}>
              <span style={{ width: "36px", height: "36px", borderRadius: "10px", background: "#4ECDC4", color: "#171744", fontFamily: "'Nunito Sans', sans-serif", fontWeight: "900", fontSize: "14px", display: "flex", alignItems: "center", justifyContent: "center" }}>{t("ss")}</span>
              <span style={{ display: "flex", flexDirection: "column" }}>
                <strong style={{ fontSize: "14px" }}>{t("souriresSolidaires")}</strong>
                <span style={{ fontSize: "12px", color: "#4A4A66" }}>{t("associationBordeaux")}</span>
              </span>
            </div>
            <strong style={{ fontFamily: "'Nunito Sans', sans-serif", fontWeight: "900", fontSize: "26px", lineHeight: "1.15" }}>{t("cantineSolidairePourFamilles")}</strong>
            <span style={{ fontSize: "15px", lineHeight: "1.5", color: "#4A4A66" }}>{t("unRepasChaudChaque")}</span>
            <span style={{ display: "inline-flex", alignSelf: "flex-start", alignItems: "center", minHeight: "50px", padding: "0 26px", borderRadius: "9999px", background: "#FF6B5B", color: "#FFFFFF", fontFamily: "'Nunito Sans', sans-serif", fontWeight: "800", fontSize: "16px" }}>{t("jeFaisUnDon")}</span>
            <div style={{ display: "flex", flexWrap: "wrap", gap: "8px" }}>
              <span style={{ fontSize: "12px", fontWeight: "600", background: "#FCF8F1", border: "1px solid #ECEAF5", borderRadius: "9999px", padding: "6px 12px" }}>{t("recuFiscal")}</span>
              <span style={{ fontSize: "12px", fontWeight: "600", background: "#FCF8F1", border: "1px solid #ECEAF5", borderRadius: "9999px", padding: "6px 12px" }}>{t("paiementSecuriseParHelloasso")}</span>
              <span style={{ fontSize: "12px", fontWeight: "600", background: "#FCF8F1", border: "1px solid #ECEAF5", borderRadius: "9999px", padding: "6px 12px" }}>{t("objectif")}</span>
            </div>
            <div style={{ display: "flex", flexDirection: "column", gap: "10px", paddingTop: "14px", borderTop: "1px solid #ECEAF5" }}>
              <span style={{ fontSize: "12px", fontWeight: "700", letterSpacing: "1px", color: "#4A4A66" }}>{t("budgetDuProjet")}</span>
              <div style={{ display: "flex", flexWrap: "wrap", gap: "14px", fontSize: "14px" }}>
                <span style={{ display: "flex", alignItems: "center", gap: "6px" }}>{t.rich("denrees", { t0: (chunks) => <span style={{ width: "10px", height: "10px", borderRadius: "50%", background: "#4ECDC4" }}>{chunks}</span>, t1: (chunks) => <strong>{chunks}</strong> })}</span>
                <span style={{ display: "flex", alignItems: "center", gap: "6px" }}>{t.rich("transport", { t0: (chunks) => <span style={{ width: "10px", height: "10px", borderRadius: "50%", background: "#FF6B5B" }}>{chunks}</span>, t1: (chunks) => <strong>{chunks}</strong> })}</span>
                <span style={{ display: "flex", alignItems: "center", gap: "6px" }}>{t.rich("equipement", { t0: (chunks) => <span style={{ width: "10px", height: "10px", borderRadius: "50%", background: "#FFB347" }}>{chunks}</span>, t1: (chunks) => <strong>{chunks}</strong> })}</span>
              </div>
            </div>
          </div>
        </div>
      </div>
    </section>
  );
}
