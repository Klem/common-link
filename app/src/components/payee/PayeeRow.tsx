'use client';

import { useState } from 'react';
import { useTranslations } from 'next-intl';
import type { PayeeDto, PayeeIbanDto } from '@/types/payee';
import { IbanVerificationStatus } from '@/types/payee';
import type { PayoutDto } from '@/types/payment';
import { PayoutStatus } from '@/types/payment';
import { getPayeePayouts } from '@/lib/api/payee';
import { isValidIbanFormat } from '@/lib/iban';
import { IbanRow } from './IbanRow';

interface PayeeRowProps {
  payee: PayeeDto;
  onDeletePayee: (id: string) => void;
  onToggleActive: (id: string, active: boolean) => void;
  onAddIban: (payeeId: string, iban: string) => void;
  onDeleteIban: (payeeId: string, ibanId: string) => void;
  onVerifyVop: (payeeId: string, ibanId: string) => void;
  onToggleIbanActive: (payeeId: string, ibanId: string, active: boolean) => void;
  verifyingIbanId: string | null;
}

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

const STATUS_ICON: Record<IbanVerificationStatus, string> = {
  [IbanVerificationStatus.PENDING]:      '⏳',
  [IbanVerificationStatus.FORMAT_VALID]: '✓',
  [IbanVerificationStatus.VERIFIED]:     '✓',
  [IbanVerificationStatus.CLOSE_MATCH]:  '≈',
  [IbanVerificationStatus.NO_MATCH]:     '✗',
  [IbanVerificationStatus.INVALID]:      '✗',
  [IbanVerificationStatus.NOT_POSSIBLE]: '—',
};

const STATUS_CLASS: Record<IbanVerificationStatus, string> = {
  [IbanVerificationStatus.PENDING]:      'pending',
  [IbanVerificationStatus.FORMAT_VALID]: 'format-valid',
  [IbanVerificationStatus.VERIFIED]:     'verified',
  [IbanVerificationStatus.CLOSE_MATCH]:  'close-match',
  [IbanVerificationStatus.NO_MATCH]:     'no-match',
  [IbanVerificationStatus.INVALID]:      'invalid',
  [IbanVerificationStatus.NOT_POSSIBLE]: 'not-possible',
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

const STATUS_TOOLTIP_KEY: Record<IbanVerificationStatus, string> = {
  [IbanVerificationStatus.PENDING]:      'payees.statusTip.pending',
  [IbanVerificationStatus.FORMAT_VALID]: 'payees.statusTip.formatValid',
  [IbanVerificationStatus.VERIFIED]:     'payees.statusTip.verified',
  [IbanVerificationStatus.CLOSE_MATCH]:  'payees.statusTip.closeMatch',
  [IbanVerificationStatus.NO_MATCH]:     'payees.statusTip.noMatch',
  [IbanVerificationStatus.NOT_POSSIBLE]: 'payees.statusTip.notPossible',
  [IbanVerificationStatus.INVALID]:      'payees.statusTip.invalid',
};

export function PayeeRow({
  payee,
  onDeletePayee,
  onToggleActive,
  onAddIban,
  onDeleteIban,
  onVerifyVop,
  onToggleIbanActive,
  verifyingIbanId,
}: PayeeRowProps) {
  const t = useTranslations('dashboard');
  const [showIbanInput, setShowIbanInput] = useState(false);
  const [ibanValue, setIbanValue] = useState('');
  const [ibanFormatError, setIbanFormatError] = useState(false);
  const [pendingDelete, setPendingDelete] = useState(false);
  const [historyOpen, setHistoryOpen] = useState(false);
  const [payouts, setPayouts] = useState<PayoutDto[]>([]);
  const [historyLoading, setHistoryLoading] = useState(false);
  const [historyLoaded, setHistoryLoaded] = useState(false);

  const aggregatedStatus = computeAggregatedStatus(payee.ibans);
  const statusClass = STATUS_CLASS[aggregatedStatus];

  const toggleHistory = async () => {
    const next = !historyOpen;
    setHistoryOpen(next);
    if (next && !historyLoaded) {
      setHistoryLoading(true);
      try {
        const data = await getPayeePayouts(payee.id);
        setPayouts(data);
        setHistoryLoaded(true);
      } finally {
        setHistoryLoading(false);
      }
    }
  };

  const handleAddIban = () => {
    const trimmed = ibanValue.trim();
    if (!trimmed) return;
    if (!isValidIbanFormat(trimmed)) {
      setIbanFormatError(true);
      return;
    }
    setIbanFormatError(false);
    onAddIban(payee.id, trimmed);
    setIbanValue('');
    setShowIbanInput(false);
  };

  return (
    <div className={`payee-card${!payee.active ? ' payee-deactivated' : ''}`}>
      <div className="payee-row">
        {/* Col 1 — status icon */}
        <div className="payee-status-col">
          <div className={`payee-status-icon ${statusClass}`}>
            {STATUS_ICON[aggregatedStatus]}
          </div>
          <span className={`payee-status-lbl ${statusClass}`}>
            {t(STATUS_LABEL_KEY[aggregatedStatus] as Parameters<typeof t>[0])}
          </span>
          <div className="payee-status-tooltip">
            {t(STATUS_TOOLTIP_KEY[aggregatedStatus] as Parameters<typeof t>[0])}
          </div>
        </div>

        {/* Col 2 — name + chips */}
        <div>
          <p className="payee-name">{payee.name}</p>
          <div className="payee-chips">
            {payee.identifier1 && <span className="payee-chip">{payee.identifier1}</span>}
            {payee.identifier2 && <span className="payee-chip">{payee.identifier2}</span>}
            {payee.activityCode && <span className="payee-chip">{payee.activityCode}</span>}
            {payee.category && <span className="payee-chip">{payee.category}</span>}
            {payee.city && <span className="payee-chip">📍 {payee.city}</span>}
            {payee.payeeType === 'PERSON' && (
              <span className="payee-chip">👤 {t('payees.mode.person')}</span>
            )}
          </div>

          {/* IBANs */}
          {payee.ibans.length > 0 && (
            <div className="payee-iban-fields">
              {payee.ibans.map((iban) => (
                <IbanRow
                  key={iban.id}
                  iban={iban}
                  payeeId={payee.id}
                  isVerifyingVop={verifyingIbanId === iban.id}
                  payeeHasPayouts={payee.hasPayouts}
                  onDeleteIban={(ibanId) => onDeleteIban(payee.id, ibanId)}
                  onVerifyVop={(ibanId) => onVerifyVop(payee.id, ibanId)}
                  onToggleActive={(ibanId, active) => onToggleIbanActive(payee.id, ibanId, active)}
                />
              ))}
            </div>
          )}

          {/* Add IBAN */}
          <div className="payee-add-iban-wrap">
            {showIbanInput ? (
              <div>
                <div className="payee-add-iban-row">
                  <input
                    type="text"
                    autoFocus
                    value={ibanValue}
                    onChange={(e) => { setIbanValue(e.target.value); setIbanFormatError(false); }}
                    onKeyDown={(e) => {
                      if (e.key === 'Enter') handleAddIban();
                      if (e.key === 'Escape') { setIbanValue(''); setIbanFormatError(false); setShowIbanInput(false); }
                    }}
                    placeholder={t('payees.iban.inputPlaceholder')}
                    className="fi payee-fi-mono payee-fi-inline"
                  />
                  <button onClick={handleAddIban} className="btn btn-primary btn-sm">
                    {t('payees.iban.add')}
                  </button>
                  <button onClick={() => { setIbanValue(''); setIbanFormatError(false); setShowIbanInput(false); }} className="btn btn-secondary btn-sm">
                    {t('payees.iban.cancel')}
                  </button>
                </div>
                {ibanFormatError && (
                  <p className="fhint error">{t('payees.iban.invalidFormat')}</p>
                )}
              </div>
            ) : (
              <button
                onClick={() => setShowIbanInput(true)}
                className="btn btn-ghost btn-sm payee-add-iban-btn"
              >
                ＋ {t('payees.iban.addIban')}
              </button>
            )}
          </div>
        </div>

        {/* Col 3 — actions */}
        <div className="payee-actions">
          {payee.hasPayouts ? (
            <div className="payee-action-with-tip">
              <button
                className={`btn btn-xs payee-icon-btn ${payee.active ? 'btn-secondary' : 'btn-primary'}`}
                onClick={() => onToggleActive(payee.id, !payee.active)}
              >
                {payee.active ? '⏸' : '▶'}
              </button>
              <div className="payee-status-tooltip">
                {t('payees.list.hasPayoutsTooltip')}
              </div>
            </div>
          ) : pendingDelete ? (
            <div className="flex items-center gap-1 flex-shrink-0">
              <button
                className="btn btn-secondary btn-xs payee-icon-btn"
                onClick={() => setPendingDelete(false)}
                title={t('payees.iban.cancel')}
              >✕</button>
              <button
                className="btn btn-coral btn-xs payee-icon-btn"
                onClick={() => { setPendingDelete(false); onDeletePayee(payee.id); }}
                title={t('payees.list.delete')}
              >✓</button>
            </div>
          ) : (
            <div className="payee-action-with-tip">
              <button
                className="btn btn-ghost payee-btn-danger"
                onClick={() => setPendingDelete(true)}
              >✕</button>
              <div className="payee-status-tooltip">
                {t('payees.list.delete')}
              </div>
            </div>
          )}
        </div>
      </div>

      {/* Transfer history */}
      <button
        className="payee-history-btn"
        aria-expanded={historyOpen}
        onClick={toggleHistory}
      >
        <span className="ht-left">
          📋 {t('payees.history.toggle')}
          {historyLoaded && payouts.length > 0 && (
            <span className="badge-count">
              {payouts.length}
            </span>
          )}
        </span>
        <span className="ht-chev">▾</span>
      </button>

      <div className={`payee-history-body${historyOpen ? ' open' : ''}`}>
        {historyLoading ? (
          <div className="payee-empty"><span className="spinner" /></div>
        ) : payouts.length === 0 ? (
          <div className="payee-empty">{t('payees.history.empty')}</div>
        ) : (
          <>
            {payouts.map((p) => {
              const date = new Date(p.createdAt).toLocaleDateString('fr-FR', {
                day: '2-digit', month: '2-digit', year: 'numeric',
              });
              const isDone = p.status === PayoutStatus.CONFIRMED;
              return (
                <div key={p.id} className="payee-hist-entry">
                  <span className="payee-hist-entry-date">{date}</span>
                  <span className="payee-hist-entry-ref">{p.label}</span>
                  <span className={`payee-hist-status ${isDone ? 'payee-hist-done' : 'payee-hist-pending'}`}>
                    {isDone ? t('payees.history.statusDone') : t('payees.history.statusPending')}
                  </span>
                  <span className="payee-hist-entry-amt">
                    {p.amount.toLocaleString('fr-FR', { minimumFractionDigits: 2 })} €
                  </span>
                </div>
              );
            })}
            <div className="payee-hist-total">
              {t('payees.history.total')} :{' '}
              <strong>
                {payouts
                  .reduce((s, p) => s + p.amount, 0)
                  .toLocaleString('fr-FR', { minimumFractionDigits: 2 })} €
              </strong>
            </div>
          </>
        )}
      </div>
    </div>
  );
}
