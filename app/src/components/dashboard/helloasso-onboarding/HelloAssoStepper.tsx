'use client';

import { useTranslations } from 'next-intl';
import { StepState } from './helloAssoFlow';
import type { StepperItem } from './helloAssoFlow';

interface HelloAssoStepperProps {
  /** Steps computed by `stepperItems` from the screen, the path and the server status. */
  items: StepperItem[];
}

const STATE_CLASS: Record<StepState, string> = {
  [StepState.DONE]: 'done',
  [StepState.SKIPPED]: 'skipped',
  [StepState.CURRENT]: 'current',
  [StepState.UPCOMING]: '',
};

const STATE_LABEL: Record<StepState, string> = {
  [StepState.DONE]: 'state.done',
  [StepState.SKIPPED]: 'state.skipped',
  [StepState.CURRENT]: 'state.current',
  [StepState.UPCOMING]: 'state.upcoming',
};

/**
 * Four-step horizontal stepper of the HelloAsso journey (mockup `renderStepper`): Compte de paiement HelloAsso ·
 * Relier à CommonLink · Vérification · Actif. The current step carries `aria-current="step"`; every step has an
 * `aria-label` "<label> — terminé / déjà fait / en cours / à venir". Purely presentational.
 */
export default function HelloAssoStepper({ items }: HelloAssoStepperProps) {
  const t = useTranslations('settings.helloasso.onboarding.stepper');

  return (
    <ol className="stepper" aria-label={t('label')}>
      {items.map((item) => {
        const label = t(`steps.${item.key}`);
        const classes = ['stepper-item', STATE_CLASS[item.state], item.warn && 'warn'].filter(Boolean).join(' ');
        const showCheck = item.state === StepState.DONE;
        return (
          <li
            key={item.key}
            className={classes}
            aria-current={item.state === StepState.CURRENT ? 'step' : undefined}
            aria-label={t(STATE_LABEL[item.state], { label })}
          >
            <span className="stepper-num" aria-hidden="true">{showCheck ? '✓' : item.n}</span>
            <span aria-hidden="true">
              {label}
              {item.state === StepState.SKIPPED && <span className="stepper-sub">{t('skippedSub')}</span>}
            </span>
          </li>
        );
      })}
    </ol>
  );
}
