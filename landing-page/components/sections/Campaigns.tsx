import { useTranslations } from 'next-intl';
import { Link } from '@/i18n/navigation';

/**
 * Home « Campagnes en cours » section (empty state, as in the mockup).
 */
export function Campaigns() {
  const t = useTranslations('landing.campaigns');

  return (
    <section style={{ background: "#FDF8F0", padding: "96px 48px" }}>
      <div style={{ maxWidth: "1240px", margin: "0 auto", display: "flex", flexDirection: "column", gap: "32px" }}>
        <div style={{ display: "flex", alignItems: "flex-end", justifyContent: "space-between", gap: "16px", flexWrap: "wrap" }}>
          <div style={{ display: "flex", flexDirection: "column", gap: "12px" }}>
            <span style={{ fontSize: "13px", fontWeight: "700", letterSpacing: "1.5px", textTransform: "uppercase", color: "#1E7F78" }}>{t("campagnesEnCours")}</span>
            <h2 style={{ margin: "0", fontFamily: "'Nunito Sans', sans-serif", fontWeight: "900", fontSize: "40px" }}>{t("ilsCollectentEnCe")}</h2>
          </div>
          <Link href="/projets" style={{ display: "inline-flex", alignItems: "center", minHeight: "44px", fontWeight: "700", fontSize: "15px", color: "#32327D" }}>{t("voirTousLesProjets")}</Link>
        </div>
        <div style={{ background: "#FFFFFF", border: "1px solid #E6E4F4", borderRadius: "24px", padding: "64px 32px", display: "flex", flexDirection: "column", alignItems: "center", gap: "12px", textAlign: "center" }}>
          <svg width="48" height="48" viewBox="0 0 24 24" fill="none" stroke="#1E7F78" strokeWidth="1.6" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
            <path d="M12 21v-9" />
            <path d="M12 12c0-4 3-6 7-6 0 4-3 6-7 6z" />
            <path d="M12 14c0-3-2.5-5-6-5 0 3 2.5 5 6 5z" />
            <path d="M7 21h10" />
          </svg>
          <h3 style={{ margin: "0", fontFamily: "'Nunito Sans', sans-serif", fontWeight: "900", fontSize: "22px" }}>{t("rienPourInstant")}</h3>
          <p style={{ margin: "0", fontSize: "16px", color: "#4A4A66" }}>{t("lesPremieresCampagnesArrivent")}</p>
        </div>
      </div>
    </section>
  );
}
