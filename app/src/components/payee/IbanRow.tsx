'use client';

import { useState } from 'react';
import { useTranslations } from 'next-intl';
import type { PayeeIbanDto } from '@/types/payee';
import { IbanVerificationStatus } from '@/types/payee';
import { VopBanner } from './VopBanner';
import { ConfirmDialog } from '@/components/ui/ConfirmDialog';

interface IbanRowProps {
  /** The IBAN record to display. */
  iban: PayeeIbanDto;
  /** UUID of the parent payee. */
  payeeId: string;
  /** True when this specific IBAN's VOP verification is in-flight. */
  isVerifyingVop: boolean;
  /** Called when the user clicks the delete button. */
  onDeleteIban: (ibanId: string) => void;
  /** Called when the user clicks the VOP verify button. */
  onVerifyVop: (ibanId: string) => void;
}

/** Maps IBAN verification status to a form-input modifier class. */
function inputStatusClass(status: IbanVerificationStatus): string {
  switch (status) {
    case IbanVerificationStatus.VERIFIED:
    case IbanVerificationStatus.FORMAT_VALID:
      return ' success';
    case IbanVerificationStatus.INVALID:
    case IbanVerificationStatus.NO_MATCH:
      return ' error';
    default:
      return '';
  }
}

/**
 * Single IBAN row displaying the IBAN value, its verification status,
 * action buttons (copy / verify / delete), and an optional VOP banner.
 */
export function IbanRow({
  iban,
  isVerifyingVop,
  onDeleteIban,
  onVerifyVop,
}: IbanRowProps) {
  const t = useTranslations('dashboard');
  const [confirmOpen, setConfirmOpen] = useState(false);

  const canDelete = iban.status !== IbanVerificationStatus.VERIFIED;

  const renderActions = () => {
    if (isVerifyingVop) {
      return (
        <span className="inline-block w-5 h-5 border-2 border-border border-t-green rounded-full animate-spin-around flex-shrink-0" />
      );
    }
    switch (iban.status) {
      case IbanVerificationStatus.PENDING:
        return (
          <button
            onClick={() => onVerifyVop(iban.id)}
            className="btn btn-secondary btn-xs whitespace-nowrap"
          >
            {t('payees.iban.verify')}
          </button>
        );
      case IbanVerificationStatus.FORMAT_VALID:
        return (
          <button
            onClick={() => onVerifyVop(iban.id)}
            className="btn btn-primary btn-xs whitespace-nowrap"
          >
            {t('payees.iban.verifyVop')}
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

  return (
    <div className="mt-2">
      <div className="flex items-center gap-2">
        <input
          type="text"
          readOnly
          value={iban.iban}
          onChange={() => {}}
          placeholder={t('payees.iban.placeholder')}
          className={`form-input flex-1 font-mono text-sm cursor-default${inputStatusClass(iban.status)}`}
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

        {canDelete && (
          <button
            onClick={() => setConfirmOpen(true)}
            className="btn btn-icon-only btn-sm text-error hover:bg-error/10 flex-shrink-0"
            title={t('payees.list.delete')}
          >
            ✕
          </button>
        )}
      </div>

      {iban.vopResult && (
        <VopBanner vopResult={iban.vopResult} suggestedName={iban.vopSuggestedName} />
      )}

      <ConfirmDialog
        isOpen={confirmOpen}
        title={t('payees.iban.deleteTitle')}
        message={t('payees.iban.deleteMessage', { iban: iban.iban })}
        confirmLabel={t('payees.list.delete')}
        onConfirm={() => { setConfirmOpen(false); onDeleteIban(iban.id); }}
        onCancel={() => setConfirmOpen(false)}
      />
    </div>
  );
}
