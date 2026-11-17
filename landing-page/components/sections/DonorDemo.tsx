'use client';

import { Fragment, useState } from 'react';
import { useTranslations } from 'next-intl';

type CategoryId = 'denrees' | 'location' | 'frais' | 'reste';
type CategoryLabel = { label: string; short: string };
type DonationTexts = {
  campaign: string; asso: string; date: string; amount: string; used: string; unused: string; part: string;
  lines: { date: string; amount: string }[];
};

const CATEGORY_COLORS: Record<CategoryId, { color: string; text: string }> = {
  denrees: { color: '#4ECDC4', text: '#1E9E95' },
  location: { color: '#FF6B5B', text: '#D9473A' },
  frais: { color: '#FFB347', text: '#A86A00' },
  reste: { color: '#CFCBE6', text: '#4A4A66' },
};

/**
 * Numeric shape of the four example donations (texts live in `donors.demo.data.donations`, same
 * order): share used, ring segments and the category of each spending line.
 */
const DONATIONS: { usedPct: number; segs: [CategoryId, number][]; lines: CategoryId[] }[] = [
  { usedPct: 100, segs: [['denrees', 100]], lines: ['denrees'] },
  { usedPct: 100, segs: [['denrees', 45], ['location', 35], ['frais', 20]], lines: ['denrees', 'location', 'frais'] },
  { usedPct: 73, segs: [['denrees', 55], ['location', 18], ['reste', 27]], lines: ['denrees', 'location'] },
  { usedPct: 0, segs: [], lines: [] },
];
/** History order: most recent donation first. */
const ORDER = [3, 2, 1, 0];

/**
 * State of the donation-history demo, ported from the mockup's `Page_donateurs` component:
 * selecting a donation shows its ring and spending lines; hovering a segment or a legend row
 * highlights it.
 */
function useDonorDemo(t: ReturnType<typeof useTranslations>) {
  const [sel, setSel] = useState(1);
  const [hover, setHover] = useState(-1);

  const labels = t.raw('data.categories') as Record<CategoryId, CategoryLabel>;
  const texts = t.raw('data.donations') as DonationTexts[];
  const category = (id: CategoryId) => ({ ...CATEGORY_COLORS[id], ...labels[id] });
  const dons = DONATIONS.map((d, i) => ({
    ...texts[i],
    usedPct: d.usedPct,
    segs: d.segs.map(([id, pct]) => ({ c: category(id), pct })),
    lines: d.lines.map((id, k) => ({ c: category(id), ...texts[i].lines[k] })),
  }));
  const md = dons[sel];

  let acc = 0;
  const segs = [0, 1, 2].map((i) => {
    const s = md.segs[i];
    const len = s ? Math.round(s.pct * 4.02) : 0;
    const seg = { len, off: -acc, color: s ? s.c.color : '#ECEAF5', w: hover === i ? 36 : 30, op: hover === -1 || hover === i ? 1 : 0.4, enter: () => setHover(i) };
    acc += len;
    return seg;
  });
  const h = hover >= 0 && md.segs[hover] ? md.segs[hover] : null;

  return {
    ps0: segs[0], ps1: segs[1], ps2: segs[2],
    pc: h ? { pct: `${h.pct}%`, label: h.c.short, color: h.c.text } : { pct: `${md.usedPct}%`, label: t('data.used'), color: '#1E9E95' },
    pcAria: md.segs.length
      ? t('data.ariaBreakdown', { parts: md.segs.map((s) => `${s.pct} % ${s.c.label.toLowerCase()}`).join(', ') })
      : t('data.ariaUnused'),
    panelLegend: md.segs.map((s, i) => ({ label: s.c.label, color: s.c.color, pct: s.pct, bg: hover === i ? '#F1EFFA' : 'transparent', enter: () => setHover(i) })),
    pLeave: () => setHover(-1),
    history: ORDER.map((i) => ({
      campaign: dons[i].campaign, asso: dons[i].asso, date: dons[i].date, amount: dons[i].amount, part: dons[i].part,
      partColor: dons[i].usedPct === 100 ? '#1E7F78' : (dons[i].usedPct === 0 ? '#4A4A66' : '#A86A00'),
      pressed: sel === i,
      border: sel === i ? '#4ECDC4' : '#ECEAF5',
      shadow: sel === i ? '0 10px 24px rgba(23,23,68,0.10)' : 'none',
      pick: () => { setSel(i); setHover(-1); },
    })),
    mod: { used: md.used, unused: md.unused, empty: md.lines.length === 0, lines: md.lines.map((l) => ({ label: l.c.label, color: l.c.color, date: l.date, amount: l.amount })) },
  };
}

/**
 * Donors « Essayez » section: interactive donation history with the breakdown ring.
 */
export function DonorDemo() {
  const t = useTranslations('donors.demo');
  const { history, mod, pLeave, panelLegend, pc, pcAria, ps0, ps1, ps2 } = useDonorDemo(t);

  return (
    <section style={{ background: "#FFFFFF", padding: "88px 48px 96px" }}>
      <div style={{ maxWidth: "1240px", margin: "0 auto", display: "flex", flexDirection: "column", gap: "32px" }}>
        <div style={{ display: "flex", alignItems: "flex-end", justifyContent: "space-between", gap: "16px", flexWrap: "wrap" }}>
          <div style={{ display: "flex", flexDirection: "column", gap: "12px", maxWidth: "760px" }}>
            <span style={{ fontSize: "13px", fontWeight: "700", letterSpacing: "1.5px", textTransform: "uppercase", color: "#1E7F78" }}>{t("essayez")}</span>
            <h2 style={{ margin: "0", fontFamily: "'Nunito Sans', sans-serif", fontWeight: "900", fontSize: "40px", lineHeight: "1.15" }}>{t("auCentimePresVoyez")}</h2>
            <p style={{ margin: "0", fontSize: "17px", lineHeight: "1.7", color: "#4A4A66" }}>{t("chaqueDonEstReparti")}</p>
          </div>
          <span style={{ fontSize: "12px", fontWeight: "600", color: "#4A4A66", background: "#E6E4F4", borderRadius: "9999px", padding: "6px 12px" }}>{t("demonstrationDonneesFictives")}</span>
        </div>
        <div style={{ display: "grid", gridTemplateColumns: "repeat(auto-fit, minmax(min(100%, 440px), 1fr))", gap: "24px", alignItems: "start" }}>
          <div style={{ background: "#FCF8F1", border: "1px solid #ECEAF5", borderRadius: "24px", padding: "24px", display: "flex", flexDirection: "column", gap: "12px" }}>
            <span style={{ fontSize: "26px" }}>{t("mesDons")}</span>
            {history.map((h, hIndex) => (
            <Fragment key={hIndex}>
              <button type="button" onClick={h.pick} aria-pressed={h.pressed} style={{ textAlign: "left", background: "#FFFFFF", border: `1.5px solid ${h.border}`, boxShadow: `${h.shadow}`, borderRadius: "18px", padding: "14px 18px", display: "flex", flexDirection: "column", gap: "8px", fontFamily: "Inter, sans-serif", color: "#171744", cursor: "pointer", transition: "all 0.3s ease" }}>
                <span style={{ display: "flex", justifyContent: "space-between", alignItems: "flex-start", gap: "12px" }}>
                  <span style={{ display: "flex", flexDirection: "column", gap: "2px" }}>
                    <strong style={{ fontFamily: "'Nunito Sans', sans-serif", fontSize: "15px" }}>
                      {h.campaign}
                    </strong>
                    <span style={{ fontSize: "12px", color: "#4A4A66", textTransform: "uppercase" }}>
                      {`${h.asso}, ${h.date}`}
                    </span>
                  </span>
                  <strong style={{ fontFamily: "'Nunito Sans', sans-serif", fontWeight: "900", fontSize: "20px", color: "#1E9E95", whiteSpace: "nowrap" }}>
                    {h.amount}
                  </strong>
                </span>
                <span style={{ display: "flex", justifyContent: "space-between", alignItems: "center", gap: "12px", fontSize: "13px" }}>
                  <span style={{ fontWeight: "700", letterSpacing: "0.5px", color: "#4A4A66" }}>{t("partUtilisee")}</span>
                  <span style={{ fontWeight: "700", color: `${h.partColor}`, whiteSpace: "nowrap" }}>
                    {h.part}
                  </span>
                </span>
              </button>
            </Fragment>
            ))}
          </div>
          <div style={{ background: "#FFFFFF", border: "1px solid #ECEAF5", borderRadius: "28px", boxShadow: "0 24px 60px rgba(23,23,68,0.10)", overflow: "hidden" }}>
            <div style={{ padding: "22px 28px", borderBottom: "1px solid #ECEAF5" }}>
              <strong style={{ fontFamily: "'Nunito Sans', sans-serif", fontWeight: "800", fontSize: "19px" }}>{t("tracabiliteDeVotreDon")}</strong>
            </div>
            <div style={{ padding: "24px 28px", display: "flex", flexDirection: "column", gap: "20px" }}>
              <div style={{ display: "grid", gridTemplateColumns: "repeat(2, minmax(0, 1fr))", gap: "16px" }}>
                <div style={{ display: "flex", flexDirection: "column", gap: "2px" }}>
                  <span style={{ fontSize: "14px", color: "#4A4A66" }}>{t("montantDejaUtilise")}</span>
                  <strong style={{ fontSize: "17px", whiteSpace: "nowrap" }}>
                    {mod.used}
                  </strong>
                </div>
                <div style={{ display: "flex", flexDirection: "column", gap: "2px" }}>
                  <span style={{ fontSize: "14px", color: "#4A4A66" }}>{t("montantPasEncoreUtilise")}</span>
                  <strong style={{ fontSize: "17px", whiteSpace: "nowrap" }}>
                    {mod.unused}
                  </strong>
                </div>
              </div>
              <div style={{ display: "flex", alignItems: "center", gap: "24px", flexWrap: "wrap" }}>
                <svg width="170" height="170" viewBox="0 0 170 170" role="img" aria-label={pcAria} style={{ flex: "none", overflow: "visible" }}>
                  <circle cx="85" cy="85" r="64" fill="none" stroke="#ECEAF5" strokeWidth="30" />
                  <circle cx="85" cy="85" r="64" fill="none" stroke={ps0.color} strokeWidth={ps0.w} strokeDasharray={`${ps0.len} 402`} strokeDashoffset={ps0.off} opacity={ps0.op} transform="rotate(-90 85 85)" onMouseEnter={ps0.enter} onMouseLeave={pLeave} style={{ transition: "all 0.5s ease", cursor: "pointer" }} />
                  <circle cx="85" cy="85" r="64" fill="none" stroke={ps1.color} strokeWidth={ps1.w} strokeDasharray={`${ps1.len} 402`} strokeDashoffset={ps1.off} opacity={ps1.op} transform="rotate(-90 85 85)" onMouseEnter={ps1.enter} onMouseLeave={pLeave} style={{ transition: "all 0.5s ease", cursor: "pointer" }} />
                  <circle cx="85" cy="85" r="64" fill="none" stroke={ps2.color} strokeWidth={ps2.w} strokeDasharray={`${ps2.len} 402`} strokeDashoffset={ps2.off} opacity={ps2.op} transform="rotate(-90 85 85)" onMouseEnter={ps2.enter} onMouseLeave={pLeave} style={{ transition: "all 0.5s ease", cursor: "pointer" }} />
                  <text x="85" y="84" textAnchor="middle" fontFamily="Nunito Sans, sans-serif" fontWeight="900" fontSize="26" fill={pc.color}>
                    {pc.pct}
                  </text>
                  <text x="85" y="104" textAnchor="middle" fontFamily="Inter, sans-serif" fontSize="11" fill="#4A4A66">
                    {pc.label}
                  </text>
                </svg>
                <div style={{ flex: "1", minWidth: "210px", display: "flex", flexDirection: "column", gap: "6px" }}>
                  {panelLegend.map((g, gIndex) => (
                  <Fragment key={gIndex}>
                    <button type="button" onMouseEnter={g.enter} onMouseLeave={pLeave} onFocus={g.enter} onBlur={pLeave} onClick={g.enter} style={{ minHeight: "44px", display: "flex", alignItems: "center", gap: "10px", padding: "0 12px", border: "none", borderRadius: "12px", background: `${g.bg}`, fontFamily: "Inter, sans-serif", fontSize: "14px", color: "#171744", textAlign: "left", cursor: "pointer", transition: "background 0.3s ease" }}>
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
                  {mod.empty && (
                  <>
                    <span style={{ fontSize: "14px", color: "#4A4A66", padding: "0 12px" }}>{t("ceDonAttendSa")}</span>
                  </>
                  )}
                </div>
              </div>
              <div style={{ display: "flex", flexDirection: "column", gap: "10px" }}>
                <strong style={{ fontFamily: "'Nunito Sans', sans-serif", fontWeight: "800", fontSize: "16px" }}>{t("depensesFinanceesParCe")}</strong>
                {mod.lines.map((l, lIndex) => (
                <Fragment key={lIndex}>
                  <div style={{ display: "flex", justifyContent: "space-between", alignItems: "baseline", gap: "12px", fontSize: "15px" }}>
                    <span style={{ display: "flex", alignItems: "center", gap: "8px" }}>
                      <span aria-hidden="true" style={{ width: "8px", height: "8px", flex: "none", borderRadius: "50%", background: `${l.color}` }} />
                      <span>
                        {l.label}
                        <span style={{ color: "#4A4A66" }}>
                          {`, ${l.date}`}
                        </span>
                      </span>
                    </span>
                    <strong style={{ whiteSpace: "nowrap" }}>
                      {l.amount}
                    </strong>
                  </div>
                </Fragment>
                ))}
                {mod.empty && (
                <>
                  <span style={{ fontSize: "14px", lineHeight: "1.55", color: "#4A4A66" }}>{t("associationPasEncoreRegle")}</span>
                </>
                )}
              </div>
            </div>
          </div>
        </div>
      </div>
    </section>
  );
}
