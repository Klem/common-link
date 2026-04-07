'use client';

import { useState } from 'react';
import { useTranslations } from 'next-intl';
import type { BeneficiaryDto, BeneficiaryIbanDto, IbanVerificationStatus } from '@/types/beneficiary';
import { IbanRow } from './IbanRow';
import { ConfirmDialog } from '@/components/ui/ConfirmDialog';

interface BeneficiaryRowProps {
  /** The beneficiary to display. */
  beneficiary: BeneficiaryDto;
  /** Called when the user clicks the delete beneficiary button. */
  onDeleteBeneficiary: (id: string) => void;
  /** Called when the user submits a new IBAN via the inline input. */
  onAddIban: (beneficiaryId: string, iban: string) => void;
  /** Called when the user deletes an IBAN. */
  onDeleteIban: (beneficiaryId: string, ibanId: string) => void;
  /** Called when the user triggers VOP verification on an IBAN. */
  onVerifyVop: (beneficiaryId: string, ibanId: string) => void;
  /** UUID of the IBAN currently being VOP-verified, or null. */
  verifyingIbanId: string | null;
}

/**
 * Computes the aggregated status from all IBANs of a beneficiary.
 * Priority: INVALID > NO_MATCH > CLOSE_MATCH > NOT_POSSIBLE > VERIFIED (all) > FORMAT_VALID > PENDING
 */
function computeAggregatedStatus(ibans: BeneficiaryIbanDto[]): IbanVerificationStatus {
  if (ibans.length === 0) return 'PENDING';
  if (ibans.some((i) => i.status === 'INVALID')) return 'INVALID';
  if (ibans.some((i) => i.status === 'NO_MATCH')) return 'NO_MATCH';
  if (ibans.some((i) => i.status === 'CLOSE_MATCH')) return 'CLOSE_MATCH';
  if (ibans.some((i) => i.status === 'NOT_POSSIBLE')) return 'NOT_POSSIBLE';
  if (ibans.every((i) => i.status === 'VERIFIED')) return 'VERIFIED';
  if (ibans.some((i) => i.status === 'FORMAT_VALID')) return 'FORMAT_VALID';
  return 'PENDING';
}

/** Icon and color style for the aggregated status badge. */
function AggregatedStatusBadge({ status }: { status: IbanVerificationStatus }) {
  const t = useTranslations('dashboard');

  const config: Record<IbanVerificationStatus, { icon: string; ring: string; label: string }> = {
    PENDING:      { icon: '⏳', ring: 'bg-muted/20 border-muted/40',    label: t('beneficiaries.status.pending') },
    FORMAT_VALID: { icon: '✓',  ring: 'bg-green/12 border-green/30',    label: t('beneficiaries.status.formatValid') },
    VERIFIED:     { icon: '✓',  ring: 'bg-green/20 border-green/50',    label: t('beneficiaries.status.verified') },
    CLOSE_MATCH:  { icon: '≈',  ring: 'bg-yellow/12 border-yellow/30',  label: t('beneficiaries.status.closeMatch') },
    NO_MATCH:     { icon: '✗',  ring: 'bg-red/12 border-red/30',        label: t('beneficiaries.status.noMatch') },
    NOT_POSSIBLE: { icon: '?',  ring: 'bg-muted/20 border-muted/40',    label: t('beneficiaries.status.notPossible') },
    INVALID:      { icon: '⚠',  ring: 'bg-red/12 border-red/30',        label: t('beneficiaries.status.invalid') },
  };

  const { icon, ring, label } = config[status];

  const textColor: Record<IbanVerificationStatus, string> = {
    PENDING: 'text-muted',
    FORMAT_VALID: 'text-green',
    VERIFIED: 'text-green',
    CLOSE_MATCH: 'text-yellow',
    NO_MATCH: 'text-red',
    NOT_POSSIBLE: 'text-text-2',
    INVALID: 'text-red',
  };

  return (
    <div className="flex flex-col items-center gap-[4px]">
      <div
        className={`w-[34px] h-[34px] rounded-full border flex items-center justify-center text-[14px] ${ring} ${textColor[status]}`}
      >
        {icon}
      </div>
      <span className={`text-[9px] uppercase tracking-wider font-semibold ${textColor[status]}`}>
        {label}
      </span>
    </div>
  );
}

/**
 * A single beneficiary row displaying the aggregated status, name, info chips,
 * IBAN list with actions, and an inline "Add IBAN" input.
 */
export function BeneficiaryRow({
  beneficiary,
  onDeleteBeneficiary,
  onAddIban,
  onDeleteIban,
  onVerifyVop,
  verifyingIbanId,
}: BeneficiaryRowProps) {
  const t = useTranslations('dashboard');
  const [showIbanInput, setShowIbanInput] = useState(false);
  const [ibanValue, setIbanValue] = useState('');
  const [confirmDeleteOpen, setConfirmDeleteOpen] = useState(false);

  const aggregatedStatus = computeAggregatedStatus(beneficiary.ibans);

  const handleAddIban = () => {
    const trimmed = ibanValue.trim();
    if (!trimmed) return;
    onAddIban(beneficiary.id, trimmed);
    setIbanValue('');
    setShowIbanInput(false);
  };

  const handleCancelIban = () => {
    setIbanValue('');
    setShowIbanInput(false);
  };

  return (
    <div className="grid grid-cols-[52px_1fr_auto] gap-[12px] p-[16px]">
      {/* Column 1 — Aggregated status */}
      <div className="flex flex-col items-center pt-[2px]">
        <AggregatedStatusBadge status={aggregatedStatus} />
      </div>

      {/* Column 2 — Details + IBANs */}
      <div className="min-w-0">
        <p className="font-display font-bold text-[15px] text-text leading-tight">
          {beneficiary.name}
        </p>

        {/* Info chips */}
        <div className="flex flex-wrap gap-[6px] mt-[4px]">
          <Chip value={beneficiary.identifier1} />
          {beneficiary.identifier2 && <Chip value={beneficiary.identifier2} />}
          {beneficiary.activityCode && <Chip value={beneficiary.activityCode} />}
          {beneficiary.category && <Chip value={beneficiary.category} />}
        </div>

        {/* IBAN list */}
        <div className="mt-[8px]">
          {beneficiary.ibans.map((iban) => (
            <IbanRow
              key={iban.id}
              iban={iban}
              beneficiaryId={beneficiary.id}
              isVerifyingVop={verifyingIbanId === iban.id}
              onDeleteIban={(ibanId) => onDeleteIban(beneficiary.id, ibanId)}
              onVerifyVop={(ibanId) => onVerifyVop(beneficiary.id, ibanId)}
            />
          ))}
        </div>

        {/* Inline IBAN input */}
        {showIbanInput ? (
          <div className="flex items-center gap-[6px] mt-[8px]">
            <input
              type="text"
              autoFocus
              value={ibanValue}
              onChange={(e) => setIbanValue(e.target.value)}
              onKeyDown={(e) => {
                if (e.key === 'Enter') handleAddIban();
                if (e.key === 'Escape') handleCancelIban();
              }}
              placeholder={t('beneficiaries.iban.inputPlaceholder')}
              className="flex-1 bg-bg-3 border border-border rounded-[8px] px-3 py-[6px] font-mono text-[13px] text-text outline-none focus:border-cyan/40"
            />
            <button
              onClick={handleAddIban}
              className="text-[12px] text-black bg-green rounded-[6px] px-3 py-[6px] hover:opacity-90 transition-opacity"
            >
              {t('beneficiaries.iban.add')}
            </button>
            <button
              onClick={handleCancelIban}
              className="text-[12px] text-text-2 hover:text-text border border-border rounded-[6px] px-3 py-[6px] transition-colors"
            >
              {t('beneficiaries.iban.cancel')}
            </button>
          </div>
        ) : (
          <button
            onClick={() => setShowIbanInput(true)}
            className="mt-[8px] w-full border border-dashed border-border rounded-[8px] px-3 py-[6px] text-[12px] text-muted hover:text-text-2 hover:border-border/70 transition-colors text-left"
          >
            ＋ {t('beneficiaries.iban.addIban')}
          </button>
        )}
      </div>

      {/* Column 3 — Delete beneficiary */}
      <div className="flex flex-col items-end pt-[2px]">
        <button
          onClick={() => setConfirmDeleteOpen(true)}
          className="text-[12px] text-red bg-red/8 hover:bg-red/15 rounded-[8px] px-3 py-2 transition-colors"
          title={t('beneficiaries.list.delete')}
        >
          ✕
        </button>
      </div>

      <ConfirmDialog
        isOpen={confirmDeleteOpen}
        title={t('beneficiaries.list.deleteTitle')}
        message={t('beneficiaries.list.deleteMessage', { name: beneficiary.name })}
        confirmLabel={t('beneficiaries.list.delete')}
        onConfirm={() => { setConfirmDeleteOpen(false); onDeleteBeneficiary(beneficiary.id); }}
        onCancel={() => setConfirmDeleteOpen(false)}
      />
    </div>
  );
}

/** Small info chip for identifiers and metadata. */
function Chip({ value }: { value: string }) {
  return (
    <span className="bg-bg-3 border border-border rounded-[6px] px-[8px] py-[2px] font-mono text-[11px] text-cyan">
      {value}
    </span>
  );
}
