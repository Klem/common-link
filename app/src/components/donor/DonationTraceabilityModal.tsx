'use client';

import { useEffect, useRef, useState } from 'react';
import { useTranslations } from 'next-intl';
import { Donut, type DonutSlice } from '@/components/ui/Donut';
import { useDonorDonationJourney } from '@/hooks/dashboard/useDonorDonationJourney';

interface Props {
  /** Id of the donation to show traceability for, or null when the modal is closed. */
  donationId: string | null;
  onClose: () => void;
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
 * Modal "Traçabilité de votre don" — amount, allocation (used/remaining), the confirmed payouts
 * it funded, and a "compte vérifié" sub-modal explaining on-chain verification.
 *
 * No public/shareable link block: the public proof page is deferred (D3 → CD3).
 */
export function DonationTraceabilityModal({ donationId, onClose }: Props) {
  const t = useTranslations('dashboard.donor.traceability');
  const { journey, isLoading, error } = useDonorDonationJourney(donationId);
  const [verifiedOpen, setVerifiedOpen] = useState(false);
  const closeButtonRef = useRef<HTMLButtonElement>(null);

  useEffect(() => {
    if (donationId === null) return;
    closeButtonRef.current?.focus();

    function handleKeyDown(e: KeyboardEvent) {
      if (e.key !== 'Escape') return;
      if (verifiedOpen) setVerifiedOpen(false);
      else onClose();
    }
    document.addEventListener('keydown', handleKeyDown);
    return () => document.removeEventListener('keydown', handleKeyDown);
  }, [donationId, verifiedOpen, onClose]);

  if (donationId === null) return null;

  return (
    <div className="modal-backdrop" onClick={onClose}>
      <div
        className="modal modal-scroll"
        role="dialog"
        aria-modal="true"
        aria-label={t('title')}
        onClick={(e) => e.stopPropagation()}
      >
        <div className="modal-header">
          <h2 className="font-display font-bold text-text">{t('title')}</h2>
          <button ref={closeButtonRef} className="modal-close" onClick={onClose} aria-label={t('close')}>
            ×
          </button>
        </div>
        <div className="modal-body">
          {isLoading && (
            <p className="text-sm text-text-2" aria-live="polite">
              {t('loading')}
            </p>
          )}
          {error && (
            <p className="text-sm text-coral" role="alert">
              {t('error')}
            </p>
          )}
          {journey && (
            <div className="flex flex-col gap-4">
              <dl className="grid grid-cols-2 gap-3 text-sm">
                <div>
                  <dt className="text-text-2 text-xs">{t('usedAmount')}</dt>
                  <dd className="font-display font-bold">{fmtEur(journey.usedAmount)}</dd>
                </div>
                <div>
                  <dt className="text-text-2 text-xs">{t('remainingAmount')}</dt>
                  <dd className="font-display font-bold">{fmtEur(journey.remainingAmount)}</dd>
                </div>
              </dl>

              <Donut
                slices={[
                  ...journey.fundedPayouts.map((share) => ({
                    label: share.label,
                    value: share.amountImputed,
                  } satisfies DonutSlice)),
                  ...(journey.remainingAmount > 0
                    ? [{ label: t('chart.remaining'), value: journey.remainingAmount, color: 'var(--color-text-2)' }]
                    : []),
                ]}
                emptyKey="donor.traceability.chartEmpty"
              />

              <div>
                <h3 className="font-display font-bold text-sm mb-2">{t('fundedPayouts.title')}</h3>
                {journey.fundedPayouts.length === 0 ? (
                  <p className="text-sm text-text-2">{t('fundedPayouts.empty')}</p>
                ) : (
                  <ul className="flex flex-col gap-2">
                    {journey.fundedPayouts.map((share) => (
                      <li key={share.payoutId} className="flex items-center justify-between text-sm">
                        <span>
                          {share.label} <span className="text-text-2 text-xs">— {fmtDate(share.confirmedAt)}</span>
                        </span>
                        <span className="font-display font-bold">{fmtEur(share.amountImputed)}</span>
                      </li>
                    ))}
                  </ul>
                )}
              </div>

              <button
                type="button"
                className="btn btn-ghost btn-sm self-start"
                onClick={() => setVerifiedOpen(true)}
              >
                {t('verifiedAccount.cta')}
              </button>
            </div>
          )}
        </div>
      </div>

      {verifiedOpen && (
        <div className="modal-backdrop" onClick={() => setVerifiedOpen(false)}>
          <div
            className="modal"
            role="dialog"
            aria-modal="true"
            aria-label={t('verifiedAccount.title')}
            onClick={(e) => e.stopPropagation()}
          >
            <div className="modal-header">
              <h2 className="font-display font-bold text-text">{t('verifiedAccount.title')}</h2>
              <button className="modal-close" onClick={() => setVerifiedOpen(false)} aria-label={t('close')}>
                ×
              </button>
            </div>
            <div className="modal-body">
              <p className="text-sm text-text-2 leading-relaxed">{t('verifiedAccount.body')}</p>
            </div>
            <div className="modal-footer">
              <button className="btn btn-primary btn-md" onClick={() => setVerifiedOpen(false)}>
                {t('close')}
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
