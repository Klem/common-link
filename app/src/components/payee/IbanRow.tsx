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
  /** Called when the user clicks the delete button. */
  onDeleteIban: (ibanId: string) => void;
  /** Called when the user clicks the VOP verify button. */
  onVerifyVop: (ibanId: string) => void;
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
  const [pendingDelete, setPendingDelete] = useState(false);

  const canDelete = iban.status !== IbanVerificationStatus.VERIFIED;

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
          className="cm-fi"
          style={{ flex: 1, fontFamily: 'monospace', fontSize: 13, cursor: 'default' }}
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
          pendingDelete ? (
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
          ) : (
            <button
              onClick={() => setPendingDelete(true)}
              className="rm-btn-del-iban flex-shrink-0"
              title={t('payees.list.delete')}
            >
              🗑
            </button>
          )
        )}
      </div>
    </div>
  );
}
