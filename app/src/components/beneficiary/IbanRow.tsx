'use client';

import { useState } from 'react';
import { useTranslations } from 'next-intl';
import type { BeneficiaryIbanDto } from '@/types/beneficiary';
import { IbanVerificationStatus } from '@/types/beneficiary';
import { VopBanner } from './VopBanner';
import { ConfirmDialog } from '@/components/ui/ConfirmDialog';

interface IbanRowProps {
  /** The IBAN record to display. */
  iban: BeneficiaryIbanDto;
  /** UUID of the parent beneficiary. */
  beneficiaryId: string;
  /** True when this specific IBAN's VOP verification is in-flight. */
  isVerifyingVop: boolean;
  /** Called when the user clicks the delete button. */
  onDeleteIban: (ibanId: string) => void;
  /** Called when the user clicks the VOP verify button. */
  onVerifyVop: (ibanId: string) => void;
}

/** Returns the border color class based on IBAN verification status. */
function borderClass(status: IbanVerificationStatus): string {
  switch (status) {
    case IbanVerificationStatus.VERIFIED:
    case IbanVerificationStatus.FORMAT_VALID:
      return 'border-green/40';
    case IbanVerificationStatus.CLOSE_MATCH:
      return 'border-yellow/40';
    case IbanVerificationStatus.INVALID:
    case IbanVerificationStatus.NO_MATCH:
      return 'border-red/40';
    default:
      return 'border-border';
  }
}

/** Returns a small colored status indicator element. */
function StatusDot({ status }: { status: IbanVerificationStatus }) {
  switch (status) {
    case IbanVerificationStatus.VERIFIED:
      return <span className="text-green text-[12px]">✓</span>;
    case IbanVerificationStatus.FORMAT_VALID:
      return <span className="w-[8px] h-[8px] rounded-full bg-green inline-block flex-shrink-0" />;
    case IbanVerificationStatus.CLOSE_MATCH:
      return <span className="w-[8px] h-[8px] rounded-full bg-yellow inline-block flex-shrink-0" />;
    case IbanVerificationStatus.INVALID:
    case IbanVerificationStatus.NO_MATCH:
      return <span className="w-[8px] h-[8px] rounded-full bg-red inline-block flex-shrink-0" />;
    default:
      return <span className="w-[8px] h-[8px] rounded-full bg-muted inline-block flex-shrink-0" />;
  }
}

/**
 * Single IBAN row displaying the IBAN value, its verification status,
 * action buttons (verify / re-verify / delete), and an optional VOP banner.
 */
export function IbanRow({
  iban,
  isVerifyingVop,
  onDeleteIban,
  onVerifyVop,
}: IbanRowProps) {
  const t = useTranslations('dashboard');
  const [confirmOpen, setConfirmOpen] = useState(false);

  const isReadonly = iban.status === IbanVerificationStatus.VERIFIED;
  const canDelete = iban.status !== IbanVerificationStatus.VERIFIED;

  const renderActions = () => {
    if (isVerifyingVop) {
      return (
        <span className="inline-block w-4 h-4 border-2 border-border border-t-green rounded-full animate-spin" />
      );
    }
    switch (iban.status) {
      case IbanVerificationStatus.PENDING:
        return (
          <button
            onClick={() => onVerifyVop(iban.id)}
            className="text-[12px] text-text-2 hover:text-text transition-colors px-2 py-1 border border-border rounded-[6px]"
          >
            {t('beneficiaries.iban.verify')}
          </button>
        );
      case IbanVerificationStatus.FORMAT_VALID:
        return (
          <>
            <span className="text-[11px] text-green bg-green/10 border border-green/20 rounded-full px-2 py-[2px] cursor-default">
              ✓
            </span>
            <button
              onClick={() => onVerifyVop(iban.id)}
              className="text-[12px] text-black bg-green rounded-[6px] px-3 py-1 hover:opacity-90 transition-opacity whitespace-nowrap"
            >
              {t('beneficiaries.iban.verifyVop')}
            </button>
          </>
        );
      case IbanVerificationStatus.VERIFIED:
        return (
          <span className="text-[11px] text-green bg-green/10 border border-green/20 rounded-full px-2 py-[2px] cursor-default">
            ✓ {t('beneficiaries.iban.verified')}
          </span>
        );
      case IbanVerificationStatus.CLOSE_MATCH:
      case IbanVerificationStatus.NO_MATCH:
      case IbanVerificationStatus.NOT_POSSIBLE:
        return (
          <button
            onClick={() => onVerifyVop(iban.id)}
            className="text-[12px] text-text-2 hover:text-text transition-colors px-2 py-1 border border-border rounded-[6px]"
            title={t('beneficiaries.iban.verify')}
          >
            ⟳
          </button>
        );
      case IbanVerificationStatus.INVALID:
        return (
          <span className="text-[12px] text-red">Invalide</span>
        );
      default:
        return null;
    }
  };

  return (
    <div className="mt-[6px]">
      <div className="flex items-center gap-[8px]">
        <input
          type="text"
          readOnly={isReadonly}
          value={iban.iban}
          onChange={() => {}}
          placeholder={t('beneficiaries.iban.placeholder')}
          className={`flex-1 bg-bg-3 border rounded-[8px] px-3 py-[6px] font-mono text-[13px] text-text outline-none ${borderClass(iban.status)} ${isReadonly ? 'opacity-75 cursor-default' : ''}`}
        />

        <StatusDot status={iban.status} />

        <div className="flex items-center gap-[4px]">
          {renderActions()}
        </div>

        {canDelete && (
          <button
            onClick={() => setConfirmOpen(true)}
            className="text-[12px] text-red bg-red/8 hover:bg-red/15 rounded-[6px] px-2 py-1 transition-colors flex-shrink-0"
            title={t('beneficiaries.list.delete')}
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
        title={t('beneficiaries.iban.deleteTitle')}
        message={t('beneficiaries.iban.deleteMessage', { iban: iban.iban })}
        confirmLabel={t('beneficiaries.list.delete')}
        onConfirm={() => { setConfirmOpen(false); onDeleteIban(iban.id); }}
        onCancel={() => setConfirmOpen(false)}
      />
    </div>
  );
}
