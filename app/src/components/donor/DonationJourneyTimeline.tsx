'use client';

import { useState, useEffect } from 'react';
import { useTranslations } from 'next-intl';
import { EmptyStateCard } from '@/components/dashboard';
import { useDonorDonationJourney } from '@/hooks/dashboard/useDonorDonationJourney';
import { JourneyStep } from '@/types/donor';
import type { JourneyStepDto } from '@/types/donor';

interface Props {
  /** Id of the donation to open the timeline on — typically the donor's most recent donation. */
  initialDonationId: string | null;
}

const STEP_ORDER: JourneyStep[] = [
  JourneyStep.RECEIVED,
  JourneyStep.RECORDED,
  JourneyStep.SPENT,
  JourneyStep.IMPACT_REPORTED,
];

function orderedSteps(steps: JourneyStepDto[]): JourneyStepDto[] {
  return STEP_ORDER.map((step) => steps.find((s) => s.step === step)).filter(
    (s): s is JourneyStepDto => s !== undefined,
  );
}

/**
 * Home-page block "Le parcours de votre don" — a 4-step timeline for a single donation, with
 * previous/next navigation between the donor's own donations.
 */
export function DonationJourneyTimeline({ initialDonationId }: Props) {
  const t = useTranslations('dashboard.donor.journey');
  const [donationId, setDonationId] = useState(initialDonationId);

  useEffect(() => {
    setDonationId(initialDonationId);
  }, [initialDonationId]);

  const { journey, isLoading, error } = useDonorDonationJourney(donationId);

  if (donationId === null) {
    return <EmptyStateCard icon="🧭" title={t('empty.title')} subtitle={t('empty.subtitle')} />;
  }

  if (isLoading) {
    return (
      <p className="text-sm text-text-2 py-4" aria-live="polite">
        {t('loading')}
      </p>
    );
  }

  if (error || !journey) {
    return (
      <p className="text-sm text-coral py-4" role="alert">
        {t('error')}
      </p>
    );
  }

  const steps = orderedSteps(journey.steps);
  const currentStep = steps.find((s) => !s.reached) ?? steps[steps.length - 1];

  return (
    <div className="flex flex-col gap-4">
      <ol className="flex flex-col gap-3" aria-label={t('title')}>
        {steps.map((step) => (
          <li
            key={step.step}
            aria-current={step.step === currentStep?.step ? 'step' : undefined}
            className={`flex items-center gap-3 text-sm ${step.reached ? 'text-text' : 'text-text-2'}`}
          >
            <span aria-hidden="true">{step.reached ? '✅' : '⏳'}</span>
            <span className={step.reached ? 'font-display font-bold' : ''}>
              {t(`steps.${step.step}.label`)}
            </span>
            {!step.reached && (
              <span className="text-xs text-text-2">{t(`steps.${step.step}.pendingHint`)}</span>
            )}
          </li>
        ))}
      </ol>

      <nav className="flex items-center justify-between" aria-label={t('navigation')}>
        <button
          type="button"
          className="btn btn-ghost btn-sm"
          disabled={!journey.previousDonationId}
          onClick={() => setDonationId(journey.previousDonationId)}
        >
          {t('previous')}
        </button>
        <button
          type="button"
          className="btn btn-ghost btn-sm"
          disabled={!journey.nextDonationId}
          onClick={() => setDonationId(journey.nextDonationId)}
        >
          {t('next')}
        </button>
      </nav>
    </div>
  );
}
