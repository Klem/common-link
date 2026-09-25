'use client';

import { useTranslations } from 'next-intl';
import { SidePanel } from '@/components/campaign/SidePanel';
import {
  badgeClass, badgeDotClass, fmtDateTime, fmtEur,
} from '@/components/campaign/payoutDisplay';
import {
  PayoutAction,
  PayoutUiState,
  payoutAction,
  payoutErrorMessageKey,
  payoutUiState,
} from '@/types/payment';
import type { PayoutDto } from '@/types/payment';

interface Props {
  /** The payout to detail, or null when the panel is closed. */
  payout: PayoutDto | null;
  /** Bridge's transaction id, or empty when no transfer has been ordered. */
  reference: string;
  /** Human label of its accounting code. */
  typeLabel: string;
  awaitingReturnPayoutId: string | null;
  isSaving: boolean;
  onRetry: (payoutId: string) => void;
  onClose: () => void;
}

/**
 * Everything the journal's six columns cannot hold, for one payout.
 *
 * Exists so the table can stay at six columns: the destination IBAN, the object the association
 * typed, the exact timestamps and the full refusal sentence all live here rather than crowding a
 * row. The refusal sentence in particular is the reason a rejected row is worth opening at all.
 */
export function PayoutDetailPanel({
  payout, reference, typeLabel, awaitingReturnPayoutId, isSaving, onRetry, onClose,
}: Props) {
  const t = useTranslations('dashboard.campaigns.payments');

  if (!payout) return null;

  const state = payoutUiState(payout);
  const action = payoutAction(payout, awaitingReturnPayoutId);
  // Only while the failure still describes where the payout stands: the backend clears the error
  // when it attaches a fresh link, but a stale one must never caption a transfer that went through.
  const hasFailure = payout.bridgeLastErrorCode != null
    && (state === PayoutUiState.FAILED || state === PayoutUiState.RETRYABLE);

  return (
    <SidePanel
      isOpen
      title={t('detail.title')}
      closeLabel={t('journal.close')}
      onClose={onClose}
    >
      <div className="pd-amount">{fmtEur(payout.amount)}</div>
      <div className="pd-badge-row">
        <span className={badgeClass(state)}>
          <span className={badgeDotClass(state)} aria-hidden="true" />
          {t(`state.${state}`)}
        </span>
      </div>

      {/*
        The reason is translated from a stable code, never printed from the stored string: that
        string mixes Bridge's bare ISO codes with our own English messages, one of which carries a
        payout id, and it used to go into a tooltip verbatim.
      */}
      {hasFailure && (
        <p className="pd-reason">{t(`history.${payoutErrorMessageKey(payout.bridgeLastErrorCode)}`)}</p>
      )}

      <dl className="pd-list">
        {/* Absent until a transfer has actually been ordered — nothing is invented in its place. */}
        {reference && (
          <div className="pd-item">
            <dt>{t('detail.reference')}</dt>
            <dd className="mono">{reference}</dd>
          </div>
        )}
        <div className="pd-item">
          <dt>{t('detail.payee')}</dt>
          <dd>{payout.payeeName}</dd>
        </div>
        <div className="pd-item">
          <dt>{t('detail.iban')}</dt>
          <dd className="mono">{payout.ibanValue}</dd>
        </div>
        <div className="pd-item">
          <dt>{t('detail.type')}</dt>
          <dd>
            {typeLabel} <span className="mono">{payout.typeCode}</span>
          </dd>
        </div>
        <div className="pd-item">
          <dt>{t('detail.createdAt')}</dt>
          <dd>{fmtDateTime(payout.createdAt)}</dd>
        </div>
        {payout.confirmedAt && (
          <div className="pd-item">
            <dt>{t('detail.confirmedAt')}</dt>
            <dd>{fmtDateTime(payout.confirmedAt)}</dd>
          </div>
        )}
      </dl>

      {state === PayoutUiState.AUTHORISED || state === PayoutUiState.IN_TRANSIT ? (
        <p className="pd-note">{t('history.inTransit')}</p>
      ) : null}

      <div className="pd-actions">
        {action === PayoutAction.AWAITING_RETURN && (
          <p className="pd-note">{t('history.awaitingBank')}</p>
        )}
        {action === PayoutAction.RETRY && (
          <button
            type="button"
            className="cm-btn cm-btn-primary"
            title={t('history.retryHint')}
            disabled={isSaving}
            onClick={() => onRetry(payout.id)}
          >
            {t('history.retry')}
          </button>
        )}
        {action === PayoutAction.AUTHORISE && payout.bridgeCheckoutUrl && (
          <a
            className="cm-btn cm-btn-primary"
            href={payout.bridgeCheckoutUrl}
            title={t('history.authoriseHint')}
          >
            {t('history.authorise')}
          </a>
        )}
      </div>
    </SidePanel>
  );
}
