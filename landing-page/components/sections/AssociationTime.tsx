'use client';

import { useCallback, useEffect, useRef, useState } from 'react';
import { useTranslations } from 'next-intl';

const EFFECT_COLORS = ['#4ECDC4', '#FF6B5B', '#FFB347'];
/** Payment phases: idle, bank authorisation, transfer, paid. */
type Phase = 0 | 1 | 2 | 3;

/**
 * State of the payment simulation, ported from the mockup's `Page_associations` component: a click
 * on « Payer » walks through the bank authorisation (1.1 s) and the transfer (1 s), then reveals
 * the three published effects 600 ms apart. « reset » cancels the pending timers.
 */
function usePaymentDemo(t: ReturnType<typeof useTranslations>) {
  const [phase, setPhase] = useState<Phase>(0);
  const [shown, setShown] = useState(0);
  const timers = useRef<ReturnType<typeof setTimeout>[]>([]);

  const clear = useCallback(() => {
    timers.current.forEach((id) => clearTimeout(id));
    timers.current = [];
  }, []);
  useEffect(() => clear, [clear]);

  const pay = () => {
    if (phase !== 0) return;
    clear();
    setPhase(1);
    setShown(0);
    timers.current.push(setTimeout(() => setPhase(2), 1100));
    timers.current.push(setTimeout(() => { setPhase(3); setShown(1); }, 2100));
    timers.current.push(setTimeout(() => setShown(2), 2700));
    timers.current.push(setTimeout(() => setShown(3), 3300));
  };
  const reset = () => {
    clear();
    setPhase(0);
    setShown(0);
  };
  const effect = (i: number) => {
    const on = shown > i;
    return { bg: on ? '#FFFFFF' : '#FCF8F1', border: on ? EFFECT_COLORS[i] : '#ECEAF5', icon: on ? EFFECT_COLORS[i] : '#E6E4F4', title: on ? '#171744' : '#4A4A66', tx: on ? 0 : 12 };
  };
  const labels = ['data.pay', 'data.authorising', 'data.transferring', 'data.paid'] as const;

  return {
    pay,
    reset,
    busy: phase !== 0,
    payBg: phase === 3 ? '#1E9E95' : (phase === 0 ? '#32327D' : '#8A8AB8'),
    payLabel: t(labels[phase]),
    done: phase === 3,
    e0: effect(0), e1: effect(1), e2: effect(2),
    bilanCount: shown > 2 ? 4 : 3,
    bilanTotal: shown > 2 ? t('data.totalAfter') : t('data.totalBefore'),
  };
}

/**
 * Associations « Gain de temps » section: simulated payment that gets published.
 */
export function AssociationTime() {
  const t = useTranslations('associations.time');
  const { bilanCount, bilanTotal, busy, done, e0, e1, e2, pay, payBg, payLabel, reset } = usePaymentDemo(t);

  return (
    <section id="associations-temps" style={{ background: "#FFFFFF", padding: "96px 48px" }}>
      <div style={{ maxWidth: "1240px", margin: "0 auto", display: "flex", flexDirection: "column", gap: "32px" }}>
        <div style={{ display: "flex", alignItems: "flex-end", justifyContent: "space-between", gap: "16px", flexWrap: "wrap" }}>
          <div style={{ display: "flex", flexDirection: "column", gap: "12px", maxWidth: "760px" }}>
            <span style={{ fontSize: "13px", fontWeight: "700", letterSpacing: "1.5px", textTransform: "uppercase", color: "#1E7F78" }}>{t("gainDeTemps")}</span>
            <h2 style={{ margin: "0", fontFamily: "'Nunito Sans', sans-serif", fontWeight: "900", fontSize: "44px", lineHeight: "1.1" }}>{t("payezEstPublie")}</h2>
            <p style={{ margin: "0", fontSize: "19px", lineHeight: "1.6", color: "#4A4A66" }}>{t("unSeulGesteDe")}</p>
          </div>
          <span style={{ fontSize: "12px", fontWeight: "600", color: "#4A4A66", background: "#E6E4F4", borderRadius: "9999px", padding: "6px 12px" }}>{t("demonstrationDonneesFictives")}</span>
        </div>
        <div style={{ display: "grid", gridTemplateColumns: "repeat(auto-fit, minmax(min(100%, 440px), 1fr))", gap: "28px", alignItems: "center" }}>
          <div style={{ background: "#FFFFFF", border: "1px solid #ECEAF5", borderRadius: "24px", padding: "30px", display: "flex", flexDirection: "column", gap: "18px", boxShadow: "0 20px 50px rgba(23,23,68,0.10)" }}>
            <span style={{ fontSize: "12px", fontWeight: "700", letterSpacing: "1.5px", color: "#4A4A66" }}>{t("votreEspaceAssociation")}</span>
            <strong style={{ fontFamily: "'Nunito Sans', sans-serif", fontWeight: "900", fontSize: "22px" }}>{t("emettreUnPaiement")}</strong>
            <div style={{ border: "1.5px solid #ECEAF5", borderRadius: "16px", padding: "16px 18px", display: "flex", alignItems: "center", justifyContent: "space-between", gap: "12px", background: "#FCF8F1" }}>
              <span style={{ display: "flex", alignItems: "center", gap: "12px" }}>
                <span aria-hidden="true" style={{ width: "12px", height: "12px", flex: "none", borderRadius: "50%", background: "#4ECDC4" }} />
                <span style={{ display: "flex", flexDirection: "column", gap: "2px" }}>
                  <strong style={{ fontSize: "16px" }}>{t("achatDeDenreesAlimentaires")}</strong>
                  <span style={{ fontSize: "14px", color: "#4A4A66" }}>{t("grossisteBioBordeaux")}</span>
                </span>
              </span>
              <strong style={{ fontFamily: "'Nunito Sans', sans-serif", fontWeight: "900", fontSize: "20px", whiteSpace: "nowrap" }}>
                480,00 €
              </strong>
            </div>
            <button type="button" onClick={pay} disabled={busy} style={{ minHeight: "58px", borderRadius: "9999px", border: "none", background: `${payBg}`, color: "#FFFFFF", fontFamily: "'Nunito Sans', sans-serif", fontWeight: "800", fontSize: "17px", cursor: "pointer", transition: "background 0.3s ease" }}>
              {payLabel}
            </button>
            <span style={{ fontSize: "13px", lineHeight: "1.5", color: "#4A4A66" }}>{t("leVirementPartDe")}</span>
            {done && (
            <>
              <button type="button" onClick={reset} style={{ alignSelf: "flex-start", minHeight: "44px", padding: "0 4px", border: "none", background: "transparent", color: "#32327D", fontFamily: "Inter, sans-serif", fontWeight: "600", fontSize: "14px", textDecoration: "underline", cursor: "pointer" }}>{t("recommencer")}</button>
            </>
            )}
          </div>
          <div style={{ display: "flex", flexDirection: "column", gap: "14px" }}>
            <div style={{ background: `${e0.bg}`, border: `1.5px solid ${e0.border}`, borderRadius: "20px", padding: "18px 20px", display: "flex", gap: "16px", alignItems: "flex-start", transform: `translateX(${e0.tx}px)`, transition: "all 0.5s ease" }}>
              <span aria-hidden="true" style={{ width: "44px", height: "44px", flex: "none", borderRadius: "50%", background: `${e0.icon}`, color: "#171744", display: "flex", alignItems: "center", justifyContent: "center", fontWeight: "900", transition: "background 0.4s ease" }}>
                1
              </span>
              <span style={{ flex: "1", display: "flex", flexDirection: "column", gap: "8px" }}>
                <strong style={{ fontFamily: "'Nunito Sans', sans-serif", fontSize: "18px", color: `${e0.title}` }}>{t("publieSurLaPage")}</strong>
                <span style={{ display: "flex", justifyContent: "space-between", gap: "12px", fontSize: "14px", color: "#4A4A66" }}>
                  <span>{t("achatDeDenreesAlimentaires2")}</span>
                  <strong style={{ color: "#1E9E95", whiteSpace: "nowrap" }}>
                    480,00 €
                  </strong>
                </span>
              </span>
            </div>
            <div style={{ background: `${e1.bg}`, border: `1.5px solid ${e1.border}`, borderRadius: "20px", padding: "18px 20px", display: "flex", gap: "16px", alignItems: "flex-start", transform: `translateX(${e1.tx}px)`, transition: "all 0.5s ease" }}>
              <span aria-hidden="true" style={{ width: "44px", height: "44px", flex: "none", borderRadius: "50%", background: `${e1.icon}`, color: "#171744", display: "flex", alignItems: "center", justifyContent: "center", fontWeight: "900", transition: "background 0.4s ease" }}>
                2
              </span>
              <span style={{ flex: "1", display: "flex", flexDirection: "column", gap: "8px" }}>
                <strong style={{ fontFamily: "'Nunito Sans', sans-serif", fontSize: "18px", color: `${e1.title}` }}>{t("vosDonateursSontPrevenus")}</strong>
                <span style={{ display: "flex", flexDirection: "column", gap: "2px", background: "#F1EFFA", borderRadius: "14px", padding: "10px 14px", fontSize: "14px" }}>
                  <span style={{ fontSize: "12px", fontWeight: "700", color: "#32327D" }}>{t("commonlinkMaintenant")}</span>
                  <span>{t("votreDonFinanceAchat")}</span>
                </span>
              </span>
            </div>
            <div style={{ background: `${e2.bg}`, border: `1.5px solid ${e2.border}`, borderRadius: "20px", padding: "18px 20px", display: "flex", gap: "16px", alignItems: "flex-start", transform: `translateX(${e2.tx}px)`, transition: "all 0.5s ease" }}>
              <span aria-hidden="true" style={{ width: "44px", height: "44px", flex: "none", borderRadius: "50%", background: `${e2.icon}`, color: "#171744", display: "flex", alignItems: "center", justifyContent: "center", fontWeight: "900", transition: "background 0.4s ease" }}>
                3
              </span>
              <span style={{ flex: "1", display: "flex", flexDirection: "column", gap: "8px" }}>
                <strong style={{ fontFamily: "'Nunito Sans', sans-serif", fontSize: "18px", color: `${e2.title}` }}>{t("votreBilanSeComplete")}</strong>
                <span style={{ fontSize: "14px", color: "#4A4A66" }}>{t.rich("depensesPublieesPayesRien", { t0: (chunks) => <strong style={{ color: "#171744" }}>{chunks}</strong>, t1: (chunks) => <strong style={{ color: "#171744" }}>{chunks}</strong>, bilanCount, bilanTotal })}</span>
              </span>
            </div>
          </div>
        </div>
      </div>
    </section>
  );
}
