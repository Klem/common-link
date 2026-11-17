import { useTranslations } from 'next-intl';
import type { SwitchButton } from './Tarifs';

type TarifsHeroProps = { tAsso: SwitchButton; tDon: SwitchButton; pickAsso: () => void; pickDon: () => void };

/**
 * Pricing hero with the « Je collecte / Je donne » profile switch.
 */
export function TarifsHero({ tAsso, tDon, pickAsso, pickDon }: TarifsHeroProps) {
  const t = useTranslations('tarifs.hero');

  return (
    <section style={{ background: "#32327D", color: "#FFFFFF", padding: "80px 48px 72px" }}>
      <div style={{ maxWidth: "1240px", margin: "0 auto", display: "flex", flexDirection: "column", alignItems: "center", gap: "26px", textAlign: "center" }}>
        <span style={{ fontSize: "13px", fontWeight: "700", letterSpacing: "1.5px", textTransform: "uppercase", color: "#4ECDC4" }}>{t("tarifs")}</span>
        <h1 style={{ margin: "0", fontFamily: "'Nunito Sans', sans-serif", fontWeight: "900", fontSize: "56px", lineHeight: "1.08" }}>{t.rich("quiPaieQuoi", { t0: (chunks) => <span style={{ fontFamily: "Lora, serif", fontStyle: "italic", fontWeight: "500", color: "#4ECDC4" }}>{chunks}</span> })}</h1>
        <p style={{ margin: "0", fontSize: "19px", color: "rgba(255,255,255,0.82)" }}>{t("choisissezVotreProfil")}</p>
        <div role="group" aria-label={t("choisirVotreProfil")} style={{ display: "inline-flex", gap: "6px", padding: "6px", borderRadius: "9999px", background: "rgba(255,255,255,0.12)" }}>
          <button type="button" onClick={pickAsso} aria-pressed={tAsso.pressed} style={{ minHeight: "52px", padding: "0 28px", border: "none", borderRadius: "9999px", background: `${tAsso.bg}`, color: `${tAsso.color}`, fontFamily: "'Nunito Sans', sans-serif", fontWeight: "800", fontSize: "17px", cursor: "pointer", transition: "all 0.3s ease" }}>{t("jeCollecte")}</button>
          <button type="button" onClick={pickDon} aria-pressed={tDon.pressed} style={{ minHeight: "52px", padding: "0 28px", border: "none", borderRadius: "9999px", background: `${tDon.bg}`, color: `${tDon.color}`, fontFamily: "'Nunito Sans', sans-serif", fontWeight: "800", fontSize: "17px", cursor: "pointer", transition: "all 0.3s ease" }}>{t("jeDonne")}</button>
        </div>
      </div>
    </section>
  );
}
