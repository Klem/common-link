'use client';

import { useTranslations } from 'next-intl';
import { ONBOARDING_STEPS } from './onboardingFlow';

interface OnboardingStepperProps {
  /** Current 1-based step (see `SCREEN_TO_STEP`). */
  step: number;
  /** True once the flow is finished (active and collecting): every step is shown as done. */
  done?: boolean;
  /** Shows the current step in the warning colour (complement requested, active without payments — S4). */
  warn?: boolean;
}

/**
 * Horizontal stepper of the bank tab onboarding flow (mockup `renderStepper`):
 * Préparer · Remplir chez Mollie · Paiement test · Vérification · Actif.
 *
 * Purely presentational; the step index is derived by the caller from the server status.
 */
export default function OnboardingStepper({ step, done = false, warn = false }: OnboardingStepperProps) {
  const t = useTranslations('settings.mollie.onboarding.stepper');

  return (
    <ol className="stepper" aria-label={t('label')}>
      {ONBOARDING_STEPS.map((key, index) => {
        const n = index + 1;
        const isDone = n < step || done;
        const isCurrent = n === step && !done;
        const label = t(`steps.${key}`);
        const classes = ['stepper-item', isDone && 'done', isCurrent && 'current', isCurrent && warn && 'warn']
          .filter(Boolean)
          .join(' ');
        const stateKey = isDone ? 'state.done' : isCurrent ? 'state.current' : 'state.upcoming';
        return (
          <li
            key={key}
            className={classes}
            aria-current={isCurrent ? 'step' : undefined}
            aria-label={t(stateKey, { label })}
          >
            <span className="stepper-num" aria-hidden="true">{isDone ? '✓' : n}</span>
            <span aria-hidden="true">{label}</span>
          </li>
        );
      })}
    </ol>
  );
}
