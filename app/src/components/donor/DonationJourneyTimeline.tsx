'use client';

import { useState, useEffect } from 'react';
import { useTranslations } from 'next-intl';
import { EmptyStateCard } from '@/components/dashboard';
import { useDonorDonationJourney } from '@/hooks/dashboard/useDonorDonationJourney';
import { JourneyStep } from '@/types/donor';
import type { JourneyStepDto, DonorDonationDto } from '@/types/donor';

interface Props {
  /** Id of the donation to open the timeline on — typically the donor's most recent donation. */
  initialDonationId: string | null;
  /**
   * Donations already loaded by the caller, used only to render the "followup-banner" (amount,
   * campaign, association, date) for whichever donation is currently shown. When the donor
   * navigates (prev/next) to a donation outside this list, the banner is simply omitted rather
   * than showing stale or invented data — the timeline itself keeps working either way.
   */
  knownDonations?: DonorDonationDto[];
  /** Opens the existing "Traçabilité de votre don" modal for the given donation id. */
  onOpenTraceability?: (donationId: string) => void;
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

function fmtEur(amount: number): string {
  return new Intl.NumberFormat('fr-FR', { style: 'currency', currency: 'EUR' }).format(amount);
}

function fmtDate(iso: string): string {
  return new Intl.DateTimeFormat('fr-FR', { day: '2-digit', month: 'short', year: 'numeric' }).format(
    new Date(iso),
  );
}

/**
 * Home-page block "Le parcours de votre don" — a 4-step timeline for a single donation, with
 * previous/next navigation between the donor's own donations, and a summary banner when the
 * shown donation is one already loaded by the caller.
 */
export function DonationJourneyTimeline({ initialDonationId, knownDonations = [], onOpenTraceability }: Props) {
  const t = useTranslations('dashboard.donor.journey');
  const [donationId, setDonationId] = useState(initialDonationId);

  useEffect(() => {
    setDonationId(initialDonationId);
  }, [initialDonationId]);

  const { journey, isLoading, error } = useDonorDonationJourney(donationId);
  const summary = knownDonations.find((d) => d.id === donationId) ?? null;

  if (donationId === null) {
    return (
      <section aria-labelledby="followup-h2">
        <div className="followup-head">
          <div>
            <h2 id="followup-h2">{t('title')}</h2>
          </div>
        </div>
        <EmptyStateCard icon="🧭" title={t('empty.title')} subtitle={t('empty.subtitle')} />
      </section>
    );
  }

  if (isLoading) {
    return (
      <section aria-labelledby="followup-h2">
        <div className="followup-head">
          <div>
            <h2 id="followup-h2">{t('title')}</h2>
          </div>
        </div>
        <p className="text-sm text-text-2 py-4" aria-live="polite">
          {t('loading')}
        </p>
      </section>
    );
  }

  if (error || !journey) {
    return (
      <section aria-labelledby="followup-h2">
        <div className="followup-head">
          <div>
            <h2 id="followup-h2">{t('title')}</h2>
          </div>
        </div>
        <p className="text-sm text-coral py-4" role="alert">
          {t('error')}
        </p>
      </section>
    );
  }

  const steps = orderedSteps(journey.steps);
  const currentStep = steps.find((s) => !s.reached) ?? steps[steps.length - 1];

  return (
    <section aria-labelledby="followup-h2">
      <div className="followup-head">
        <div>
          <h2 id="followup-h2">{t('title')}</h2>
          <p>{t('subtitle')}</p>
        </div>
        <div className="followup-nav" role="group" aria-label={t('navigation')}>
          <button
            type="button"
            className="followup-nav-btn"
            disabled={!journey.previousDonationId}
            onClick={() => setDonationId(journey.previousDonationId)}
            aria-label={t('previous')}
          >
            <span aria-hidden="true">‹</span>
          </button>
          <button
            type="button"
            className="followup-nav-btn"
            disabled={!journey.nextDonationId}
            onClick={() => setDonationId(journey.nextDonationId)}
            aria-label={t('next')}
          >
            <span aria-hidden="true">›</span>
          </button>
        </div>
      </div>

      <div className="followup-card-wrap">
        {summary && (
          <div className="followup-banner">
            <div className="followup-banner-emoji" aria-hidden="true">
              {summary.campaignEmoji}
            </div>
            <div className="followup-banner-info">
              <h3 id="followup-title">
                {t('bannerTitle', { amount: fmtEur(summary.amount), campaign: summary.campaignName })}
              </h3>
              <span>
                {summary.associationName} · {fmtDate(summary.donatedAt)}
              </span>
            </div>
            {onOpenTraceability && (
              <div className="followup-banner-actions">
                <button
                  type="button"
                  className="btn btn-sm btn-secondary"
                  onClick={() => onOpenTraceability(donationId)}
                >
                  <span aria-hidden="true">🔍</span> {t('viewTraceability')}
                </button>
              </div>
            )}
          </div>
        )}

        <div className="followup-body">
          <ol className="timeline" aria-label={t('title')}>
            {steps.map((step) => (
              <li
                key={step.step}
                className="tl-item"
                aria-current={step.step === currentStep?.step ? 'step' : undefined}
              >
                <div
                  className={`tl-dot${step.reached ? ' done' : step.step === currentStep?.step ? ' current' : ''}`}
                  aria-hidden="true"
                >
                  {step.reached ? '✓' : '★'}
                </div>
                <h4>{t(`steps.${step.step}.label`)}</h4>
                {!step.reached && <p>{t(`steps.${step.step}.pendingHint`)}</p>}
                {step.reachedAt && <span className="tl-date">{fmtDate(step.reachedAt)}</span>}
              </li>
            ))}
          </ol>
        </div>
      </div>
    </section>
  );
}
