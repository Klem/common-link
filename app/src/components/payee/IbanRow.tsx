'use client';

import { useState } from 'react';
import { useTranslations } from 'next-intl';
import type { PayeeIbanDto } from '@/types/payee';
import { IbanVerificationStatus } from '@/types/payee';

interface IbanRowProps {
  /** The IBAN record to display. */
  iban: PayeeIbanDto;
  /** UUID of the parent payee. */
  payeeId: string;
  /** True when this specific IBAN's VOP verification is in-flight. */
  isVerifyingVop: boolean;
  /** True when the parent payee already has at least one payout — gates whether a VERIFIED
   *  IBAN can still be hard-deleted or must be disabled instead (audit trail). */
  payeeHasPayouts: boolean;
  /** Called when the user clicks the delete button. */
  onDeleteIban: (ibanId: string) => void;
  /** Called when the user clicks the VOP verify button. */
  onVerifyVop: (ibanId: string) => void;
  /** Called when the user enables/disables this IBAN. */
  onToggleActive: (ibanId: string, active: boolean) => void;
}

/**
 * Single IBAN row displaying the IBAN value, its verification status,
 * action buttons (copy / verify / disable / delete), and an optional VOP banner.
 *
 * A VERIFIED IBAN can be disabled instead of deleted to preserve the audit trail once payouts
 * exist; a disabled IBAN is shown greyed out and excluded from payout selection.
 */
export function IbanRow({
  iban,
  isVerifyingVop,
  payeeHasPayouts,
  onDeleteIban,
  onVerifyVop,
  onToggleActive,
}: IbanRowProps) {
  const t = useTranslations('dashboard');
  const [pendingDelete, setPendingDelete] = useState(false);

  const renderActions = () => {
    if (isVerifyingVop) {
      return (
        <span className="rm-spinner" />
      );
    }
    switch (iban.status) {
      case IbanVerificationStatus.PENDING:
        return (
          <button
            onClick={() => onVerifyVop(iban.id)}
            className="btn btn-icon-only btn-sm"
            title={t('payees.iban.verify')}
          >
            ⟳
          </button>
        );
      case IbanVerificationStatus.FORMAT_VALID:
        return (
          <button
            onClick={() => onVerifyVop(iban.id)}
            className="btn btn-icon-only btn-sm"
            title={t('payees.iban.verifyVop')}
          >
            ⟳
          </button>
        );
      case IbanVerificationStatus.VERIFIED:
        return (
          <span className="badge badge-success text-xs">
            ✓ {t('payees.iban.verified')}
          </span>
        );
      case IbanVerificationStatus.CLOSE_MATCH:
      case IbanVerificationStatus.NO_MATCH:
      case IbanVerificationStatus.NOT_POSSIBLE:
        return (
          <button
            onClick={() => onVerifyVop(iban.id)}
            className="btn btn-icon-only btn-sm"
            title={t('payees.iban.verify')}
          >
            ⟳
          </button>
        );
      case IbanVerificationStatus.INVALID:
        return (
          <span className="badge badge-error text-xs">{t('payees.status.invalid')}</span>
        );
      default:
        return null;
    }
  };

  /** Trailing action area: delete, disable, both, or re-enable, depending on status/active/payouts. */
  const renderTrailingAction = () => {
    if (!iban.active) {
      return (
        <button
          onClick={() => onToggleActive(iban.id, true)}
          className="btn btn-secondary btn-sm flex-shrink-0"
          title={t('payees.iban.enable')}
        >
          {t('payees.iban.enable')}
        </button>
      );
    }

    if (pendingDelete) {
      return (
        <div className="flex items-center gap-1 flex-shrink-0">
          <button
            onClick={() => setPendingDelete(false)}
            className="rm-btn-cancel-iban-del"
            title={t('payees.iban.cancel')}
          >
            ✕
          </button>
          <button
            onClick={() => { setPendingDelete(false); onDeleteIban(iban.id); }}
            className="rm-btn-confirm-iban-del"
            title={t('payees.list.delete')}
          >
            ✓
          </button>
        </div>
      );
    }

    const disableButton = (
      <button
        onClick={() => onToggleActive(iban.id, false)}
        className="btn btn-secondary btn-sm flex-shrink-0"
        title={t('payees.iban.disable')}
      >
        {t('payees.iban.disable')}
      </button>
    );

    if (iban.status === IbanVerificationStatus.VERIFIED && payeeHasPayouts) {
      // Audit trail: a VERIFIED IBAN that already received a payout can only be disabled.
      return disableButton;
    }

    return (
      <div className="flex items-center gap-1 flex-shrink-0">
        {iban.status === IbanVerificationStatus.VERIFIED && disableButton}
        <button
          onClick={() => setPendingDelete(true)}
          className="rm-btn-del-iban flex-shrink-0"
          title={t('payees.list.delete')}
        >
          🗑
        </button>
      </div>
    );
  };

  return (
    <div className={`mt-2${!iban.active ? ' rm-iban-disabled' : ''}`}>
      <div className="flex items-center gap-2">
        <input
          type="text"
          readOnly
          value={iban.iban}
          onChange={() => {}}
          placeholder={t('payees.iban.placeholder')}
          className="cm-fi-mono cm-fi-readonly"
        />

        {/* Copy button */}
        <button
          onClick={() => navigator.clipboard.writeText(iban.iban)}
          className="btn btn-icon-only btn-sm"
          title={t('payees.iban.copy')}
        >
          📋
        </button>

        <div className="flex items-center gap-1 flex-shrink-0">
          {renderActions()}
        </div>

        {renderTrailingAction()}
      </div>
    </div>
  );
}
