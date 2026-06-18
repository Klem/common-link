'use client';

import { useState } from 'react';
import { useTranslations } from 'next-intl';

const DONUT_PALETTE = [
  'var(--color-green)',
  'var(--color-coral)',
  'var(--color-amber)',
  'var(--color-indigo)',
  '#34C759',
  '#9B59B6',
  '#3498DB',
  '#E67E22',
  '#1ABC9C',
  '#E74C3C',
  '#F39C12',
  '#2980B9',
];

export interface DonutSlice {
  label: string;
  value: number;
  color?: string;
}

interface DonutProps {
  slices: DonutSlice[];
  /** i18n key for the empty state message */
  emptyKey?: string;
}

interface Segment {
  d: string;
  color: string;
  label: string;
  value: number;
  pct: number;
}

function buildSegments(slices: DonutSlice[]): Segment[] {
  const R = 70, r = 42, cx = 90, cy = 90;
  const total = slices.reduce((s, sl) => s + sl.value, 0);
  if (total === 0) return [];

  let cumulAngle = -Math.PI / 2;
  return slices.map((sl, i) => {
    const angle = (sl.value / total) * 2 * Math.PI;
    const x1 = cx + R * Math.cos(cumulAngle);
    const y1 = cy + R * Math.sin(cumulAngle);
    cumulAngle += angle;
    const x2 = cx + R * Math.cos(cumulAngle);
    const y2 = cy + R * Math.sin(cumulAngle);
    const xi1 = cx + r * Math.cos(cumulAngle - angle);
    const yi1 = cy + r * Math.sin(cumulAngle - angle);
    const xi2 = cx + r * Math.cos(cumulAngle);
    const yi2 = cy + r * Math.sin(cumulAngle);
    const large = angle > Math.PI ? 1 : 0;
    const color = sl.color ?? DONUT_PALETTE[i % DONUT_PALETTE.length];
    const pct = Math.round((sl.value / total) * 100);
    const d = `M${x1.toFixed(2)},${y1.toFixed(2)} A${R},${R} 0 ${large},1 ${x2.toFixed(2)},${y2.toFixed(2)} L${xi2.toFixed(2)},${yi2.toFixed(2)} A${r},${r} 0 ${large},0 ${xi1.toFixed(2)},${yi1.toFixed(2)} Z`;
    return { d, color, label: sl.label, value: sl.value, pct };
  });
}

/**
 * SVG donut chart with hover interaction and right-side legend.
 * Replicates the `buildDonut()` helper from the dashboard prototype.
 */
export function Donut({ slices, emptyKey = 'reporting.tab.donutEmpty' }: DonutProps) {
  const t = useTranslations('dashboard');
  const [hovered, setHovered] = useState<number | null>(null);

  const filtered = slices.filter((s) => s.value > 0);

  if (filtered.length === 0) {
    return (
      <div className="text-[13px] text-[var(--color-text-2)] p-[20px] text-center">
        {t(emptyKey as Parameters<typeof t>[0])}
      </div>
    );
  }

  const segments = buildSegments(filtered);
  const active = hovered !== null ? segments[hovered] : null;

  return (
    <div className="flex items-center gap-[24px] flex-wrap w-full">
      {/* SVG donut */}
      <div className="relative flex-shrink-0">
        <svg width="180" height="180" viewBox="0 0 180 180">
          {segments.map((seg, i) => (
            <path
              key={i}
              d={seg.d}
              fill={seg.color}
              stroke="var(--color-bg)"
              strokeWidth="2"
              style={{
                transition: 'transform 0.15s, opacity 0.15s',
                cursor: 'pointer',
                transformOrigin: '90px 90px',
                transform: hovered === i ? 'scale(1.04)' : 'scale(1)',
                filter: hovered === i ? 'brightness(1.1)' : 'none',
              }}
              onMouseEnter={() => setHovered(i)}
              onMouseLeave={() => setHovered(null)}
            />
          ))}
          {/* Centre text */}
          <text
            x="90"
            y="90"
            textAnchor="middle"
            dominantBaseline="middle"
            fontFamily="'Nunito Sans', sans-serif"
            fontWeight="900"
            fontSize="15"
            fill={active ? active.color : 'var(--color-text)'}
          >
            {active ? `${active.pct}%` : '—'}
          </text>
          <text
            x="90"
            y="107"
            textAnchor="middle"
            dominantBaseline="middle"
            fontFamily="'Inter', sans-serif"
            fontSize="9"
            fill="var(--color-text-2)"
          >
            {active ? (active.label.length > 12 ? active.label.slice(0, 12) + '…' : active.label) : ''}
          </text>
        </svg>
      </div>

      {/* Legend */}
      <div className="flex-1 min-w-[140px]">
        {segments.map((seg, i) => (
          <div
            key={i}
            className="flex items-center gap-[7px] mb-[5px] cursor-pointer"
            onMouseEnter={() => setHovered(i)}
            onMouseLeave={() => setHovered(null)}
          >
            <span
              className="w-[10px] h-[10px] rounded-full flex-shrink-0"
              style={{ background: seg.color }}
            />
            <span
              className="text-[11px] text-[var(--color-text-2)] whitespace-nowrap overflow-hidden text-ellipsis max-w-[130px]"
              title={seg.label}
            >
              {seg.label}
            </span>
            <span className="ml-auto text-[11px] font-bold font-display text-[var(--color-text)]">
              {seg.pct}%
            </span>
          </div>
        ))}
      </div>
    </div>
  );
}

export { DONUT_PALETTE };
