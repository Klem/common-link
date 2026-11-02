'use client';

import { useEffect, useState } from 'react';
import { useTranslations } from 'next-intl';
import { changePaymentProvider, getPaymentProviderPreview } from '@/lib/api/admin';
import { ConfirmDialog } from '@/components/ui/ConfirmDialog';
import { PAYMENT_PROVIDER_REASON_MAX_LENGTH } from '@/types/admin';
import type { PaymentProviderPreview } from '@/types/admin';
import { ProviderReadinessStatus } from '@/types/paymentProvider';
import type { PaymentProviderId } from '@/types/paymentProvider';

interface Props {
  associationId: string;
}

/** Badge class per readiness; `null` (provider not integrated yet) reads as neutral. */
const READINESS_BADGE: Record<ProviderReadinessStatus, string> = {
  [ProviderReadinessStatus.READY]: 'badge badge-success',
  [ProviderReadinessStatus.PENDING]: 'badge badge-warning',
  [ProviderReadinessStatus.BROKEN]: 'badge badge-error',
  [ProviderReadinessStatus.NOT_CONNECTED]: 'badge badge-neutral',
};

/** Validation mirrored from `PaymentProviderChangeService` (rule 8): required after trim, bounded length. */
const reasonError = (reason: string): 'reasonRequired' | 'reasonTooLong' | null => {
  const trimmed = reason.trim();
  if (trimmed.length === 0) return 'reasonRequired';
  if (trimmed.length > PAYMENT_PROVIDER_REASON_MAX_LENGTH) return 'reasonTooLong';
  return null;
};

/**
 * Curator card of an association's payment provider: the current provider and its readiness, then a two-step change —
 * pick the target and give a reason, then confirm in a dialog that spells out the impact (live campaigns, widget,
 * donations in flight, readiness at the target). Loads independently by `associationId`.
 */
export function PaymentProviderPanel({ associationId }: Props) {
  const t = useTranslations('curator.dossier.paymentProvider');

  const [preview, setPreview] = useState<PaymentProviderPreview | null>(null);
  const [loading, setLoading] = useState(true);
  const [failed, setFailed] = useState(false);
  const [target, setTarget] = useState<PaymentProviderId | null>(null);
  const [reason, setReason] = useState('');
  const [showReasonError, setShowReasonError] = useState(false);
  const [confirming, setConfirming] = useState(false);
  const [submitting, setSubmitting] = useState(false);
  const [outcome, setOutcome] = useState<'success' | 'invalidReason' | 'failed' | null>(null);

  useEffect(() => {
    let active = true;
    setLoading(true);
    setFailed(false);
    getPaymentProviderPreview(associationId)
      .then((p) => { if (active) setPreview(p); })
      .catch(() => { if (active) setFailed(true); })
      .finally(() => { if (active) setLoading(false); });
    return () => { active = false; };
  }, [associationId]);

  if (loading) {
    return <div className="cm-card"><p className="form-hint">{t('loading')}</p></div>;
  }
  if (failed || !preview) {
    return <div className="cm-card"><p className="form-error">{t('error')}</p></div>;
  }

  const readinessOf = (provider: PaymentProviderId) =>
    preview.providers.find((p) => p.provider === provider)?.readiness ?? null;
  const readinessLabel = (provider: PaymentProviderId) => t(`readiness.${readinessOf(provider) ?? 'UNAVAILABLE'}`);
  const readinessBadge = (provider: PaymentProviderId) => {
    const readiness = readinessOf(provider);
    return readiness ? READINESS_BADGE[readiness] : 'badge badge-neutral';
  };

  const targets = preview.providers.map((p) => p.provider).filter((p) => p !== preview.currentProvider);
  const selectedTarget = target ?? targets[0] ?? null;
  const currentError = reasonError(reason);

  const askConfirmation = () => {
    setOutcome(null);
    if (currentError) {
      setShowReasonError(true);
      return;
    }
    setConfirming(true);
  };

  const confirm = () => {
    if (!selectedTarget) return;
    setConfirming(false);
    setSubmitting(true);
    changePaymentProvider(associationId, { provider: selectedTarget, reason: reason.trim() })
      .then((updated) => {
        setPreview(updated);
        setTarget(null);
        setReason('');
        setShowReasonError(false);
        setOutcome('success');
      })
      .catch((err) => {
        const status = (err as { response?: { status?: number } })?.response?.status;
        setOutcome(status === 422 ? 'invalidReason' : 'failed');
      })
      .finally(() => setSubmitting(false));
  };

  const from = t(`providers.${preview.currentProvider}`);
  const to = selectedTarget ? t(`providers.${selectedTarget}`) : '';

  return (
    <div className="cm-card">
      <div className="cm-card-title">{t('title')}</div>

      <p>
        {t('current')} : <strong>{from}</strong>{' '}
        <span className={readinessBadge(preview.currentProvider)}>{readinessLabel(preview.currentProvider)}</span>
      </p>

      {selectedTarget && (
        <>
          <label className="cm-label" htmlFor="payment-provider-target">{t('target')}</label>
          <select
            id="payment-provider-target"
            className="cm-fi"
            value={selectedTarget}
            onChange={(e) => setTarget(e.target.value as PaymentProviderId)}
            disabled={submitting}
          >
            {targets.map((p) => (
              <option key={p} value={p}>{`${t(`providers.${p}`)} — ${readinessLabel(p)}`}</option>
            ))}
          </select>

          <label className="cm-label" htmlFor="payment-provider-reason">{t('reason')}</label>
          <textarea
            id="payment-provider-reason"
            className="cm-fi"
            rows={3}
            value={reason}
            maxLength={PAYMENT_PROVIDER_REASON_MAX_LENGTH}
            onChange={(e) => setReason(e.target.value)}
            aria-invalid={showReasonError && currentError !== null}
            aria-describedby="payment-provider-reason-hint"
            disabled={submitting}
          />
          <p id="payment-provider-reason-hint" className="form-hint">
            {t('reasonHint', { count: reason.trim().length, max: PAYMENT_PROVIDER_REASON_MAX_LENGTH })}
          </p>
          {showReasonError && currentError && (
            <p className="form-error" role="alert">{t(currentError, { max: PAYMENT_PROVIDER_REASON_MAX_LENGTH })}</p>
          )}

          <button type="button" className="btn btn-primary btn-sm" onClick={askConfirmation} disabled={submitting}>
            {t('submit')}
          </button>
        </>
      )}

      {outcome === 'success' && <p className="form-hint" role="status">{t('success')}</p>}
      {outcome === 'invalidReason' && (
        <p className="form-error" role="alert">{t('invalidReason', { max: PAYMENT_PROVIDER_REASON_MAX_LENGTH })}</p>
      )}
      {outcome === 'failed' && <p className="form-error" role="alert">{t('failed')}</p>}

      <ConfirmDialog
        isOpen={confirming}
        title={t('confirmTitle')}
        message={t('confirmMessage', {
          from,
          to,
          liveCampaigns: preview.liveCampaigns,
          widget: preview.widgetEnabled ? t('widgetOn') : t('widgetOff'),
          pendingDonations: preview.pendingDonations,
          targetReadiness: selectedTarget ? readinessLabel(selectedTarget) : '',
        })}
        confirmLabel={t('confirm')}
        variant="default"
        onConfirm={confirm}
        onCancel={() => setConfirming(false)}
      />
    </div>
  );
}
