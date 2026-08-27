'use client';

import { useEffect, useRef, useState } from 'react';
import { useTranslations } from 'next-intl';
import type { LandingBudgetPostDto, MilestoneDto } from '@/lib/api/public';

const BAR_COLORS = [
  'var(--lp-primary)',
  'var(--lp-secondary)',
  '#B08968',
  '#9CA3AF',
] as const;

interface TransparencySectionProps {
  budget: LandingBudgetPostDto[];
  milestones: MilestoneDto[];
}

export function TransparencySection({
  budget,
  milestones,
}: TransparencySectionProps) {
  const t = useTranslations('landing');
  const sectionRef = useRef<HTMLDivElement>(null);
  const [animated, setAnimated] = useState(false);

  // A poste can round to 0% (buildBudgetProjection rounds HALF_UP) — showing it as an empty bar
  // would be misleading rather than transparent, so it is dropped from display entirely.
  const visibleBudget = budget.filter((item) => item.percentage > 0);

  useEffect(() => {
    const el = sectionRef.current;
    if (!el || visibleBudget.length === 0) return;
    const observer = new IntersectionObserver(
      ([entry]) => {
        if (entry.isIntersecting) {
          setAnimated(true);
          observer.disconnect();
        }
      },
      { threshold: 0.3 },
    );
    observer.observe(el);
    return () => observer.disconnect();
  }, [visibleBudget.length]);

  const commitments = milestones
    .filter((m) => m.transparencyCommitment !== null)
    .sort((a, b) => a.sortOrder - b.sortOrder);

  return (
    <section className="lp-section lp-transparency" ref={sectionRef}>
      <div className="lp-container">
        <span className="lp-eyebrow">{t('transparency.eyebrow')}</span>
        <h2 className="lp-section-title">{t('transparency.title')}</h2>

        {visibleBudget.length > 0 && (
          <div className="lp-budget-list">
            {visibleBudget.map((item, index) => (
              <div key={item.label} className="lp-budget-item">
                <div className="lp-budget-row">
                  <span className="lp-budget-label">{item.label}</span>
                  <span className="lp-budget-pct">{item.percentage}&nbsp;%</span>
                </div>
                <div className="lp-bar-track">
                  <div
                    className="lp-bar-fill"
                    style={{
                      width: animated ? `${item.percentage}%` : '0%',
                      background: BAR_COLORS[Math.min(index, BAR_COLORS.length - 1)],
                    }}
                  />
                </div>
              </div>
            ))}
          </div>
        )}

        <p className="lp-transparency-note">{t('transparency.note')}</p>

        {commitments.length > 0 && (
          <div className="lp-commitments">
            <p className="lp-commitments-title">{t('transparency.commitmentsTitle')}</p>
            <ul className="lp-commitments-list">
              {commitments.map((m) => (
                <li key={m.id} className="lp-commitment-item">
                  <strong>{m.title}</strong>
                  {' — '}
                  {m.transparencyCommitment}
                </li>
              ))}
            </ul>
          </div>
        )}
      </div>
    </section>
  );
}
