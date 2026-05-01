'use client';

import { useState } from 'react';
import { useTranslations } from 'next-intl';
import type { PayeeDto, PayeeIbanDto } from '@/types/payee';
import { IbanVerificationStatus } from '@/types/payee';
import { IbanRow } from './IbanRow';
import { ConfirmDialog } from '@/components/ui/ConfirmDialog';

interface PayeeRowProps {
  /** The payee to display. */
  payee: PayeeDto;
  /** Called when the user clicks the delete payee button. */
  onDeletePayee: (id: string) => void;
  /** Called when the user submits a new IBAN via the inline input. */
  onAddIban: (payeeId: string, iban: string) => void;
  /** Called when the user deletes an IBAN. */
  onDeleteIban: (payeeId: string, ibanId: string) => void;
  /** Called when the user triggers VOP verification on an IBAN. */
  onVerifyVop: (payeeId: string, ibanId: string) => void;
  /** UUID of the IBAN currently being VOP-verified, or null. */
  verifyingIbanId: string | null;
}

/**
 * Computes the aggregated status from all IBANs of a payee.
 * Priority: INVALID > NO_MATCH > CLOSE_MATCH > NOT_POSSIBLE > VERIFIED (all) > FORMAT_VALID > PENDING
 */
function computeAggregatedStatus(ibans: PayeeIbanDto[]): IbanVerificationStatus {
  if (ibans.length === 0) return IbanVerificationStatus.PENDING;
  if (ibans.some((i) => i.status === IbanVerificationStatus.INVALID)) return IbanVerificationStatus.INVALID;
  if (ibans.some((i) => i.status === IbanVerificationStatus.NO_MATCH)) return IbanVerificationStatus.NO_MATCH;
  if (ibans.some((i) => i.status === IbanVerificationStatus.CLOSE_MATCH)) return IbanVerificationStatus.CLOSE_MATCH;
  if (ibans.some((i) => i.status === IbanVerificationStatus.NOT_POSSIBLE)) return IbanVerificationStatus.NOT_POSSIBLE;
  if (ibans.every((i) => i.status === IbanVerificationStatus.VERIFIED)) return IbanVerificationStatus.VERIFIED;
  if (ibans.some((i) => i.status === IbanVerificationStatus.FORMAT_VALID)) return IbanVerificationStatus.FORMAT_VALID;
  return IbanVerificationStatus.PENDING;
}

const STATUS_BADGE: Record<IbanVerificationStatus, string> = {
  [IbanVerificationStatus.VERIFIED]:     'badge badge-success',
  [IbanVerificationStatus.FORMAT_VALID]: 'badge badge-success',
  [IbanVerificationStatus.CLOSE_MATCH]:  'badge badge-warning',
  [IbanVerificationStatus.NO_MATCH]:     'badge badge-error',
  [IbanVerificationStatus.INVALID]:      'badge badge-error',
  [IbanVerificationStatus.PENDING]:      'badge badge-neutral',
  [IbanVerificationStatus.NOT_POSSIBLE]: 'badge badge-neutral',
};

const STATUS_LABEL_KEY: Record<IbanVerificationStatus, string> = {
  [IbanVerificationStatus.PENDING]:      'payees.status.pending',
  [IbanVerificationStatus.FORMAT_VALID]: 'payees.status.formatValid',
  [IbanVerificationStatus.VERIFIED]:     'payees.status.verified',
  [IbanVerificationStatus.CLOSE_MATCH]:  'payees.status.closeMatch',
  [IbanVerificationStatus.NO_MATCH]:     'payees.status.noMatch',
  [IbanVerificationStatus.NOT_POSSIBLE]: 'payees.status.notPossible',
  [IbanVerificationStatus.INVALID]:      'payees.status.invalid',
};

/** Returns the first 1–2 uppercase initials from a name. */
function getInitials(name: string): string {
  return name
    .split(' ')
    .slice(0, 2)
    .map((w) => w[0])
    .join('')
    .toUpperCase();
}

/**
 * A single payee row displaying the avatar, name, first IBAN, aggregated VOP badge,
 * IBAN list with actions, and an inline "Add IBAN" input.
 */
export function PayeeRow({
  payee,
  onDeletePayee,
  onAddIban,
  onDeleteIban,
  onVerifyVop,
  verifyingIbanId,
}: PayeeRowProps) {
  const t = useTranslations('dashboard');
  const [showIbanInput, setShowIbanInput] = useState(false);
  const [ibanValue, setIbanValue] = useState('');
  const [confirmDeleteOpen, setConfirmDeleteOpen] = useState(false);

  const aggregatedStatus = computeAggregatedStatus(payee.ibans);
  const firstIban = payee.ibans[0];
  const truncatedIban = firstIban
    ? firstIban.iban.replace(/\s/g, '').slice(0, 16) + '…'
    : null;

  const handleAddIban = () => {
    const trimmed = ibanValue.trim();
    if (!trimmed) return;
    onAddIban(payee.id, trimmed);
    setIbanValue('');
    setShowIbanInput(false);
  };

  const handleCancelIban = () => {
    setIbanValue('');
    setShowIbanInput(false);
  };

  return (
    <div className="p-4">
      {/* Header row: avatar + name/iban/badge + delete */}
      <div className="flex flex-col gap-3 sm:flex-row sm:items-center">
        <div className="avatar avatar-md avatar-indigo flex-shrink-0">
          {getInitials(payee.name)}
        </div>

        <div className="flex-1 min-w-0">
          <p className="font-semibold text-text text-sm leading-tight">{payee.name}</p>
          <div className="flex flex-wrap items-center gap-2 mt-1">
            {truncatedIban && (
              <span className="text-text-2 font-mono text-xs">{truncatedIban}</span>
            )}
            <span className={STATUS_BADGE[aggregatedStatus]}>
              {t(STATUS_LABEL_KEY[aggregatedStatus] as Parameters<typeof t>[0])}
            </span>
          </div>
          {/* Info chips */}
          <div className="flex flex-wrap gap-1.5 mt-1.5">
            <Chip value={payee.identifier1} />
            {payee.identifier2 && <Chip value={payee.identifier2} />}
            {payee.activityCode && <Chip value={payee.activityCode} />}
            {payee.category && <Chip value={payee.category} />}
          </div>
        </div>

        <div className="flex items-center gap-2 sm:flex-shrink-0">
          <button
            onClick={() => setConfirmDeleteOpen(true)}
            className="btn btn-icon-only btn-sm text-error hover:bg-error/10"
            title={t('payees.list.delete')}
          >
            ✕
          </button>
        </div>
      </div>

      {/* IBAN list */}
      {payee.ibans.length > 0 && (
        <div className="mt-3">
          {payee.ibans.map((iban) => (
            <IbanRow
              key={iban.id}
              iban={iban}
              payeeId={payee.id}
              isVerifyingVop={verifyingIbanId === iban.id}
              onDeleteIban={(ibanId) => onDeleteIban(payee.id, ibanId)}
              onVerifyVop={(ibanId) => onVerifyVop(payee.id, ibanId)}
            />
          ))}
        </div>
      )}

      {/* Inline IBAN input */}
      <div className="mt-3">
        {showIbanInput ? (
          <div className="flex items-center gap-2">
            <input
              type="text"
              autoFocus
              value={ibanValue}
              onChange={(e) => setIbanValue(e.target.value)}
              onKeyDown={(e) => {
                if (e.key === 'Enter') handleAddIban();
                if (e.key === 'Escape') handleCancelIban();
              }}
              placeholder={t('payees.iban.inputPlaceholder')}
              className="form-input flex-1 font-mono text-sm"
            />
            <button onClick={handleAddIban} className="btn btn-primary btn-sm">
              {t('payees.iban.add')}
            </button>
            <button onClick={handleCancelIban} className="btn btn-ghost btn-sm">
              {t('payees.iban.cancel')}
            </button>
          </div>
        ) : (
          <button
            onClick={() => setShowIbanInput(true)}
            className="w-full border border-dashed border-border rounded-lg px-3 py-1.5 text-xs text-text-2 hover:text-text hover:border-border/70 transition-colors text-left"
          >
            ＋ {t('payees.iban.addIban')}
          </button>
        )}
      </div>

      <ConfirmDialog
        isOpen={confirmDeleteOpen}
        title={t('payees.list.deleteTitle')}
        message={t('payees.list.deleteMessage', { name: payee.name })}
        confirmLabel={t('payees.list.delete')}
        onConfirm={() => { setConfirmDeleteOpen(false); onDeletePayee(payee.id); }}
        onCancel={() => setConfirmDeleteOpen(false)}
      />
    </div>
  );
}

/** Small info chip for identifiers and metadata. */
function Chip({ value }: { value: string }) {
  return (
    <span className="badge badge-neutral font-mono text-xs">
      {value}
    </span>
  );
}
