'use client';

import { Fragment, useEffect, useState } from 'react';
import { useTranslations } from 'next-intl';

/** Interval between two steps of the journey, as in the mockup. */
const STEP_MS = 2200;
const DOT_COLORS = ['#4ECDC4', '#32327D', '#FF6B5B', '#FFB347'];
/** Spending split of the traceability panel (labels come from the messages). */
const SEGMENTS = [
  { pct: 70, color: '#4ECDC4', text: '#1E9E95' },
  { pct: 30, color: '#FF6B5B', text: '#D9473A' },
];
type Panel = '' | 'verify' | 'pays' | 'bilan';

/**
 * State of the journey, ported from the mockup's `Page_donateurs` component: the steps light up
 * one after the other every {@link STEP_MS} ms (5 positions); each step can open a detail panel
 * (verify the donation, traceability ring, closing report), one at a time.
 */
function useDonorJourney(t: ReturnType<typeof useTranslations>) {
  const [step, setStep] = useState(0);
  const [panel, setPanel] = useState<Panel>('');
  const [showWhat, setShowWhat] = useState(false);
  const [showTech, setShowTech] = useState(false);
  const [hover, setHover] = useState(-1);

  useEffect(() => {
    const id = setInterval(() => setStep((s) => (s + 1) % 5), STEP_MS);
    return () => clearInterval(id);
  }, []);

  const st = (i: number) => {
    const active = step === i;
    const reached = step >= i;
    return {
      border: active ? DOT_COLORS[i] : '#E6E4F4',
      bg: reached ? '#FFFFFF' : '#FCF8F1',
      shadow: active ? '0 12px 28px rgba(23,23,68,0.12)' : 'none',
      title: reached ? '#171744' : '#4A4A66',
      dot: reached ? DOT_COLORS[i] : '#E6E4F4',
      dotText: reached ? (i === 1 ? '#FFFFFF' : '#171744') : '#4A4A66',
    };
  };
  const open = (p: Panel) => () => {
    setPanel((current) => (current === p ? '' : p));
    setShowWhat(false);
    setShowTech(false);
    setHover(-1);
  };
  const labels = t.raw('data.segments') as { label: string; short: string }[];
  const tsegs = SEGMENTS.map((s, i) => ({ ...s, ...labels[i] }));
  let acc = 0;
  const ring = tsegs.map((s, i) => {
    const len = Math.round(s.pct * 4.02);
    const r = { len, off: -acc, w: hover === i ? 36 : 30, op: hover === -1 || hover === i ? 1 : 0.4, enter: () => setHover(i) };
    acc += len;
    return r;
  });

  return {
    showPanel: panel !== '',
    isVerify: panel === 'verify', isPays: panel === 'pays', isBilan: panel === 'bilan',
    panelTitle: panel ? t(`data.titles.${panel}`) : '',
    openVerify: open('verify'), openPays: open('pays'), openBilan: open('bilan'),
    closePanel: () => setPanel(''),
    showWhat, showTech,
    whatMark: showWhat ? '−' : '+', techMark: showTech ? '−' : '+',
    toggleWhat: () => setShowWhat((v) => !v),
    toggleTech: () => setShowTech((v) => !v),
    ts0: ring[0], ts1: ring[1],
    tc: hover >= 0 ? { pct: `${tsegs[hover].pct}%`, label: tsegs[hover].short, color: tsegs[hover].text } : { pct: '100%', label: t('data.used'), color: '#1E9E95' },
    tLegend: tsegs.map((s, i) => ({ label: s.label, color: s.color, pct: s.pct, bg: hover === i ? '#F1EFFA' : 'transparent', enter: () => setHover(i) })),
    tLeave: () => setHover(-1),
    st0: st(0), st1: st(1), st2: st(2), st3: st(3),
  };
}

/**
 * Donors « Le parcours de votre don » section: animated step-by-step journey with detail panels.
 */
export function DonorJourney() {
  const t = useTranslations('donors.journey');
  const { closePanel, isBilan, isPays, isVerify, openBilan, openPays, openVerify, panelTitle, showPanel, showTech, showWhat, st0, st1, st2, st3, tLeave, tLegend, tc, techMark, toggleTech, toggleWhat, ts0, ts1, whatMark } = useDonorJourney(t);

  return (
    <section style={{ background: "#FDF8F0", padding: "96px 48px" }} id="donateurs-parcours">
      <div style={{ maxWidth: "1240px", margin: "0 auto", display: "flex", flexDirection: "column", gap: "40px" }}>
        <div style={{ display: "flex", flexDirection: "column", gap: "12px", alignItems: "center", textAlign: "center" }}>
          <span style={{ fontSize: "13px", fontWeight: "700", letterSpacing: "1.5px", textTransform: "uppercase", color: "#1E7F78" }}>{t("apresVotreDon")}</span>
          <h2 style={{ margin: "0", fontFamily: "'Nunito Sans', sans-serif", fontWeight: "900", fontSize: "40px" }}>{t("leParcoursDeVotre")}</h2>
          <p style={{ margin: "0", fontSize: "17px", color: "#4A4A66" }}>{t("etapeParEtapeJusqu")}</p>
        </div>
        <div style={{ background: "#FFFFFF", border: "1px solid #ECEAF5", borderRadius: "24px", padding: "32px", display: "flex", flexDirection: "column", gap: "28px" }}>
          <div style={{ display: "flex", alignItems: "center", justifyContent: "space-between", gap: "16px", flexWrap: "wrap" }}>
            <div style={{ display: "flex", flexDirection: "column", gap: "4px" }}>
              <div style={{ display: "flex", alignItems: "center", gap: "10px", flexWrap: "wrap" }}>
                <strong style={{ fontFamily: "'Nunito Sans', sans-serif", fontSize: "22px" }}>{t("pourLaRenovationDe")}</strong>
                <span style={{ fontSize: "12px", fontWeight: "600", color: "#4A4A66", background: "#E6E4F4", borderRadius: "9999px", padding: "4px 10px" }}>{t("exemple")}</span>
              </div>
              <span style={{ fontSize: "14px", color: "#4A4A66" }}>{t("lesPetitsEcoliersMars")}</span>
            </div>
            <button type="button" onClick={openPays} style={{ minHeight: "44px", padding: "0 18px", borderRadius: "9999px", border: "1.5px solid #32327D", background: "#FFFFFF", color: "#32327D", fontFamily: "'Nunito Sans', sans-serif", fontWeight: "800", fontSize: "14px", cursor: "pointer" }}>{t("voirLaTracabilite")}</button>
          </div>
          <div style={{ display: "grid", gridTemplateColumns: "repeat(auto-fit, minmax(min(100%, 230px), 1fr))", gap: "16px" }}>
            <div style={{ textAlign: "left", borderRadius: "18px", border: `1.5px solid ${st0.border}`, background: `${st0.bg}`, boxShadow: `${st0.shadow}`, padding: "20px", display: "flex", flexDirection: "column", gap: "8px", color: "#171744", transition: "all 0.5s ease" }}>
              <span style={{ display: "flex", alignItems: "center", gap: "10px" }}>
                <span style={{ width: "30px", height: "30px", flex: "none", borderRadius: "50%", background: `${st0.dot}`, color: `${st0.dotText}`, fontFamily: "'Nunito Sans', sans-serif", fontWeight: "900", fontSize: "14px", display: "flex", alignItems: "center", justifyContent: "center", transition: "all 0.4s ease" }}>
                  1
                </span>
                <span style={{ fontSize: "12px", fontWeight: "700", color: "#1E7F78" }}>{t("mars")}</span>
              </span>
              <strong style={{ fontFamily: "'Nunito Sans', sans-serif", fontSize: "17px", color: `${st0.title}` }}>{t("donRecu")}</strong>
              <span style={{ fontSize: "14px", lineHeight: "1.55", color: "#4A4A66" }}>{t("votreDonDeEst")}</span>
            </div>
            <div style={{ textAlign: "left", borderRadius: "18px", border: `1.5px solid ${st1.border}`, background: `${st1.bg}`, boxShadow: `${st1.shadow}`, padding: "20px", display: "flex", flexDirection: "column", gap: "8px", color: "#171744", transition: "all 0.5s ease" }}>
              <span style={{ display: "flex", alignItems: "center", gap: "10px" }}>
                <span style={{ width: "30px", height: "30px", flex: "none", borderRadius: "50%", background: `${st1.dot}`, color: `${st1.dotText}`, fontFamily: "'Nunito Sans', sans-serif", fontWeight: "900", fontSize: "14px", display: "flex", alignItems: "center", justifyContent: "center", transition: "all 0.4s ease" }}>
                  2
                </span>
                <span style={{ fontSize: "12px", fontWeight: "700", color: "#1E7F78" }}>{t("mars2")}</span>
              </span>
              <strong style={{ fontFamily: "'Nunito Sans', sans-serif", fontSize: "17px", color: `${st1.title}` }}>{t("inscritAuRegistrePublic")}</strong>
              <span style={{ fontSize: "14px", lineHeight: "1.55", color: "#4A4A66" }}>{t("personnePasMemeCommonlink")}</span>
              <button type="button" onClick={openVerify} aria-expanded={isVerify} style={{ alignSelf: "flex-start", minHeight: "44px", padding: "0", border: "none", background: "transparent", fontFamily: "Inter, sans-serif", fontSize: "14px", fontWeight: "700", color: "#32327D", textDecoration: "underline", cursor: "pointer" }}>{t("verifierCeDon")}</button>
            </div>
            <div style={{ textAlign: "left", borderRadius: "18px", border: `1.5px solid ${st2.border}`, background: `${st2.bg}`, boxShadow: `${st2.shadow}`, padding: "20px", display: "flex", flexDirection: "column", gap: "8px", color: "#171744", transition: "all 0.5s ease" }}>
              <span style={{ display: "flex", alignItems: "center", gap: "10px" }}>
                <span style={{ width: "30px", height: "30px", flex: "none", borderRadius: "50%", background: `${st2.dot}`, color: `${st2.dotText}`, fontFamily: "'Nunito Sans', sans-serif", fontWeight: "900", fontSize: "14px", display: "flex", alignItems: "center", justifyContent: "center", transition: "all 0.4s ease" }}>
                  3
                </span>
                <span style={{ fontSize: "12px", fontWeight: "700", color: "#1E7F78" }}>{t("dernierPaiementLeAvril")}</span>
              </span>
              <strong style={{ fontFamily: "'Nunito Sans', sans-serif", fontSize: "17px", color: `${st2.title}` }}>{t("utilisePourDesDepenses")}</strong>
              <span style={{ fontSize: "14px", lineHeight: "1.55", color: "#4A4A66" }}>{t("vosOntFinanceDes")}</span>
              <button type="button" onClick={openPays} aria-expanded={isPays} style={{ alignSelf: "flex-start", minHeight: "44px", padding: "0", border: "none", background: "transparent", fontFamily: "Inter, sans-serif", fontSize: "14px", fontWeight: "700", color: "#32327D", textDecoration: "underline", cursor: "pointer" }}>{t("voirLesDepensesFinancees")}</button>
            </div>
            <div style={{ textAlign: "left", borderRadius: "18px", border: `1.5px solid ${st3.border}`, background: `${st3.bg}`, boxShadow: `${st3.shadow}`, padding: "20px", display: "flex", flexDirection: "column", gap: "8px", color: "#171744", transition: "all 0.5s ease" }}>
              <span style={{ display: "flex", alignItems: "center", gap: "10px" }}>
                <span style={{ width: "30px", height: "30px", flex: "none", borderRadius: "50%", background: `${st3.dot}`, color: `${st3.dotText}`, fontFamily: "'Nunito Sans', sans-serif", fontWeight: "900", fontSize: "14px", display: "flex", alignItems: "center", justifyContent: "center", transition: "all 0.4s ease" }}>
                  4
                </span>
                <span style={{ fontSize: "12px", fontWeight: "700", color: "#1E7F78" }}>{t("laCloture")}</span>
              </span>
              <strong style={{ fontFamily: "'Nunito Sans', sans-serif", fontSize: "17px", color: `${st3.title}` }}>{t("bilanImpact")}</strong>
              <span style={{ fontSize: "14px", lineHeight: "1.55", color: "#4A4A66" }}>{t("associationPublieSonBilan")}</span>
              <button type="button" onClick={openBilan} aria-expanded={isBilan} style={{ alignSelf: "flex-start", minHeight: "44px", padding: "0", border: "none", background: "transparent", fontFamily: "Inter, sans-serif", fontSize: "14px", fontWeight: "700", color: "#32327D", textDecoration: "underline", cursor: "pointer" }}>{t("voirUnExempleDe")}</button>
            </div>
          </div>
          {showPanel && (
          <>
            <div style={{ borderTop: "1px solid #ECEAF5", paddingTop: "26px", display: "flex", flexDirection: "column", gap: "20px" }}>
              <div style={{ display: "flex", alignItems: "center", justifyContent: "space-between", gap: "12px", flexWrap: "wrap" }}>
                <strong style={{ fontFamily: "'Nunito Sans', sans-serif", fontWeight: "900", fontSize: "22px" }}>
                  {panelTitle}
                </strong>
                <button type="button" onClick={closePanel} aria-label={t("fermer")} style={{ width: "44px", height: "44px", borderRadius: "12px", border: "none", background: "#FDF6EC", color: "#171744", fontSize: "20px", cursor: "pointer" }}>
                  ×
                </button>
              </div>
              {isVerify && (
              <>
                <div style={{ display: "flex", flexDirection: "column", gap: "14px", maxWidth: "760px" }}>
                  <div style={{ display: "flex", alignItems: "center", gap: "16px", padding: "18px 20px", borderRadius: "18px", background: "rgba(78,205,196,0.12)" }}>
                    <span aria-hidden="true" style={{ width: "48px", height: "48px", flex: "none", borderRadius: "50%", background: "#4ECDC4", display: "flex", alignItems: "center", justifyContent: "center" }}>
                      <svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="#171744" strokeWidth="2.6" strokeLinecap="round" strokeLinejoin="round">
                        <path d="M5 12l5 5L19 7" />
                      </svg>
                    </span>
                    <span style={{ display: "flex", flexDirection: "column", gap: "2px" }}>
                      <strong style={{ fontSize: "17px" }}>{t("inscritAuRegistrePublic2")}</strong>
                      <span style={{ fontSize: "14px", color: "#4A4A66" }}>{t("votreDonLaisseUne")}</span>
                    </span>
                  </div>
                  <button type="button" onClick={toggleWhat} aria-expanded={showWhat} style={{ minHeight: "48px", display: "flex", alignItems: "center", justifyContent: "space-between", padding: "0 18px", border: "1px solid #ECEAF5", borderRadius: "14px", background: "#FFFFFF", fontFamily: "'Nunito Sans', sans-serif", fontWeight: "800", fontSize: "15px", color: "#171744", cursor: "pointer" }}>{t.rich("ceQueCelaMontre", { t0: (chunks) => <span aria-hidden="true">{chunks}</span>, whatMark })}</button>
                  {showWhat && (
                  <>
                    <div style={{ padding: "4px 18px 8px", display: "flex", flexDirection: "column", gap: "10px", fontSize: "15px", lineHeight: "1.55" }}>
                      <span>{t("leMontantLaDate")}</span>
                      <span style={{ color: "#4A4A66" }}>{t.rich("valeursLuesDirectementDans", { t0: (chunks) => <span style={{ fontFamily: "ui-monospace, Menlo, monospace", color: "#32327D" }}>{chunks}</span> })}</span>
                      <span style={{ color: "#4A4A66" }}>{t("leRegistreNeDit")}</span>
                    </div>
                  </>
                  )}
                  <button type="button" onClick={toggleTech} aria-expanded={showTech} style={{ minHeight: "48px", display: "flex", alignItems: "center", justifyContent: "space-between", padding: "0 18px", border: "1px solid #ECEAF5", borderRadius: "14px", background: "#FFFFFF", fontFamily: "'Nunito Sans', sans-serif", fontWeight: "800", fontSize: "15px", color: "#171744", cursor: "pointer" }}>{t.rich("detailsTechniques", { t0: (chunks) => <span aria-hidden="true">{chunks}</span>, techMark })}</button>
                  {showTech && (
                  <>
                    <div style={{ padding: "4px 18px 8px", display: "grid", gridTemplateColumns: "repeat(auto-fit, minmax(min(100%, 280px), 1fr))", gap: "10px 24px", fontSize: "14px" }}>
                      <span style={{ display: "flex", justifyContent: "space-between", gap: "12px" }}>
                        <span style={{ color: "#4A4A66" }}>{t("referenceInscription")}</span>
                        <span style={{ fontFamily: "ui-monospace, Menlo, monospace", color: "#32327D" }}>
                          0x7f3a…9c21
                        </span>
                      </span>
                      <span style={{ display: "flex", justifyContent: "space-between", gap: "12px" }}>
                        <span style={{ color: "#4A4A66" }}>{t("bloc")}</span>
                        <span style={{ fontFamily: "ui-monospace, Menlo, monospace", color: "#32327D" }}>
                          41 238 554
                        </span>
                      </span>
                      <span style={{ display: "flex", justifyContent: "space-between", gap: "12px" }}>
                        <span style={{ color: "#4A4A66" }}>{t("reseau")}</span>
                        <span style={{ fontFamily: "ui-monospace, Menlo, monospace", color: "#32327D" }}>{t("gnosis")}</span>
                      </span>
                      <span style={{ display: "flex", justifyContent: "space-between", gap: "12px" }}>
                        <span style={{ color: "#4A4A66" }}>{t("montantEnCentimes")}</span>
                        <span style={{ fontFamily: "ui-monospace, Menlo, monospace", color: "#32327D" }}>
                          5000
                        </span>
                      </span>
                      <span style={{ display: "flex", justifyContent: "space-between", gap: "12px" }}>
                        <span style={{ color: "#4A4A66" }}>{t("empreinteDuRecu")}</span>
                        <span style={{ fontFamily: "ui-monospace, Menlo, monospace", color: "#32327D" }}>
                          0x2b91…04fd
                        </span>
                      </span>
                      <span style={{ display: "flex", justifyContent: "space-between", gap: "12px" }}>
                        <span style={{ color: "#4A4A66" }}>{t("explorateurPublic")}</span>
                        <span style={{ color: "#4A4A66" }}>{t("desactiveDansLaDemonstration")}</span>
                      </span>
                    </div>
                  </>
                  )}
                </div>
              </>
              )}
              {isPays && (
              <>
                <div style={{ display: "flex", flexDirection: "column", gap: "18px", maxWidth: "760px" }}>
                  <div style={{ display: "grid", gridTemplateColumns: "repeat(2, minmax(0, 1fr))", gap: "16px" }}>
                    <div style={{ display: "flex", flexDirection: "column", gap: "2px" }}>
                      <span style={{ fontSize: "14px", color: "#4A4A66" }}>{t("montantDejaUtilise")}</span>
                      <strong style={{ fontSize: "17px", whiteSpace: "nowrap" }}>
                        50,00 €
                      </strong>
                    </div>
                    <div style={{ display: "flex", flexDirection: "column", gap: "2px" }}>
                      <span style={{ fontSize: "14px", color: "#4A4A66" }}>{t("montantPasEncoreUtilise")}</span>
                      <strong style={{ fontSize: "17px", whiteSpace: "nowrap" }}>
                        0,00 €
                      </strong>
                    </div>
                  </div>
                  <div style={{ display: "flex", alignItems: "center", gap: "24px", flexWrap: "wrap" }}>
                    <svg width="160" height="160" viewBox="0 0 170 170" role="img" aria-label={t("repartitionDuDonKits")} style={{ flex: "none", overflow: "visible" }}>
                      <circle cx="85" cy="85" r="64" fill="none" stroke="#ECEAF5" strokeWidth="30" />
                      <circle cx="85" cy="85" r="64" fill="none" stroke="#4ECDC4" strokeWidth={ts0.w} strokeDasharray={`${ts0.len} 402`} strokeDashoffset={ts0.off} opacity={ts0.op} transform="rotate(-90 85 85)" onMouseEnter={ts0.enter} onMouseLeave={tLeave} style={{ transition: "all 0.5s ease", cursor: "pointer" }} />
                      <circle cx="85" cy="85" r="64" fill="none" stroke="#FF6B5B" strokeWidth={ts1.w} strokeDasharray={`${ts1.len} 402`} strokeDashoffset={ts1.off} opacity={ts1.op} transform="rotate(-90 85 85)" onMouseEnter={ts1.enter} onMouseLeave={tLeave} style={{ transition: "all 0.5s ease", cursor: "pointer" }} />
                      <text x="85" y="84" textAnchor="middle" fontFamily="Nunito Sans, sans-serif" fontWeight="900" fontSize="26" fill={tc.color}>
                        {tc.pct}
                      </text>
                      <text x="85" y="104" textAnchor="middle" fontFamily="Inter, sans-serif" fontSize="11" fill="#4A4A66">
                        {tc.label}
                      </text>
                    </svg>
                    <div style={{ flex: "1", minWidth: "210px", display: "flex", flexDirection: "column", gap: "6px" }}>
                      {tLegend.map((g, gIndex) => (
                      <Fragment key={gIndex}>
                        <button type="button" onMouseEnter={g.enter} onMouseLeave={tLeave} onFocus={g.enter} onBlur={tLeave} onClick={g.enter} style={{ minHeight: "44px", display: "flex", alignItems: "center", gap: "10px", padding: "0 12px", border: "none", borderRadius: "12px", background: `${g.bg}`, fontFamily: "Inter, sans-serif", fontSize: "14px", color: "#171744", textAlign: "left", cursor: "pointer", transition: "background 0.3s ease" }}>
                          <span style={{ width: "12px", height: "12px", flex: "none", borderRadius: "50%", background: `${g.color}` }} />
                          <span style={{ flex: "1" }}>
                            {g.label}
                          </span>
                          <strong style={{ whiteSpace: "nowrap" }}>
                            {`${g.pct} %`}
                          </strong>
                        </button>
                      </Fragment>
                      ))}
                    </div>
                  </div>
                  <div style={{ display: "flex", flexDirection: "column", gap: "10px" }}>
                    <strong style={{ fontFamily: "'Nunito Sans', sans-serif", fontWeight: "800", fontSize: "16px" }}>{t("depensesFinanceesParCe")}</strong>
                    <div style={{ display: "flex", justifyContent: "space-between", alignItems: "baseline", gap: "12px", fontSize: "15px" }}>
                      <span>{t.rich("achatDeKitsScolaires", { t0: (chunks) => <span style={{ color: "#4A4A66" }}>{chunks}</span> })}</span>
                      <strong style={{ whiteSpace: "nowrap" }}>
                        35,00 €
                      </strong>
                    </div>
                    <div style={{ display: "flex", justifyContent: "space-between", alignItems: "baseline", gap: "12px", fontSize: "15px" }}>
                      <span>{t.rich("transportDuMaterielAvril", { t0: (chunks) => <span style={{ color: "#4A4A66" }}>{chunks}</span> })}</span>
                      <strong style={{ whiteSpace: "nowrap" }}>
                        15,00 €
                      </strong>
                    </div>
                  </div>
                </div>
              </>
              )}
              {isBilan && (
              <>
                <div style={{ alignSelf: "center", width: "100%", maxWidth: "760px", background: "#FFFFFF", border: "1px solid #ECEAF5", borderRadius: "6px", boxShadow: "0 18px 50px rgba(23,23,68,0.12)", padding: "40px 44px", display: "flex", flexDirection: "column", gap: "22px" }}>
                  <div style={{ display: "flex", alignItems: "flex-start", justifyContent: "space-between", gap: "16px", flexWrap: "wrap", paddingBottom: "18px", borderBottom: "2px solid #4ECDC4" }}>
                    <span style={{ display: "flex", flexDirection: "column", gap: "4px" }}>
                      <span style={{ fontSize: "12px", fontWeight: "700", letterSpacing: "1.5px", textTransform: "uppercase", color: "#1E7F78" }}>{t("bilanDeCloture")}</span>
                      <strong style={{ fontFamily: "'Nunito Sans', sans-serif", fontWeight: "900", fontSize: "24px", lineHeight: "1.2" }}>{t("renovationDeEcoleDe")}</strong>
                      <span style={{ fontSize: "14px", color: "#4A4A66" }}>{t("lesPetitsEcoliersCampagne")}</span>
                    </span>
                    <span style={{ fontFamily: "'Nunito Sans', sans-serif", fontWeight: "900", fontSize: "18px", color: "#171744" }}>{t.rich("commonLink", { t0: (chunks) => <span style={{ color: "#2FB3AA" }}>{chunks}</span> })}</span>
                  </div>
                  <div style={{ display: "grid", gridTemplateColumns: "repeat(3, minmax(0, 1fr))", gap: "12px" }}>
                    <div style={{ background: "#FCF8F1", borderRadius: "14px", padding: "16px", display: "flex", flexDirection: "column", gap: "4px" }}>
                      <strong style={{ fontFamily: "'Nunito Sans', sans-serif", fontWeight: "900", fontSize: "24px", whiteSpace: "nowrap" }}>
                        6 000 €
                      </strong>
                      <span style={{ fontSize: "13px", color: "#4A4A66" }}>{t("collectes")}</span>
                    </div>
                    <div style={{ background: "#FCF8F1", borderRadius: "14px", padding: "16px", display: "flex", flexDirection: "column", gap: "4px" }}>
                      <strong style={{ fontFamily: "'Nunito Sans', sans-serif", fontWeight: "900", fontSize: "24px", color: "#1E9E95", whiteSpace: "nowrap" }}>
                        5 820 €
                      </strong>
                      <span style={{ fontSize: "13px", color: "#4A4A66" }}>{t("payesDepuisCommonlink")}</span>
                    </div>
                    <div style={{ background: "#FCF8F1", borderRadius: "14px", padding: "16px", display: "flex", flexDirection: "column", gap: "4px" }}>
                      <strong style={{ fontFamily: "'Nunito Sans', sans-serif", fontWeight: "900", fontSize: "24px", color: "#A86A00", whiteSpace: "nowrap" }}>
                        180 €
                      </strong>
                      <span style={{ fontSize: "13px", color: "#4A4A66" }}>{t("resteDisponible")}</span>
                    </div>
                  </div>
                  <div style={{ display: "grid", gridTemplateColumns: "repeat(3, minmax(0, 1fr))", gap: "12px", textAlign: "center" }}>
                    <div style={{ display: "flex", flexDirection: "column", gap: "2px" }}>
                      <strong style={{ fontFamily: "'Nunito Sans', sans-serif", fontWeight: "900", fontSize: "30px", color: "#32327D" }}>
                        320
                      </strong>
                      <span style={{ fontSize: "13px", color: "#4A4A66" }}>{t("elevesAccueillis")}</span>
                    </div>
                    <div style={{ display: "flex", flexDirection: "column", gap: "2px" }}>
                      <strong style={{ fontFamily: "'Nunito Sans', sans-serif", fontWeight: "900", fontSize: "30px", color: "#32327D" }}>
                        4
                      </strong>
                      <span style={{ fontSize: "13px", color: "#4A4A66" }}>{t("sallesRenovees")}</span>
                    </div>
                    <div style={{ display: "flex", flexDirection: "column", gap: "2px" }}>
                      <strong style={{ fontFamily: "'Nunito Sans', sans-serif", fontWeight: "900", fontSize: "30px", color: "#32327D" }}>
                        250
                      </strong>
                      <span style={{ fontSize: "13px", color: "#4A4A66" }}>{t("kitsDistribues")}</span>
                    </div>
                  </div>
                  <div style={{ display: "flex", flexDirection: "column", gap: "8px" }}>
                    <strong style={{ fontFamily: "'Nunito Sans', sans-serif", fontWeight: "800", fontSize: "16px" }}>{t("recitDeAssociation")}</strong>
                    <span style={{ fontSize: "15px", lineHeight: "1.65", color: "#171744" }}>{t("lesQuatreSallesDe")}</span>
                    <span style={{ fontSize: "15px", fontStyle: "italic", color: "#4A4A66", borderLeft: "3px solid #FFB347", paddingLeft: "14px" }}>{t("lesEnfantsOntEnfin")}</span>
                  </div>
                  <div style={{ display: "flex", flexDirection: "column", gap: "8px" }}>
                    <strong style={{ fontFamily: "'Nunito Sans', sans-serif", fontWeight: "800", fontSize: "16px" }}>{t("prevuEtRealise")}</strong>
                    <div style={{ display: "grid", gridTemplateColumns: "minmax(0, 2fr) repeat(2, minmax(0, 1fr))", gap: "8px 12px", fontSize: "14px" }}>
                      <span style={{ fontWeight: "700", color: "#4A4A66" }}>{t("poste")}</span>
                      <span style={{ fontWeight: "700", color: "#4A4A66", textAlign: "right" }}>{t("prevu")}</span>
                      <span style={{ fontWeight: "700", color: "#4A4A66", textAlign: "right" }}>{t("realise")}</span>
                      <span>{t("travauxDeRenovation")}</span>
                      <span style={{ textAlign: "right" }}>
                        3 200 €
                      </span>
                      <span style={{ textAlign: "right", fontWeight: "700" }}>
                        3 150 €
                      </span>
                      <span>{t("kitsScolaires")}</span>
                      <span style={{ textAlign: "right" }}>
                        2 000 €
                      </span>
                      <span style={{ textAlign: "right", fontWeight: "700" }}>
                        1 980 €
                      </span>
                      <span>{t("transportDuMateriel")}</span>
                      <span style={{ textAlign: "right" }}>
                        800 €
                      </span>
                      <span style={{ textAlign: "right", fontWeight: "700" }}>
                        690 €
                      </span>
                    </div>
                  </div>
                  <div style={{ display: "flex", alignItems: "center", justifyContent: "space-between", gap: "12px", flexWrap: "wrap", paddingTop: "14px", borderTop: "1px solid #ECEAF5" }}>
                    <span style={{ fontSize: "12px", color: "#4A4A66" }}>{t("bilanEtabliParAssociation")}</span>
                    <button type="button" style={{ minHeight: "40px", padding: "0 16px", borderRadius: "9999px", border: "none", background: "#4ECDC4", color: "#171744", fontFamily: "'Nunito Sans', sans-serif", fontWeight: "800", fontSize: "14px", cursor: "pointer" }}>{t("telechargerLePdf")}</button>
                  </div>
                </div>
              </>
              )}
            </div>
          </>
          )}
        </div>
      </div>
    </section>
  );
}
