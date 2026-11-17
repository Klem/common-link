import { useTranslations } from 'next-intl';
import { Link } from '@/i18n/navigation';
import { APP_URL } from '@/lib/constants';

/**
 * Associations page hero.
 */
export function AssociationHero() {
  const t = useTranslations('associations.hero');

  return (
    <section style={{ background: "#32327D", color: "#FFFFFF", padding: "88px 48px 96px" }}>
      <div style={{ maxWidth: "1240px", margin: "0 auto", display: "grid", gridTemplateColumns: "repeat(auto-fit, minmax(min(100%, 460px), 1fr))", gap: "56px", alignItems: "center" }}>
        <div style={{ display: "flex", flexDirection: "column", gap: "26px" }}>
          <span style={{ fontSize: "13px", fontWeight: "700", letterSpacing: "1.5px", textTransform: "uppercase", color: "#4ECDC4" }}>{t("pourLesAssociations")}</span>
          <h1 style={{ margin: "0", fontFamily: "'Nunito Sans', sans-serif", fontWeight: "900", fontSize: "54px", lineHeight: "1.08", letterSpacing: "-0.5px" }}>{t.rich("unNouveauCanalDe", { t0: (chunks) => <span style={{ fontFamily: "Lora, serif", fontStyle: "italic", fontWeight: "500", color: "#FFB347" }}>{chunks}</span> })}</h1>
          <p style={{ margin: "0", fontSize: "19px", lineHeight: "1.65", color: "rgba(255,255,255,0.84)", maxWidth: "540px" }}>{t("commonlinkPublieVosDepenses")}</p>
          <div style={{ display: "flex", flexWrap: "wrap", gap: "14px" }}>
            <a href={APP_URL} style={{ display: "inline-flex", alignItems: "center", minHeight: "52px", padding: "0 28px", borderRadius: "9999px", background: "#4ECDC4", color: "#171744", fontWeight: "700", fontSize: "16px", textDecoration: "none" }}>{t("creerMonCompteAssociation")}</a>
            <Link href="/tarifs" style={{ display: "inline-flex", alignItems: "center", minHeight: "52px", padding: "0 28px", borderRadius: "9999px", border: "1.5px solid rgba(255,255,255,0.55)", color: "#FFFFFF", fontWeight: "600", fontSize: "16px", textDecoration: "none" }}>{t("voirLesTarifs")}</Link>
          </div>
          <div style={{ display: "grid", gridTemplateColumns: "repeat(3, minmax(0, 1fr))", gap: "20px", paddingTop: "26px", borderTop: "1px solid rgba(255,255,255,0.14)" }}>
            <div style={{ display: "flex", flexDirection: "column", gap: "4px" }}>
              <span style={{ fontFamily: "'Nunito Sans', sans-serif", fontWeight: "900", fontSize: "32px", color: "#4ECDC4", whiteSpace: "nowrap" }}>
                8 %
              </span>
              <span style={{ fontSize: "14px", lineHeight: "1.45", color: "rgba(255,255,255,0.80)" }}>{t("htDeCommissionSans")}</span>
            </div>
            <div style={{ display: "flex", flexDirection: "column", gap: "4px" }}>
              <span style={{ fontFamily: "'Nunito Sans', sans-serif", fontWeight: "900", fontSize: "32px", color: "#FFB347", whiteSpace: "nowrap" }}>
                0 €
              </span>
              <span style={{ fontSize: "14px", lineHeight: "1.45", color: "rgba(255,255,255,0.80)" }}>{t("detenuParCommonlink")}</span>
            </div>
            <div style={{ display: "flex", flexDirection: "column", gap: "4px" }}>
              <span style={{ fontFamily: "'Nunito Sans', sans-serif", fontWeight: "900", fontSize: "32px", color: "#FFFFFF", whiteSpace: "nowrap" }}>
                0
              </span>
              <span style={{ fontSize: "14px", lineHeight: "1.45", color: "rgba(255,255,255,0.80)" }}>{t("documentFournirPourVos")}</span>
            </div>
          </div>
        </div>
        <div style={{ display: "grid", gridTemplateColumns: "repeat(2, minmax(0, 1fr))", gap: "16px" }}>
          <a href="#associations-temps" style={{ background: "#FFFFFF", color: "#171744", borderRadius: "24px", padding: "28px", display: "flex", flexDirection: "column", gap: "14px", textDecoration: "none", minHeight: "240px", boxSizing: "border-box" }}>
            <span aria-hidden="true" style={{ width: "56px", height: "56px", borderRadius: "16px", background: "rgba(78,205,196,0.18)", display: "flex", alignItems: "center", justifyContent: "center" }}>
              <svg width="28" height="28" viewBox="0 0 24 24" fill="none" stroke="#1E7F78" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
                <circle cx="12" cy="13" r="8" />
                <path d="M12 9v4l2.5 2" />
                <path d="M9 2h6" />
              </svg>
            </span>
            <span style={{ fontSize: "13px", fontWeight: "800", letterSpacing: "1.2px", textTransform: "uppercase", color: "#1E7F78" }}>{t("gainDeTemps")}</span>
            <strong style={{ fontFamily: "'Nunito Sans', sans-serif", fontWeight: "900", fontSize: "25px", lineHeight: "1.2" }}>{t("vosPaiementsSePublient")}</strong>
          </a>
          <a href="#associations-canal" style={{ background: "#FFFFFF", color: "#171744", borderRadius: "24px", padding: "28px", display: "flex", flexDirection: "column", gap: "14px", textDecoration: "none", minHeight: "240px", boxSizing: "border-box" }}>
            <span aria-hidden="true" style={{ width: "56px", height: "56px", borderRadius: "16px", background: "rgba(255,107,91,0.16)", display: "flex", alignItems: "center", justifyContent: "center" }}>
              <svg width="28" height="28" viewBox="0 0 24 24" fill="none" stroke="#D9473A" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
                <path d="M3 11v2a1 1 0 0 0 1 1h2l5 4V6L6 10H4a1 1 0 0 0-1 1z" />
                <path d="M16 8a5 5 0 0 1 0 8" />
                <path d="M19 5a9 9 0 0 1 0 14" />
              </svg>
            </span>
            <span style={{ fontSize: "13px", fontWeight: "800", letterSpacing: "1.2px", textTransform: "uppercase", color: "#D9473A" }}>{t("nouveauCanal")}</span>
            <strong style={{ fontFamily: "'Nunito Sans', sans-serif", fontWeight: "900", fontSize: "25px", lineHeight: "1.2" }}>{t("unePageDeCollecte")}</strong>
          </a>
          <a href="#associations-donateurs" style={{ background: "#FFFFFF", color: "#171744", borderRadius: "24px", padding: "28px", display: "flex", flexDirection: "column", gap: "14px", textDecoration: "none", minHeight: "240px", boxSizing: "border-box" }}>
            <span aria-hidden="true" style={{ width: "56px", height: "56px", borderRadius: "16px", background: "rgba(255,179,71,0.22)", display: "flex", alignItems: "center", justifyContent: "center" }}>
              <svg width="28" height="28" viewBox="0 0 24 24" fill="none" stroke="#A86A00" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
                <circle cx="9" cy="8" r="3.5" />
                <path d="M2.5 20a6.5 6.5 0 0 1 13 0" />
                <path d="M16 4.5a3.5 3.5 0 0 1 0 7" />
                <path d="M18 14a6.5 6.5 0 0 1 3.5 6" />
              </svg>
            </span>
            <span style={{ fontSize: "13px", fontWeight: "800", letterSpacing: "1.2px", textTransform: "uppercase", color: "#A86A00" }}>{t("nouveauxDonateurs")}</span>
            <strong style={{ fontFamily: "'Nunito Sans', sans-serif", fontWeight: "900", fontSize: "25px", lineHeight: "1.2" }}>{t("ceuxQuiVeulentVoir")}</strong>
          </a>
          <a href="#associations-autonomie" style={{ background: "#FFFFFF", color: "#171744", borderRadius: "24px", padding: "28px", display: "flex", flexDirection: "column", gap: "14px", textDecoration: "none", minHeight: "240px", boxSizing: "border-box" }}>
            <span aria-hidden="true" style={{ width: "56px", height: "56px", borderRadius: "16px", background: "rgba(50,50,125,0.12)", display: "flex", alignItems: "center", justifyContent: "center" }}>
              <svg width="28" height="28" viewBox="0 0 24 24" fill="none" stroke="#32327D" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
                <circle cx="8" cy="15" r="4" />
                <path d="M11 12l9-9" />
                <path d="M17 6l3 3" />
                <path d="M15 8l2 2" />
              </svg>
            </span>
            <span style={{ fontSize: "13px", fontWeight: "800", letterSpacing: "1.2px", textTransform: "uppercase", color: "#32327D" }}>{t("autonomie")}</span>
            <strong style={{ fontFamily: "'Nunito Sans', sans-serif", fontWeight: "900", fontSize: "25px", lineHeight: "1.2" }}>{t("desDonsVousDes")}</strong>
          </a>
        </div>
      </div>
    </section>
  );
}
