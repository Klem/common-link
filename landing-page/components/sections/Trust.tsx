import { useTranslations } from 'next-intl';

/**
 * Home « Confiance et sécurité » section: three commitments.
 */
export function Trust() {
  const t = useTranslations('landing.trust');

  return (
    <section style={{ background: "#32327D", color: "#FFFFFF", padding: "96px 48px" }}>
      <div style={{ maxWidth: "1240px", margin: "0 auto", display: "flex", flexDirection: "column", gap: "48px" }}>
        <div style={{ display: "flex", flexDirection: "column", gap: "12px", maxWidth: "640px" }}>
          <span style={{ fontSize: "13px", fontWeight: "700", letterSpacing: "1.5px", textTransform: "uppercase", color: "#4ECDC4" }}>{t("confianceEtSecurite")}</span>
          <h2 style={{ margin: "0", fontFamily: "'Nunito Sans', sans-serif", fontWeight: "900", fontSize: "40px", lineHeight: "1.15" }}>{t("troisEngagementsSurChaque")}</h2>
        </div>
        <div style={{ display: "grid", gridTemplateColumns: "repeat(auto-fit, minmax(min(100%, 300px), 1fr))", gap: "40px" }}>
          <div style={{ display: "flex", flexDirection: "column", gap: "12px", paddingTop: "24px", borderTop: "2px solid #4ECDC4" }}>
            <svg width="28" height="28" viewBox="0 0 24 24" fill="none" stroke="#4ECDC4" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
              <path d="M7 3h7l5 5v13H7z" />
              <path d="M14 3v5h5" />
              <path d="M10 14l2 2 4-4" />
            </svg>
            <strong style={{ fontFamily: "'Nunito Sans', sans-serif", fontSize: "20px" }}>{t("recuFiscalAutomatique")}</strong>
            <span style={{ fontSize: "15px", lineHeight: "1.65", color: "rgba(255,255,255,0.82)" }}>{t("votreRecuPartDes")}</span>
          </div>
          <div style={{ display: "flex", flexDirection: "column", gap: "12px", paddingTop: "24px", borderTop: "2px solid #4ECDC4" }}>
            <svg width="28" height="28" viewBox="0 0 24 24" fill="none" stroke="#4ECDC4" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
              <path d="M4 6h16M4 12h16M4 18h10" />
            </svg>
            <strong style={{ fontFamily: "'Nunito Sans', sans-serif", fontSize: "20px" }}>{t("paiementsPublies")}</strong>
            <span style={{ fontSize: "15px", lineHeight: "1.65", color: "rgba(255,255,255,0.82)" }}>{t("montantDestinataireDateVisible")}</span>
          </div>
          <div style={{ display: "flex", flexDirection: "column", gap: "12px", paddingTop: "24px", borderTop: "2px solid #4ECDC4" }}>
            <svg width="28" height="28" viewBox="0 0 24 24" fill="none" stroke="#4ECDC4" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
              <rect x="5" y="11" width="14" height="10" rx="2" />
              <path d="M8 11V8a4 4 0 0 1 8 0v3" />
            </svg>
            <strong style={{ fontFamily: "'Nunito Sans', sans-serif", fontSize: "20px" }}>{t("registreSecurise")}</strong>
            <span style={{ fontSize: "15px", lineHeight: "1.65", color: "rgba(255,255,255,0.82)" }}>{t("chaquePaiementPublieEst")}</span>
          </div>
        </div>
      </div>
    </section>
  );
}
