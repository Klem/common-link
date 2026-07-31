'use client';

import { useState } from 'react';
import { useTranslations } from 'next-intl';
import { z } from 'zod';
import { approveVerification, rejectVerification } from '@/lib/api/admin';
import { VerificationStatus } from '@/types/association';
import { STATUS_BADGE_CLASS } from '@/components/admin/adminShared';
import { useToastStore } from '@/stores/toastStore';

const rejectSchema = z.object({
  reason: z.string().trim().min(1).max(1000),
});

interface Props {
  associationId: string;
  status: VerificationStatus;
  verifiedAt?: string | null;
  rejectionReason?: string | null;
  onDecisionMade: (newStatus: VerificationStatus, rejectionReason?: string) => void;
  onNeedRefetch: () => void;
}

export function VerificationDecisionPanel({
  associationId,
  status,
  verifiedAt,
  rejectionReason,
  onDecisionMade,
  onNeedRefetch,
}: Props) {
  const t = useTranslations('admin');
  const addToast = useToastStore((s) => s.addToast);

  const [armingApprove, setArmingApprove] = useState(false);
  const [showRejectForm, setShowRejectForm] = useState(false);
  const [reason, setReason] = useState('');
  const [reasonError, setReasonError] = useState<string | null>(null);
  const [isSubmitting, setIsSubmitting] = useState(false);

  // Read-only note for non-PENDING dossiers
  if (status !== VerificationStatus.PENDING) {
    return (
      <div
        style={{
          display: 'flex',
          alignItems: 'center',
          gap: 10,
          fontSize: 14,
          color: 'var(--color-text-2)',
        }}
      >
        <span className={STATUS_BADGE_CLASS[status]}>{t(`status.${status}`)}</span>
        {status === VerificationStatus.VERIFIED && verifiedAt && (
          <span>
            {t('verificationDetail.verifiedOn')}{' '}
            {new Date(verifiedAt).toLocaleDateString(undefined, {
              day: '2-digit',
              month: 'short',
              year: 'numeric',
            })}
          </span>
        )}
        {status === VerificationStatus.REJECTED && rejectionReason && (
          <span style={{ fontStyle: 'italic' }}>{rejectionReason}</span>
        )}
      </div>
    );
  }

  const handleApproveConfirm = async () => {
    setIsSubmitting(true);
    try {
      await approveVerification(associationId);
      onDecisionMade(VerificationStatus.VERIFIED, undefined);
      addToast('success', 'admin.decision.approved');
    } catch (err: unknown) {
      const status =
        err && typeof err === 'object' && 'response' in err
          ? (err as { response?: { status?: number } }).response?.status
          : undefined;
      if (status === 409) {
        addToast('warning', 'admin.decision.notPending');
        onNeedRefetch();
      }
      // 5xx are already handled by the global axios interceptor (toast shown automatically)
    } finally {
      setIsSubmitting(false);
      setArmingApprove(false);
    }
  };

  const handleRejectSubmit = async () => {
    const parsed = rejectSchema.safeParse({ reason });
    if (!parsed.success) {
      const issue = parsed.error.issues[0];
      if (issue.code === 'too_small') {
        setReasonError(t('decision.reasonRequired'));
      } else if (issue.code === 'too_big') {
        setReasonError(t('decision.reasonTooLong'));
      } else {
        setReasonError(t('decision.reasonRequired'));
      }
      return;
    }
    setReasonError(null);
    setIsSubmitting(true);
    try {
      await rejectVerification(associationId, parsed.data.reason);
      onDecisionMade(VerificationStatus.REJECTED, parsed.data.reason);
      addToast('success', 'admin.decision.rejected');
    } catch (err: unknown) {
      const errStatus =
        err && typeof err === 'object' && 'response' in err
          ? (err as { response?: { status?: number } }).response?.status
          : undefined;
      if (errStatus === 409) {
        addToast('warning', 'admin.decision.notPending');
        onNeedRefetch();
      } else if (errStatus === 422) {
        setReasonError(t('decision.reasonRequired'));
      }
      // 5xx handled globally
    } finally {
      setIsSubmitting(false);
    }
  };

  const resetForms = () => {
    setArmingApprove(false);
    setShowRejectForm(false);
    setReason('');
    setReasonError(null);
  };

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 16 }}>
      {/* Approve */}
      <div style={{ display: 'flex', alignItems: 'center', gap: 10, flexWrap: 'wrap' }}>
        {!armingApprove ? (
          <button
            className="btn btn-primary btn-sm"
            disabled={isSubmitting || showRejectForm}
            onClick={() => { resetForms(); setArmingApprove(true); }}
          >
            {t('decision.approve')}
          </button>
        ) : (
          <>
            <span style={{ fontSize: 14, fontWeight: 600 }}>
              {t('decision.confirmApprove')}
            </span>
            <button
              className="btn btn-sm"
              style={{ background: 'var(--color-success)', color: '#fff' }}
              disabled={isSubmitting}
              onClick={handleApproveConfirm}
            >
              ✓
            </button>
            <button
              className="btn btn-secondary btn-sm"
              disabled={isSubmitting}
              onClick={() => setArmingApprove(false)}
            >
              ✕
            </button>
          </>
        )}

        {!armingApprove && !showRejectForm && (
          <button
            className="btn btn-secondary btn-sm"
            disabled={isSubmitting}
            onClick={() => { resetForms(); setShowRejectForm(true); }}
          >
            {t('decision.reject')}
          </button>
        )}
      </div>

      {/* Reject form */}
      {showRejectForm && (
        <div
          style={{
            display: 'flex',
            flexDirection: 'column',
            gap: 10,
            padding: '14px 16px',
            border: '1px solid var(--color-border)',
            borderRadius: 8,
            background: 'rgba(231,76,60,0.04)',
          }}
        >
          <label
            style={{ fontSize: 13, fontWeight: 600, color: 'var(--color-text)' }}
          >
            {t('decision.reasonLabel')}
          </label>
          <textarea
            value={reason}
            onChange={(e) => { setReason(e.target.value); setReasonError(null); }}
            rows={4}
            maxLength={1000}
            disabled={isSubmitting}
            style={{
              width: '100%',
              padding: '8px 10px',
              border: `1px solid ${reasonError ? 'var(--color-error)' : 'var(--color-border)'}`,
              borderRadius: 6,
              fontSize: 14,
              resize: 'vertical',
              background: 'var(--color-bg)',
              color: 'var(--color-text)',
            }}
          />
          {reasonError && (
            <p style={{ fontSize: 12, color: 'var(--color-error)', marginTop: -4 }}>
              {reasonError}
            </p>
          )}
          <p style={{ fontSize: 11, color: 'var(--color-text-2)', textAlign: 'right' }}>
            {reason.trim().length} / 1000
          </p>
          <div style={{ display: 'flex', gap: 8 }}>
            <button
              className="btn btn-sm"
              style={{ background: 'var(--color-error)', color: '#fff' }}
              disabled={isSubmitting}
              onClick={handleRejectSubmit}
            >
              {t('decision.submit')}
            </button>
            <button
              className="btn btn-secondary btn-sm"
              disabled={isSubmitting}
              onClick={() => setShowRejectForm(false)}
            >
              {t('decision.cancel')}
            </button>
          </div>
        </div>
      )}
    </div>
  );
}
