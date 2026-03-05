'use client';

import { useEffect, useState } from 'react';
import { useTranslations } from 'next-intl';

interface LoginProgressOverlayProps {
  provider: 'google' | 'magic' | 'email';
  steps?: string[];
}

interface Step {
  label: string;
  status: 'pending' | 'active' | 'done';
}

const STEP_DELAY_MS = 800;

export function LoginProgressOverlay({ provider, steps: customSteps }: LoginProgressOverlayProps) {
  const t = useTranslations('auth');
  const [visibleCount, setVisibleCount] = useState(0);
  const [doneCount, setDoneCount] = useState(0);

  const providerLabel = provider === 'google' ? 'Google' : provider === 'magic' ? 'Magic Link' : 'email';

  const defaultSteps = customSteps ?? [
    t('progress.authenticating', { provider: providerLabel }),
    t('progress.verified'),
    t('progress.loadingProfile'),
    t('progress.redirecting'),
  ];

  const steps: Step[] = defaultSteps.map((label, i) => ({
    label,
    status: i < doneCount ? 'done' : i < visibleCount ? 'active' : 'pending',
  }));

  useEffect(() => {
    const timers: ReturnType<typeof setTimeout>[] = [];

    defaultSteps.forEach((_, i) => {
      // Show step
      timers.push(setTimeout(() => setVisibleCount(i + 1), i * STEP_DELAY_MS + 300));
      // Mark done (before next step appears)
      if (i < defaultSteps.length - 1) {
        timers.push(
          setTimeout(() => setDoneCount(i + 1), i * STEP_DELAY_MS + STEP_DELAY_MS - 50),
        );
      }
    });

    // Mark last step done
    timers.push(
      setTimeout(
        () => setDoneCount(defaultSteps.length),
        (defaultSteps.length - 1) * STEP_DELAY_MS + 900,
      ),
    );

    return () => timers.forEach(clearTimeout);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  return (
    <div
      className="fixed inset-0 bg-bg z-[9999] flex flex-col items-center justify-center"
      style={{ animation: 'fadeIn 0.3s ease' }}
    >
      {/* Logo */}
      <div className="flex items-center gap-[10px] font-display text-[22px] font-extrabold text-green mb-10">
        <div
          className="w-[38px] h-[38px] rounded-[10px] flex items-center justify-center text-[18px]"
          style={{ background: 'linear-gradient(135deg, var(--color-green-dim), var(--color-green))' }}
        >
          🌍
        </div>
        CommonLink
      </div>

      {/* Steps */}
      <div className="flex flex-col gap-4 w-[300px]">
        {steps.map((step, i) => (
          <div
            key={i}
            className={`flex items-center gap-3 px-4 py-3 bg-bg-2 border border-border rounded-[10px] text-[13.5px] text-text-2 transition-all duration-300 ${
              step.status === 'pending' ? 'opacity-0 translate-y-2 pointer-events-none' : 'opacity-100 translate-y-0'
            } ${step.status === 'done' ? 'border-green/35 bg-green/[.07]' : ''}`}
            style={
              step.status !== 'pending'
                ? { animation: 'slideUpStep 0.35s cubic-bezier(0.22,1,0.36,1) both' }
                : undefined
            }
          >
            <span className="text-[16px]">
              {step.status === 'done' ? (
                <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="var(--color-green)" strokeWidth="2.5" aria-hidden="true">
                  <path
                    d="M5 13l4 4L19 7"
                    strokeLinecap="round"
                    strokeLinejoin="round"
                    style={{ animation: step.status === 'done' ? 'checkDraw 0.3s ease forwards' : 'none' }}
                    strokeDasharray="30"
                    strokeDashoffset={step.status === 'done' ? 0 : 30}
                  />
                </svg>
              ) : (
                <div
                  className="w-4 h-4 rounded-full border-2"
                  style={{
                    borderColor: 'var(--color-green-dim)',
                    borderTopColor: 'var(--color-green)',
                    animation: 'spinAround 0.7s linear infinite',
                  }}
                />
              )}
            </span>
            <span>{step.label}</span>
          </div>
        ))}
      </div>
    </div>
  );
}
