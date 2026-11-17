import { useTranslations } from 'next-intl';
import type { Reveal } from './Tarifs';

type TarifsDonorProps = { d0: Reveal; d1: Reveal; watch: (el: Element | null) => void };

/**
 * Pricing for donors: giving costs nothing.
 */
export function TarifsDonor({ d0, d1, watch }: TarifsDonorProps) {
  const t = useTranslations('tarifs.donor');

  return (
    <>
      <section style={{ background: "#FDF8F0", padding: "80px 48px 96px" }}>
        <div style={{ maxWidth: "1240px", margin: "0 auto", display: "flex", flexDirection: "column", gap: "40px" }}>
          <div style={{ display: "grid", gridTemplateColumns: "repeat(auto-fit, minmax(min(100%, 460px), 1fr))", gap: "40px", alignItems: "center" }}>
            <div style={{ display: "flex", flexDirection: "column", gap: "14px" }}>
              <span style={{ fontSize: "13px", fontWeight: "700", letterSpacing: "1.5px", textTransform: "uppercase", color: "#1E7F78" }}>{t("pourLesDonateurs")}</span>
              <span style={{ fontFamily: "'Nunito Sans', sans-serif", fontWeight: "900", fontSize: "64px", lineHeight: "1.05", color: "#32327D" }}>{t("donnerNeVousCoute")}</span>
            </div>
            <div style={{ background: "#FFFFFF", border: "1px solid #ECEAF5", borderRadius: "24px", padding: "36px", display: "flex", alignItems: "center", justifyContent: "center", gap: "24px", flexWrap: "wrap", textAlign: "center" }}>
              <div style={{ display: "flex", flexDirection: "column", gap: "4px" }}>
                <span style={{ fontSize: "14px", fontWeight: "600", color: "#4A4A66" }}>{t("vousDonnez")}</span>
                <span style={{ fontFamily: "'Nunito Sans', sans-serif", fontWeight: "900", fontSize: "52px" }}>
                  100 €
                </span>
              </div>
              <svg width="44" height="44" viewBox="0 0 24 24" fill="none" stroke="#FF6B5B" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
                <path d="M5 12h14" />
                <path d="M13 6l6 6-6 6" />
              </svg>
              <div style={{ display: "flex", flexDirection: "column", gap: "4px" }}>
                <span style={{ fontSize: "14px", fontWeight: "600", color: "#4A4A66" }}>{t("associationRecoit")}</span>
                <span style={{ fontFamily: "'Nunito Sans', sans-serif", fontWeight: "900", fontSize: "52px", color: "#1E7F78" }}>
                  100 €
                </span>
              </div>
            </div>
          </div>
          <div ref={watch} style={{ display: "grid", gridTemplateColumns: "repeat(auto-fit, minmax(min(100%, 420px), 1fr))", gap: "20px" }}>
            <div style={{ opacity: `${d0.op}`, transform: `translateY(${d0.ty}px)`, transition: "opacity 0.5s ease, transform 0.5s ease", background: "#FFFFFF", border: "1px solid #ECEAF5", borderRadius: "20px", padding: "28px", display: "flex", flexDirection: "column", gap: "12px" }}>
              <span aria-hidden="true" style={{ width: "52px", height: "52px", borderRadius: "14px", background: "rgba(78,205,196,0.16)", display: "flex", alignItems: "center", justifyContent: "center" }}>
                <svg width="26" height="26" viewBox="0 0 24 24" fill="none" stroke="#1E7F78" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round">
                  <path d="M12 21s-7-4.5-9.5-9A5.5 5.5 0 0 1 12 6a5.5 5.5 0 0 1 9.5 6c-2.5 4.5-9.5 9-9.5 9z" />
                </svg>
              </span>
              <strong style={{ fontFamily: "'Nunito Sans', sans-serif", fontSize: "22px" }}>{t("toutVotreDon")}</strong>
              <span style={{ fontSize: "16px", lineHeight: "1.55", color: "#4A4A66" }}>{t("vaAssociationVotreRecu")}</span>
            </div>
            <div style={{ opacity: `${d1.op}`, transform: `translateY(${d1.ty}px)`, transition: "opacity 0.5s ease, transform 0.5s ease", background: "#FFFFFF", border: "1px solid #ECEAF5", borderRadius: "20px", padding: "28px", display: "flex", flexDirection: "column", gap: "12px" }}>
              <span aria-hidden="true" style={{ width: "52px", height: "52px", borderRadius: "14px", background: "rgba(255,179,71,0.18)", display: "flex", alignItems: "center", justifyContent: "center" }}>
                <svg width="26" height="26" viewBox="0 0 24 24" fill="none" stroke="#A86A00" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round">
                  <circle cx="12" cy="12" r="9" />
                  <path d="M12 8v8M8 12h8" />
                </svg>
              </span>
              <strong style={{ fontFamily: "'Nunito Sans', sans-serif", fontSize: "22px" }}>{t("uneContributionFacultative")}</strong>
              <span style={{ fontSize: "16px", lineHeight: "1.55", color: "#4A4A66" }}>{t("auPaiementHelloassoVous")}</span>
            </div>
          </div>
        </div>
      </section>
    </>
  );
}
