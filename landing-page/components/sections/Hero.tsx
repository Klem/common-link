'use client';

import { Fragment, useCallback, useEffect, useRef, useState } from 'react';
import { useTranslations } from 'next-intl';
import { Link } from '@/i18n/navigation';
import { APP_URL } from '@/lib/constants';

/** Interval between two steps of the « Suivez un don » flow, as in the mockup. */
const STEP_MS = 2200;
const ACCENT = '#4ECDC4';
/** Spending split shown on the traceability ring (labels come from the messages). */
const SEGMENTS = [
  { pct: 70, color: '#4ECDC4', text: '#1E9E95' },
  { pct: 20, color: '#FF6B5B', text: '#D9473A' },
  { pct: 10, color: '#FFB347', text: '#A86A00' },
];

type SegmentLabel = { label: string; short: string };

/**
 * State of the hero demo card, ported from the mockup's `Page_accueil` component: the flow
 * advances one step every {@link STEP_MS} ms (5 positions, the coin stops on the 4th card), a
 * click on a card jumps to it, a click on the payment opens the traceability view (timer
 * stopped) where hovering a ring segment or a legend row highlights it.
 */
function useHeroDemo(t: ReturnType<typeof useTranslations>) {
  const [step, setStep] = useState(0);
  const [trace, setTrace] = useState(false);
  const [hover, setHover] = useState(-1);
  const timer = useRef<ReturnType<typeof setInterval> | null>(null);

  const stop = useCallback(() => {
    if (timer.current) clearInterval(timer.current);
    timer.current = null;
  }, []);
  const start = useCallback(() => {
    stop();
    timer.current = setInterval(() => setStep((s) => (s + 1) % 5), STEP_MS);
  }, [stop]);

  useEffect(() => {
    start();
    return stop;
  }, [start, stop]);

  const go = (i: number) => {
    setStep(i);
    start();
  };
  const st = (i: number) => {
    const active = step === i;
    return {
      border: active ? ACCENT : '#E6E4F4',
      bg: active ? '#FFFFFF' : '#FDF8F0',
      shadow: active ? '0 12px 28px rgba(23,23,68,0.14)' : 'none',
      title: step >= i ? '#171744' : '#4A4A66',
    };
  };
  const labels = t.raw('data.segments') as SegmentLabel[];
  const segs = SEGMENTS.map((s, i) => ({ ...s, ...labels[i] }));
  let acc = 0;
  const ring = segs.map((s, i) => {
    const len = Math.round(s.pct * 4.02);
    const r = { len, off: -acc, w: hover === i ? 36 : 30, op: hover === -1 || hover === i ? 1 : 0.4, enter: () => setHover(i) };
    acc += len;
    return r;
  });
  const h = hover >= 0 ? segs[hover] : null;
  const pos = Math.min(step, 3);

  return {
    accent: ACCENT,
    st0: st(0), st1: st(1), st2: st(2), st3: st(3),
    coinTop: 34 + pos * 112,
    railFill: pos * 112 + 30,
    pick0: () => go(0), pick1: () => go(1), pick3: () => go(3),
    openTrace: () => { stop(); setTrace(true); setStep(2); setHover(-1); },
    closeTrace: () => { setTrace(false); setHover(-1); start(); },
    isTrace: trace,
    isFlow: !trace,
    cardTitle: trace ? t('data.cardTitleTrace') : t('data.cardTitleFlow'),
    caption: trace ? t('data.captionTrace') : t('data.captionFlow'),
    as0: ring[0], as1: ring[1], as2: ring[2],
    ac: h ? { pct: `${h.pct}%`, label: h.short, color: h.text } : { pct: '100%', label: t('data.used'), color: '#1E9E95' },
    aLegend: segs.map((s, i) => ({ label: s.label, color: s.color, pct: s.pct, bg: hover === i ? '#F1EFFA' : 'transparent', enter: () => setHover(i) })),
    aLeave: () => setHover(-1),
  };
}

/**
 * Home hero: headline, CTAs and the animated « Suivez un don » / traceability card.
 */
export function Hero() {
  const t = useTranslations('landing.hero');
  const { aLeave, aLegend, ac, accent, as0, as1, as2, caption, cardTitle, closeTrace, coinTop, isFlow, isTrace, openTrace, pick0, pick1, pick3, railFill, st0, st1, st2, st3 } = useHeroDemo(t);

  return (
    <section style={{ background: "#32327D", color: "#FFFFFF", padding: "88px 48px 96px" }}>
      <div style={{ maxWidth: "1240px", margin: "0 auto", display: "grid", gridTemplateColumns: "repeat(auto-fit, minmax(min(100%, 460px), 1fr))", gap: "64px", alignItems: "center" }}>
        <div style={{ display: "flex", flexDirection: "column", gap: "28px" }}>
          <h1 style={{ margin: "0", fontFamily: "'Nunito Sans', sans-serif", fontWeight: "900", fontSize: "56px", lineHeight: "1.08", letterSpacing: "-0.5px" }}>{t.rich("laPlateformeDeDons", { t0: (chunks) => <span style={{ fontFamily: "Lora, serif", fontStyle: "italic", fontWeight: "500", color: `${accent}` }}>{chunks}</span> })}</h1>
          <p style={{ margin: "0", fontSize: "19px", lineHeight: "1.65", color: "rgba(255,255,255,0.82)", maxWidth: "520px" }}>{t("donnezUneAssociationElle")}</p>
          <div style={{ display: "flex", flexWrap: "wrap", gap: "14px" }}>
            <a href={APP_URL} style={{ display: "inline-flex", alignItems: "center", minHeight: "52px", padding: "0 28px", borderRadius: "9999px", background: `${accent}`, color: "#171744", fontWeight: "700", fontSize: "16px", textDecoration: "none" }}>{t("faireUnDon")}</a>
            <Link href="/associations" style={{ display: "inline-flex", alignItems: "center", minHeight: "52px", padding: "0 28px", borderRadius: "9999px", border: "1.5px solid rgba(255,255,255,0.55)", color: "#FFFFFF", fontWeight: "600", fontSize: "16px", textDecoration: "none" }}>{t("creerUneCampagne")}</Link>
          </div>
          <div style={{ display: "flex", flexWrap: "wrap", gap: "24px", paddingTop: "24px", borderTop: "1px solid rgba(255,255,255,0.14)" }}>
            <div style={{ display: "flex", alignItems: "center", gap: "10px", fontSize: "15px", color: "rgba(255,255,255,0.88)" }}>
              <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke={accent} strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
                <path d="M4 6h16M4 12h16M4 18h10" />
              </svg>
              <span>{t.rich("chaquePaiementPublie", { t0: (chunks) => <strong style={{ color: "#FFFFFF" }}>{chunks}</strong> })}</span>
            </div>
            <div style={{ display: "flex", alignItems: "center", gap: "10px", fontSize: "15px", color: "rgba(255,255,255,0.88)" }}>
              <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke={accent} strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
                <path d="M7 3h7l5 5v13H7z" />
                <path d="M14 3v5h5" />
                <path d="M10 14l2 2 4-4" />
              </svg>
              <span>{t.rich("recuFiscalImmediat", { t0: (chunks) => <strong style={{ color: "#FFFFFF" }}>{chunks}</strong> })}</span>
            </div>
            <div style={{ display: "flex", alignItems: "center", gap: "10px", fontSize: "15px", color: "rgba(255,255,255,0.88)" }}>
              <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke={accent} strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
                <circle cx="12" cy="12" r="9" />
                <path d="M9 12h6" />
              </svg>
              <span>{t.rich("deFraisPourVous", { t0: (chunks) => <strong style={{ color: "#FFFFFF" }}>{chunks}</strong> })}</span>
            </div>
          </div>
        </div>
        <div style={{ background: "#FFFFFF", color: "#171744", borderRadius: "24px", padding: "28px", boxShadow: "0 24px 60px rgba(10,10,40,0.35)", display: "flex", flexDirection: "column", gap: "20px" }}>
          <div style={{ display: "flex", alignItems: "center", justifyContent: "space-between", gap: "12px", flexWrap: "wrap" }}>
            <div style={{ display: "flex", alignItems: "center", gap: "10px" }}>
              <span style={{ fontFamily: "'Nunito Sans', sans-serif", fontWeight: "900", fontSize: "20px" }}>
                {cardTitle}
              </span>
              <span style={{ fontSize: "12px", fontWeight: "600", color: "#4A4A66", background: "#E6E4F4", borderRadius: "9999px", padding: "4px 10px" }}>{t("exemple")}</span>
            </div>
            {isTrace && (
            <>
              <button type="button" onClick={closeTrace} style={{ minHeight: "44px", padding: "0 16px", borderRadius: "9999px", border: "1.5px solid #E6E4F4", background: "#FFFFFF", color: "#32327D", fontFamily: "Inter, sans-serif", fontWeight: "600", fontSize: "14px", cursor: "pointer" }}>{t("retourAuParcours")}</button>
            </>
            )}
          </div>
          {isFlow && (
          <>
            <div style={{ position: "relative", display: "flex", flexDirection: "column", gap: "16px", paddingLeft: "40px" }}>
              <div aria-hidden="true" style={{ position: "absolute", left: "13px", top: "18px", bottom: "18px", width: "2px", background: "#E6E4F4" }} />
              <div aria-hidden="true" style={{ position: "absolute", left: "13px", top: "18px", width: "2px", height: `${railFill}px`, background: `${accent}`, transition: "height 0.6s ease" }} />
              <div aria-hidden="true" style={{ position: "absolute", left: "0", top: `${coinTop}px`, width: "28px", height: "28px", borderRadius: "50%", background: "#FFB347", color: "#171744", fontWeight: "800", fontSize: "13px", display: "flex", alignItems: "center", justifyContent: "center", boxShadow: "0 0 0 6px rgba(255,179,71,0.25)", transition: "top 0.6s ease" }}>
                €
              </div>
              <button type="button" onClick={pick0} style={{ textAlign: "left", minHeight: "96px", boxSizing: "border-box", padding: "16px 18px", borderRadius: "16px", border: `1.5px solid ${st0.border}`, background: `${st0.bg}`, boxShadow: `${st0.shadow}`, transition: "all 0.5s ease", cursor: "pointer", fontFamily: "Inter, sans-serif", color: "#171744", display: "flex", alignItems: "center", gap: "16px" }}>
                <div style={{ flex: "1", display: "flex", flexDirection: "column", gap: "4px" }}>
                  <span style={{ fontFamily: "'Nunito Sans', sans-serif", fontWeight: "800", fontSize: "16px", color: `${st0.title}` }}>{t("donRecu")}</span>
                  <span style={{ fontSize: "14px", lineHeight: "1.5", color: "#4A4A66" }}>{t("vousDonnezLaCantine")}</span>
                </div>
                <span style={{ fontFamily: "'Nunito Sans', sans-serif", fontWeight: "900", fontSize: "20px", whiteSpace: "nowrap" }}>
                  50 €
                </span>
              </button>
              <button type="button" onClick={pick1} style={{ textAlign: "left", minHeight: "96px", boxSizing: "border-box", padding: "16px 18px", borderRadius: "16px", border: `1.5px solid ${st1.border}`, background: `${st1.bg}`, boxShadow: `${st1.shadow}`, transition: "all 0.5s ease", cursor: "pointer", fontFamily: "Inter, sans-serif", color: "#171744", display: "flex", alignItems: "center", gap: "16px" }}>
                <div style={{ flex: "1", display: "flex", flexDirection: "column", gap: "4px" }}>
                  <span style={{ fontFamily: "'Nunito Sans', sans-serif", fontWeight: "800", fontSize: "16px", color: `${st1.title}` }}>{t("scelleDansLeRegistre")}</span>
                  <span style={{ fontSize: "14px", lineHeight: "1.5", color: "#4A4A66" }}>{t("niAssociationNiNous")}</span>
                </div>
                <span style={{ fontFamily: "ui-monospace, Menlo, monospace", fontSize: "13px", color: "#32327D", background: "#E6E4F4", borderRadius: "8px", padding: "6px 10px", whiteSpace: "nowrap" }}>
                  7f3a…c21e
                </span>
              </button>
              <button type="button" onClick={openTrace} style={{ textAlign: "left", minHeight: "96px", boxSizing: "border-box", padding: "16px 18px", borderRadius: "16px", border: `1.5px solid ${st2.border}`, background: `${st2.bg}`, boxShadow: `${st2.shadow}`, transition: "all 0.5s ease", cursor: "pointer", fontFamily: "Inter, sans-serif", color: "#171744", display: "flex", alignItems: "center", gap: "16px" }}>
                <div style={{ flex: "1", display: "flex", flexDirection: "column", gap: "4px" }}>
                  <span style={{ fontFamily: "'Nunito Sans', sans-serif", fontWeight: "800", fontSize: "16px", color: `${st2.title}` }}>{t("payeDepuisCommonlink")}</span>
                  <span style={{ fontSize: "14px", lineHeight: "1.5", color: "#4A4A66" }}>{t("associationAcheteSesDenrees")}</span>
                </div>
                <span style={{ fontFamily: "'Nunito Sans', sans-serif", fontWeight: "900", fontSize: "20px", whiteSpace: "nowrap" }}>
                  1 240 €
                </span>
              </button>
              <button type="button" onClick={pick3} style={{ textAlign: "left", minHeight: "96px", boxSizing: "border-box", padding: "16px 18px", borderRadius: "16px", border: `1.5px solid ${st3.border}`, background: `${st3.bg}`, boxShadow: `${st3.shadow}`, transition: "all 0.5s ease", cursor: "pointer", fontFamily: "Inter, sans-serif", color: "#171744", display: "flex", alignItems: "center", gap: "16px" }}>
                <div style={{ flex: "1", display: "flex", flexDirection: "column", gap: "4px" }}>
                  <span style={{ fontFamily: "'Nunito Sans', sans-serif", fontWeight: "800", fontSize: "16px", color: `${st3.title}` }}>{t("publieSurLaPage")}</span>
                  <span style={{ fontSize: "14px", lineHeight: "1.5", color: "#4A4A66" }}>{t("montantEtDestinataireVisibles")}</span>
                </div>
                <span style={{ display: "inline-flex", alignItems: "center", gap: "6px", fontSize: "13px", fontWeight: "700", color: "#1E7F78", background: "rgba(78,205,196,0.16)", borderRadius: "9999px", padding: "6px 12px", whiteSpace: "nowrap" }}>
                  <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="#1E7F78" strokeWidth="3" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
                    <path d="M5 12l5 5L19 7" />
                  </svg>
                  {t("paiementPublie")}
                </span>
              </button>
            </div>
          </>
          )}
          {isTrace && (
          <>
            <div style={{ display: "flex", flexDirection: "column", gap: "18px" }}>
              <div style={{ display: "grid", gridTemplateColumns: "repeat(2, minmax(0, 1fr))", gap: "12px" }}>
                <div style={{ display: "flex", flexDirection: "column", gap: "2px" }}>
                  <span style={{ fontSize: "13px", color: "#4A4A66" }}>{t("montantDejaUtilise")}</span>
                  <strong style={{ fontSize: "16px", whiteSpace: "nowrap" }}>
                    50,00 €
                  </strong>
                </div>
                <div style={{ display: "flex", flexDirection: "column", gap: "2px" }}>
                  <span style={{ fontSize: "13px", color: "#4A4A66" }}>{t("montantPasEncoreUtilise")}</span>
                  <strong style={{ fontSize: "16px", whiteSpace: "nowrap" }}>
                    0,00 €
                  </strong>
                </div>
              </div>
              <div style={{ display: "flex", alignItems: "center", gap: "20px", flexWrap: "wrap" }}>
                <svg width="150" height="150" viewBox="0 0 170 170" role="img" aria-label={t("repartitionDuDonAchat")} style={{ flex: "none", overflow: "visible" }}>
                  <circle cx="85" cy="85" r="64" fill="none" stroke="#ECEAF5" strokeWidth="30" />
                  <circle cx="85" cy="85" r="64" fill="none" stroke="#4ECDC4" strokeWidth={as0.w} strokeDasharray={`${as0.len} 402`} strokeDashoffset={as0.off} opacity={as0.op} transform="rotate(-90 85 85)" onMouseEnter={as0.enter} onMouseLeave={aLeave} style={{ transition: "all 0.5s ease", cursor: "pointer" }} />
                  <circle cx="85" cy="85" r="64" fill="none" stroke="#FF6B5B" strokeWidth={as1.w} strokeDasharray={`${as1.len} 402`} strokeDashoffset={as1.off} opacity={as1.op} transform="rotate(-90 85 85)" onMouseEnter={as1.enter} onMouseLeave={aLeave} style={{ transition: "all 0.5s ease", cursor: "pointer" }} />
                  <circle cx="85" cy="85" r="64" fill="none" stroke="#FFB347" strokeWidth={as2.w} strokeDasharray={`${as2.len} 402`} strokeDashoffset={as2.off} opacity={as2.op} transform="rotate(-90 85 85)" onMouseEnter={as2.enter} onMouseLeave={aLeave} style={{ transition: "all 0.5s ease", cursor: "pointer" }} />
                  <text x="85" y="84" textAnchor="middle" fontFamily="Nunito Sans, sans-serif" fontWeight="900" fontSize="26" fill={ac.color}>
                    {ac.pct}
                  </text>
                  <text x="85" y="104" textAnchor="middle" fontFamily="Inter, sans-serif" fontSize="11" fill="#4A4A66">
                    {ac.label}
                  </text>
                </svg>
                <div style={{ flex: "1", minWidth: "190px", display: "flex", flexDirection: "column", gap: "4px" }}>
                  {aLegend.map((g, gIndex) => (
                  <Fragment key={gIndex}>
                    <button type="button" onMouseEnter={g.enter} onMouseLeave={aLeave} onFocus={g.enter} onBlur={aLeave} onClick={g.enter} style={{ minHeight: "40px", display: "flex", alignItems: "center", gap: "10px", padding: "0 10px", border: "none", borderRadius: "10px", background: `${g.bg}`, fontFamily: "Inter, sans-serif", fontSize: "13px", color: "#171744", textAlign: "left", cursor: "pointer", transition: "background 0.3s ease" }}>
                      <span style={{ width: "10px", height: "10px", flex: "none", borderRadius: "50%", background: `${g.color}` }} />
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
              <div style={{ display: "flex", flexDirection: "column", gap: "8px" }}>
                <strong style={{ fontFamily: "'Nunito Sans', sans-serif", fontWeight: "800", fontSize: "15px" }}>{t("depensesFinanceesParCe")}</strong>
                <div style={{ display: "flex", justifyContent: "space-between", alignItems: "baseline", gap: "10px", fontSize: "14px" }}>
                  <span>{t.rich("achatDeDenreesAlimentaires", { t0: (chunks) => <span style={{ color: "#4A4A66" }}>{chunks}</span> })}</span>
                  <strong style={{ whiteSpace: "nowrap" }}>
                    35,00 €
                  </strong>
                </div>
                <div style={{ display: "flex", justifyContent: "space-between", alignItems: "baseline", gap: "10px", fontSize: "14px" }}>
                  <span>{t.rich("locationCamionRefrigereOct", { t0: (chunks) => <span style={{ color: "#4A4A66" }}>{chunks}</span> })}</span>
                  <strong style={{ whiteSpace: "nowrap" }}>
                    10,00 €
                  </strong>
                </div>
                <div style={{ display: "flex", justifyContent: "space-between", alignItems: "baseline", gap: "10px", fontSize: "14px" }}>
                  <span>{t.rich("remboursementDeFraisDe", { t0: (chunks) => <span style={{ color: "#4A4A66" }}>{chunks}</span> })}</span>
                  <strong style={{ whiteSpace: "nowrap" }}>
                    5,00 €
                  </strong>
                </div>
              </div>
            </div>
          </>
          )}
          <p style={{ margin: "0", fontSize: "13px", lineHeight: "1.5", color: "#4A4A66" }}>
            {caption}
          </p>
        </div>
      </div>
    </section>
  );
}
