import { useTranslations } from 'next-intl';
import { Link } from '@/i18n/navigation';

/**
 * Home « Pour les associations » teaser.
 */
export function AssoTeaser() {
  const t = useTranslations('landing.asso');

  return (
    <section style={{ background: "#FFFFFF", padding: "96px 48px" }}>
      <div style={{ maxWidth: "1240px", margin: "0 auto", display: "grid", gridTemplateColumns: "repeat(auto-fit, minmax(min(100%, 460px), 1fr))", gap: "64px", alignItems: "center" }}>
        <div style={{ display: "flex", flexDirection: "column", gap: "24px" }}>
          <span style={{ fontSize: "13px", fontWeight: "700", letterSpacing: "1.5px", textTransform: "uppercase", color: "#1E7F78" }}>{t("pourLesAssociations")}</span>
          <h2 style={{ margin: "0", fontFamily: "'Nunito Sans', sans-serif", fontWeight: "900", fontSize: "40px", lineHeight: "1.15" }}>{t("collectezEtMontrezCe")}</h2>
          <p style={{ margin: "0", fontSize: "17px", lineHeight: "1.7", color: "#4A4A66" }}>{t("votrePageSeMonte")}</p>
          <div style={{ display: "flex", flexDirection: "column", gap: "18px" }}>
            <div style={{ display: "flex", gap: "16px" }}>
              <span style={{ width: "36px", height: "36px", flex: "none", borderRadius: "50%", background: "#4ECDC4", color: "#171744", fontWeight: "800", display: "flex", alignItems: "center", justifyContent: "center" }}>
                1
              </span>
              <div style={{ display: "flex", flexDirection: "column", gap: "4px" }}>
                <strong style={{ fontFamily: "'Nunito Sans', sans-serif", fontSize: "17px" }}>{t("creezVotreCampagne")}</strong>
                <span style={{ fontSize: "15px", lineHeight: "1.6", color: "#4A4A66" }}>{t("votreHistoireVotreBudget")}</span>
              </div>
            </div>
            <div style={{ display: "flex", gap: "16px" }}>
              <span style={{ width: "36px", height: "36px", flex: "none", borderRadius: "50%", background: "#4ECDC4", color: "#171744", fontWeight: "800", display: "flex", alignItems: "center", justifyContent: "center" }}>
                2
              </span>
              <div style={{ display: "flex", flexDirection: "column", gap: "4px" }}>
                <strong style={{ fontFamily: "'Nunito Sans', sans-serif", fontSize: "17px" }}>{t("recevezLesDons")}</strong>
                <span style={{ fontSize: "15px", lineHeight: "1.6", color: "#4A4A66" }}>{t("parCarteBancaireEt")}</span>
              </div>
            </div>
            <div style={{ display: "flex", gap: "16px" }}>
              <span style={{ width: "36px", height: "36px", flex: "none", borderRadius: "50%", background: "#4ECDC4", color: "#171744", fontWeight: "800", display: "flex", alignItems: "center", justifyContent: "center" }}>
                3
              </span>
              <div style={{ display: "flex", flexDirection: "column", gap: "4px" }}>
                <strong style={{ fontFamily: "'Nunito Sans', sans-serif", fontSize: "17px" }}>{t("payezDepuisLaPlateforme")}</strong>
                <span style={{ fontSize: "15px", lineHeight: "1.6", color: "#4A4A66" }}>{t("reglezVosDepensesDepuis")}</span>
              </div>
            </div>
          </div>
          <div style={{ display: "flex", flexWrap: "wrap", gap: "14px" }}>
            <Link href="/associations" style={{ display: "inline-flex", alignItems: "center", minHeight: "52px", padding: "0 26px", borderRadius: "9999px", background: "#32327D", color: "#FFFFFF", fontWeight: "700", fontSize: "16px", textDecoration: "none" }}>{t("creerMaCampagne")}</Link>
            <Link href="/tarifs" style={{ display: "inline-flex", alignItems: "center", minHeight: "52px", padding: "0 26px", borderRadius: "9999px", border: "1.5px solid #32327D", color: "#32327D", fontWeight: "600", fontSize: "16px", textDecoration: "none" }}>{t("voirLesTarifs")}</Link>
          </div>
        </div>
        <div style={{ background: "#32327D", color: "#FFFFFF", borderRadius: "24px", padding: "28px", display: "flex", flexDirection: "column", gap: "20px", boxShadow: "0 24px 60px rgba(23,23,68,0.25)" }}>
          <div style={{ display: "flex", alignItems: "center", justifyContent: "space-between", gap: "12px", flexWrap: "wrap" }}>
            <span style={{ fontFamily: "'Nunito Sans', sans-serif", fontWeight: "900", fontSize: "18px" }}>{t("tableauDeBordCe")}</span>
            <span style={{ fontSize: "12px", fontWeight: "600", color: "#171744", background: "#E6E4F4", borderRadius: "9999px", padding: "4px 10px" }}>{t("exemple")}</span>
          </div>
          <div style={{ display: "grid", gridTemplateColumns: "repeat(3, minmax(0, 1fr))", gap: "12px" }}>
            <div style={{ background: "rgba(255,255,255,0.08)", borderRadius: "16px", padding: "16px", display: "flex", flexDirection: "column", gap: "6px" }}>
              <span style={{ fontSize: "13px", color: "rgba(255,255,255,0.78)" }}>{t("donsCollectes")}</span>
              <span style={{ fontFamily: "'Nunito Sans', sans-serif", fontWeight: "900", fontSize: "24px" }}>
                2 840 €
              </span>
            </div>
            <div style={{ background: "rgba(255,255,255,0.08)", borderRadius: "16px", padding: "16px", display: "flex", flexDirection: "column", gap: "6px" }}>
              <span style={{ fontSize: "13px", color: "rgba(255,255,255,0.78)" }}>{t("nouveauxDonateurs")}</span>
              <span style={{ fontFamily: "'Nunito Sans', sans-serif", fontWeight: "900", fontSize: "24px" }}>
                +23
              </span>
            </div>
            <div style={{ background: "rgba(255,255,255,0.08)", borderRadius: "16px", padding: "16px", display: "flex", flexDirection: "column", gap: "6px" }}>
              <span style={{ fontSize: "13px", color: "rgba(255,255,255,0.78)" }}>{t("paiementsPublies")}</span>
              <span style={{ fontFamily: "'Nunito Sans', sans-serif", fontWeight: "900", fontSize: "24px" }}>
                4
              </span>
            </div>
          </div>
          <div style={{ display: "flex", flexDirection: "column", gap: "10px" }}>
            <div style={{ display: "flex", justifyContent: "space-between", fontSize: "14px" }}>
              <span style={{ color: "rgba(255,255,255,0.85)" }}>{t("avancementDeLaCampagne")}</span>
              <strong style={{ color: "#4ECDC4" }}>
                71 %
              </strong>
            </div>
            <div style={{ height: "10px", borderRadius: "9999px", background: "rgba(255,255,255,0.15)", overflow: "hidden" }}>
              <div style={{ width: "71%", height: "100%", background: "#4ECDC4", borderRadius: "9999px" }} />
            </div>
            <span style={{ fontSize: "13px", color: "rgba(255,255,255,0.78)" }}>{t("collectes")}</span>
          </div>
        </div>
      </div>
    </section>
  );
}
