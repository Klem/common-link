import type { ReactNode } from 'react';
import { useTranslations } from 'next-intl';
import { Link } from '@/i18n/navigation';

/**
 * « Tous les projets » section of the projects page, markup and inline styles from the mockup.
 *
 * The mockup only shows the empty state. When live campaigns exist, the page passes the campaign
 * directory as `children`, rendered in place of that empty state.
 */
export function ProjectsSection({ children }: { children?: ReactNode }) {
  const t = useTranslations('projects.section');

  return (
    <section style={{ background: "#FDF8F0", padding: "64px 48px 96px" }}>
      <div style={{ maxWidth: "1240px", margin: "0 auto", display: "flex", flexDirection: "column", gap: "32px" }}>
        <nav aria-label={t("filAriane")} style={{ fontSize: "14px", color: "#4A4A66", display: "flex", gap: "8px" }}>
          <Link href="/" style={{ color: "#32327D", fontWeight: "600" }}>{t("accueil")}</Link>
          <span aria-hidden="true">
            ›
          </span>
          <span>{t("projets")}</span>
        </nav>
        <div style={{ display: "flex", flexDirection: "column", gap: "12px", maxWidth: "720px" }}>
          <h1 style={{ margin: "0", fontFamily: "'Nunito Sans', sans-serif", fontWeight: "900", fontSize: "48px", lineHeight: "1.1" }}>{t("tousLesProjets")}</h1>
          <p style={{ margin: "0", fontSize: "18px", lineHeight: "1.65", color: "#4A4A66" }}>{t("chaquePageIndiqueLe")}</p>
        </div>
        {children ?? (
          <div style={{ background: "#FFFFFF", border: "1px solid #E6E4F4", borderRadius: "24px", padding: "80px 32px", display: "flex", flexDirection: "column", alignItems: "center", gap: "14px", textAlign: "center" }}>
            <span style={{ width: "88px", height: "88px", borderRadius: "50%", background: "rgba(78,205,196,0.14)", display: "flex", alignItems: "center", justifyContent: "center" }}>
              <svg width="44" height="44" viewBox="0 0 24 24" fill="none" stroke="#1E7F78" strokeWidth="1.6" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
                <path d="M12 21v-9" />
                <path d="M12 12c0-4 3-6 7-6 0 4-3 6-7 6z" />
                <path d="M12 14c0-3-2.5-5-6-5 0 3 2.5 5 6 5z" />
                <path d="M7 21h10" />
              </svg>
            </span>
            <h2 style={{ margin: "0", fontFamily: "'Nunito Sans', sans-serif", fontWeight: "900", fontSize: "26px" }}>{t("rienPourInstant")}</h2>
            <p style={{ margin: "0", fontSize: "17px", color: "#4A4A66" }}>{t("lesPremieresCampagnesArrivent")}</p>
          </div>
        )}
      </div>
    </section>
  );
}
