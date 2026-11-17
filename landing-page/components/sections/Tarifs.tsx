'use client';

import { useCallback, useEffect, useRef, useState } from 'react';
import { useTranslations } from 'next-intl';
import { TarifsHero } from './TarifsHero';
import { TarifsAsso } from './TarifsAsso';
import { TarifsDonor } from './TarifsDonor';

/** Reveal state of one animated line: opacity, vertical offset (px), strike-through width (%). */
export type Reveal = { op: number; ty: number; strike: number };
/** Look of one button of the « Je collecte / Je donne » switch. */
export type SwitchButton = { bg: string; color: string; pressed: boolean };

type View = 'asso' | 'don';

/** Delay between two revealed lines, as in the mockup. */
const REVEAL_MS = 230;
/** Number of animated lines: six fees then two information blocks. */
const REVEAL_STEPS = 8;

/**
 * Pricing page body, ported from the mockup's `Page_tarifs` component.
 *
 * Holds the state shared by the three sections: the selected profile and the progressive reveal.
 * The reveal starts when the watched block is 20 % visible (IntersectionObserver), then shows one
 * more line every {@link REVEAL_MS} ms; with `prefers-reduced-motion` everything is shown at once.
 * Switching profile resets the reveal, which restarts when the other block comes into view.
 */
export function Tarifs() {
  const t = useTranslations('tarifs.asso');
  const [view, setView] = useState<View>('asso');
  const [shown, setShown] = useState(0);
  const observer = useRef<IntersectionObserver | null>(null);
  const timer = useRef<ReturnType<typeof setInterval> | null>(null);

  const stopTimer = () => {
    if (timer.current) clearInterval(timer.current);
    timer.current = null;
  };
  const reveal = useCallback(() => {
    stopTimer();
    timer.current = setInterval(() => {
      setShown((s) => {
        if (s >= REVEAL_STEPS) {
          stopTimer();
          return s;
        }
        return s + 1;
      });
    }, REVEAL_MS);
  }, []);

  const watch = useCallback((el: Element | null) => {
    observer.current?.disconnect();
    observer.current = null;
    if (!el) return;
    const reduce = window.matchMedia?.('(prefers-reduced-motion: reduce)').matches;
    if (reduce || typeof IntersectionObserver === 'undefined') {
      setShown(99);
      return;
    }
    observer.current = new IntersectionObserver((entries) => {
      if (entries.some((e) => e.isIntersecting)) {
        observer.current?.disconnect();
        observer.current = null;
        reveal();
      }
    }, { threshold: 0.2 });
    observer.current.observe(el);
  }, [reveal]);

  useEffect(() => () => {
    observer.current?.disconnect();
    stopTimer();
  }, []);

  const pick = (v: View) => {
    if (view === v) return;
    stopTimer();
    setView(v);
    setShown(0);
  };
  const r = (i: number): Reveal => ({ op: shown > i ? 1 : 0, ty: shown > i ? 0 : 18, strike: shown > i ? 104 : 0 });
  const btn = (on: boolean): SwitchButton => ({ bg: on ? '#FFFFFF' : 'transparent', color: on ? '#171744' : '#FFFFFF', pressed: on });
  const fees = t.raw('data.fees') as string[];

  return (
    <>
      <TarifsHero tAsso={btn(view === 'asso')} tDon={btn(view === 'don')} pickAsso={() => pick('asso')} pickDon={() => pick('don')} />
      {view === 'asso' && (
        <TarifsAsso fees={fees.map((label, i) => ({ label, ...r(i) }))} info0={r(6)} info1={r(7)} watch={watch} />
      )}
      {view === 'don' && <TarifsDonor d0={r(0)} d1={r(1)} watch={watch} />}
    </>
  );
}
