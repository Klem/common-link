import { useTranslations } from 'next-intl';
import { APP_URL } from '@/lib/constants';

/**
 * Donors page hero.
 */
export function DonorHero() {
  const t = useTranslations('donors.hero');

  return (
    <section style={{ background: "#FDF8F0", padding: "80px 48px 72px" }}>
      <div style={{ maxWidth: "1240px", margin: "0 auto", display: "flex", flexDirection: "column", gap: "28px" }}>
        <span style={{ fontSize: "13px", fontWeight: "700", letterSpacing: "1.5px", textTransform: "uppercase", color: "#1E7F78" }}>{t("pourLesDonateurs")}</span>
        <h1 style={{ margin: "0", fontFamily: "'Nunito Sans', sans-serif", fontWeight: "900", fontSize: "56px", lineHeight: "1.08", letterSpacing: "-0.5px", maxWidth: "820px" }}>{t.rich("vousDonnezVousVoyez", { t0: (chunks) => <span style={{ fontFamily: "Lora, serif", fontStyle: "italic", fontWeight: "500", color: "#1E7F78" }}>{chunks}</span> })}</h1>
        <p style={{ margin: "0", fontSize: "19px", lineHeight: "1.65", color: "#4A4A66", maxWidth: "640px" }}>{t("associationRegleSesDepenses")}</p>
        <div style={{ display: "grid", gridTemplateColumns: "repeat(auto-fit, minmax(min(100%, 300px), 1fr))", gap: "20px" }}>
          <div style={{ display: "flex", flexDirection: "column", gap: "14px", background: "#FFFFFF", border: "1px solid #ECEAF5", borderRadius: "24px", padding: "28px" }}>
            <span aria-hidden="true" style={{ width: "60px", height: "60px", borderRadius: "18px", background: "rgba(78,205,196,0.16)", display: "flex", alignItems: "center", justifyContent: "center" }}>
              <svg width="30" height="30" viewBox="0 0 24 24" fill="none" stroke="#1E7F78" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
                <rect x="3" y="6" width="18" height="12" rx="2" />
                <path d="M3 10h18" />
              </svg>
            </span>
            <strong style={{ fontFamily: "'Nunito Sans', sans-serif", fontWeight: "900", fontSize: "26px", lineHeight: "1.15" }}>{t("donEnQuelquesClics")}</strong>
            <span style={{ fontSize: "17px", lineHeight: "1.5", color: "#4A4A66" }}>{t("parCarteBancaireEt")}</span>
          </div>
          <div style={{ display: "flex", flexDirection: "column", gap: "14px", background: "#FFFFFF", border: "1px solid #ECEAF5", borderRadius: "24px", padding: "28px" }}>
            <span aria-hidden="true" style={{ width: "60px", height: "60px", borderRadius: "18px", background: "rgba(255,107,91,0.14)", display: "flex", alignItems: "center", justifyContent: "center" }}>
              <svg width="30" height="30" viewBox="0 0 24 24" fill="none" stroke="#D9473A" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
                <path d="M3 17l6-6 4 4 8-8" />
                <path d="M15 7h6v6" />
              </svg>
            </span>
            <strong style={{ fontFamily: "'Nunito Sans', sans-serif", fontWeight: "900", fontSize: "26px", lineHeight: "1.15" }}>{t("suiviEnDirect")}</strong>
            <span style={{ fontSize: "17px", lineHeight: "1.5", color: "#4A4A66" }}>{t("votreEspaceJourEn")}</span>
          </div>
          <div style={{ display: "flex", flexDirection: "column", gap: "14px", background: "#FFFFFF", border: "1px solid #ECEAF5", borderRadius: "24px", padding: "28px" }}>
            <span aria-hidden="true" style={{ width: "60px", height: "60px", borderRadius: "18px", background: "rgba(255,179,71,0.20)", display: "flex", alignItems: "center", justifyContent: "center" }}>
              <svg width="30" height="30" viewBox="0 0 24 24" fill="none" stroke="#A86A00" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
                <path d="M7 3h7l5 5v13H7z" />
                <path d="M14 3v5h5" />
                <path d="M10 14l2 2 4-4" />
              </svg>
            </span>
            <strong style={{ fontFamily: "'Nunito Sans', sans-serif", fontWeight: "900", fontSize: "26px", lineHeight: "1.15" }}>{t("recuFiscalAutomatique")}</strong>
            <span style={{ fontSize: "17px", lineHeight: "1.5", color: "#4A4A66" }}>{t("cerfaPretTelecharger")}</span>
          </div>
        </div>
        <div>
          <a href={APP_URL} style={{ display: "inline-flex", alignItems: "center", minHeight: "52px", padding: "0 28px", borderRadius: "9999px", background: "#32327D", color: "#FFFFFF", fontWeight: "700", fontSize: "16px", textDecoration: "none" }}>{t("creerMonCompte")}</a>
        </div>
      </div>
    </section>
  );
}
