import { Fragment } from 'react';
import { useTranslations } from 'next-intl';
import type { Reveal } from './Tarifs';

type TarifsAssoProps = {
  /** Fees CommonLink does not charge, each revealed then crossed out in turn. */
  fees: (Reveal & { label: string })[];
  info0: Reveal;
  info1: Reveal;
  /** Callback ref: starts the reveal when the block scrolls into view. */
  watch: (el: Element | null) => void;
};

/**
 * Pricing for associations: crossed-out fees, the only commission.
 */
export function TarifsAsso({ fees, info0, info1, watch }: TarifsAssoProps) {
  const t = useTranslations('tarifs.asso');

  return (
    <>
      <section style={{ background: "#FDF8F0", padding: "80px 48px 96px" }}>
        <div style={{ maxWidth: "1240px", margin: "0 auto", display: "flex", flexDirection: "column", gap: "56px" }}>
          <div ref={watch} style={{ display: "flex", flexDirection: "column", gap: "24px" }}>
            <div style={{ display: "flex", flexDirection: "column", gap: "10px" }}>
              <span style={{ fontSize: "13px", fontWeight: "700", letterSpacing: "1.5px", textTransform: "uppercase", color: "#1E7F78" }}>{t("pourLesAssociations")}</span>
              <h2 style={{ margin: "0", fontFamily: "'Nunito Sans', sans-serif", fontWeight: "900", fontSize: "48px", lineHeight: "1.1" }}>{t("ceQueVousNe")}</h2>
            </div>
            <div style={{ display: "grid", gridTemplateColumns: "repeat(auto-fit, minmax(min(100%, 340px), 1fr))", gap: "16px" }}>
              {fees.map((f, fIndex) => (
              <Fragment key={fIndex}>
                <div style={{ opacity: `${f.op}`, transform: `translateY(${f.ty}px)`, transition: "opacity 0.5s ease, transform 0.5s ease", background: "#FFFFFF", border: "1px solid #ECEAF5", borderRadius: "20px", padding: "24px 26px", display: "flex", alignItems: "center", justifyContent: "space-between", gap: "12px" }}>
                  <span style={{ position: "relative", display: "inline-block", fontSize: "20px", fontWeight: "700", whiteSpace: "nowrap" }}>
                    {f.label}
                    <span aria-hidden="true" style={{ position: "absolute", left: "-2px", top: "52%", height: "3px", width: `${f.strike}%`, background: "#FF6B5B", borderRadius: "2px", transition: "width 0.45s ease 0.35s" }} />
                  </span>
                  <span style={{ flex: "none", fontFamily: "'Nunito Sans', sans-serif", fontWeight: "900", fontSize: "20px", color: "#1E7F78", background: "rgba(78,205,196,0.14)", borderRadius: "9999px", padding: "6px 16px" }}>
                    0 €
                  </span>
                </div>
              </Fragment>
              ))}
            </div>
          </div>
          <div style={{ display: "grid", gridTemplateColumns: "repeat(auto-fit, minmax(min(100%, 440px), 1fr))", gap: "24px", alignItems: "stretch" }}>
            <div style={{ background: "#32327D", color: "#FFFFFF", borderRadius: "24px", padding: "32px", display: "flex", flexDirection: "column", gap: "10px" }}>
              <span style={{ fontSize: "13px", fontWeight: "700", letterSpacing: "1.5px", textTransform: "uppercase", color: "rgba(255,255,255,0.78)" }}>{t("ceQuiArriveSur")}</span>
              <span style={{ fontSize: "20px" }}>{t("surCollectes")}</span>
              <span style={{ fontFamily: "'Nunito Sans', sans-serif", fontWeight: "900", fontSize: "32px", lineHeight: "1.2", color: "#4ECDC4" }}>{t("arriventSurVotreCompte")}</span>
              <span style={{ fontSize: "16px", lineHeight: "1.6", color: "rgba(255,255,255,0.86)" }}>{t("laCommissionEstPrelevee")}</span>
            </div>
            <div style={{ background: "#FFFFFF", border: "1px solid #ECEAF5", borderRadius: "24px", padding: "32px", display: "flex", flexDirection: "column", gap: "10px" }}>
              <span style={{ fontSize: "13px", fontWeight: "700", letterSpacing: "1.5px", textTransform: "uppercase", color: "#4A4A66" }}>{t("notreSeuleCommission")}</span>
              <span style={{ fontFamily: "'Nunito Sans', sans-serif", fontWeight: "900", fontSize: "40px", lineHeight: "1.1", color: "#32327D" }}>{t.rich("htSurLesDons", { t0: (chunks) => <span style={{ fontSize: "18px", fontWeight: "700", color: "#4A4A66" }}>{chunks}</span> })}</span>
              <span style={{ fontSize: "16px", color: "#4A4A66" }}>{t("soitTtc")}</span>
              <span style={{ fontSize: "13px", lineHeight: "1.55", color: "#4A4A66" }}>{t("associationsEtFondsDe")}</span>
            </div>
          </div>
          <div style={{ display: "grid", gridTemplateColumns: "repeat(auto-fit, minmax(min(100%, 420px), 1fr))", gap: "20px" }}>
            <div style={{ opacity: `${info0.op}`, transform: `translateY(${info0.ty}px)`, transition: "opacity 0.5s ease, transform 0.5s ease", background: "#FFFFFF", border: "1px solid #ECEAF5", borderRadius: "20px", padding: "26px", display: "flex", gap: "16px", alignItems: "center" }}>
              <span aria-hidden="true" style={{ width: "48px", height: "48px", flex: "none", borderRadius: "14px", background: "rgba(255,179,71,0.18)", display: "flex", alignItems: "center", justifyContent: "center" }}>
                <svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="#A86A00" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round">
                  <rect x="5" y="11" width="14" height="10" rx="2" />
                  <path d="M8 11V8a4 4 0 0 1 7.5-2" />
                </svg>
              </span>
              <div style={{ display: "flex", flexDirection: "column", gap: "4px" }}>
                <strong style={{ fontFamily: "'Nunito Sans', sans-serif", fontSize: "19px" }}>{t("sansEngagement")}</strong>
                <span style={{ fontSize: "15px", lineHeight: "1.5", color: "#4A4A66" }}>{t("arretezQuandVousVoulez")}</span>
              </div>
            </div>
            <div style={{ opacity: `${info1.op}`, transform: `translateY(${info1.ty}px)`, transition: "opacity 0.5s ease, transform 0.5s ease", background: "#FFFFFF", border: "1px solid #ECEAF5", borderRadius: "20px", padding: "26px", display: "flex", gap: "16px", alignItems: "center" }}>
              <span aria-hidden="true" style={{ width: "48px", height: "48px", flex: "none", borderRadius: "14px", background: "rgba(78,205,196,0.16)", display: "flex", alignItems: "center", justifyContent: "center" }}>
                <svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="#1E7F78" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round">
                  <path d="M7 3h7l5 5v13H7z" />
                  <path d="M14 3v5h5" />
                  <path d="M10 13h5M10 17h5" />
                </svg>
              </span>
              <div style={{ display: "flex", flexDirection: "column", gap: "4px" }}>
                <strong style={{ fontFamily: "'Nunito Sans', sans-serif", fontSize: "19px" }}>{t("preleveeAutomatiquement")}</strong>
                <span style={{ fontSize: "15px", lineHeight: "1.5", color: "#4A4A66" }}>{t("trenteJoursOuvresApres")}</span>
              </div>
            </div>
          </div>
        </div>
      </section>
    </>
  );
}
