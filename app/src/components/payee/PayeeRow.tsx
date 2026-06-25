'use client';

import { useState } from 'react';
import { useTranslations } from 'next-intl';
import type { PayeeDto, PayeeIbanDto } from '@/types/payee';
import { IbanVerificationStatus } from '@/types/payee';
import type { PayoutDto } from '@/types/payment';
import { PayoutStatus } from '@/types/payment';
import { getPayeePayouts } from '@/lib/api/payee';
import { IbanRow } from './IbanRow';
import { ConfirmDialog } from '@/components/ui/ConfirmDialog';

interface PayeeRowProps {
  payee: PayeeDto;
  onDeletePayee: (id: string) => void;
  onToggleActive: (id: string, active: boolean) => void;
  onAddIban: (payeeId: string, iban: string) => void;
  onDeleteIban: (payeeId: string, ibanId: string) => void;
  onVerifyVop: (payeeId: string, ibanId: string) => void;
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
  verifyingIbanId,
}: PayeeRowProps) {
  const t = useTranslations('dashboard');
  const [showIbanInput, setShowIbanInput] = useState(false);
  const [ibanValue, setIbanValue] = useState('');
  const [confirmDeleteOpen, setConfirmDeleteOpen] = useState(false);
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
    onAddIban(payee.id, trimmed);
    setIbanValue('');
    setShowIbanInput(false);
  };

  return (
    <div className={`rm-recip-card${!payee.active ? ' rm-deactivated' : ''}`}>
      <div className="rm-recip-row">
        {/* Col 1 — status icon */}
        <div className="rm-status-col">
          <div className={`rm-status-icon ${statusClass}`}>
            {STATUS_ICON[aggregatedStatus]}
          </div>
          <span className={`rm-status-lbl ${statusClass}`}>
            {t(STATUS_LABEL_KEY[aggregatedStatus] as Parameters<typeof t>[0])}
          </span>
          <div className="rm-status-tooltip">
            {t(STATUS_TOOLTIP_KEY[aggregatedStatus] as Parameters<typeof t>[0])}
          </div>
        </div>

        {/* Col 2 — name + chips */}
        <div>
          <p className="rm-recip-name">{payee.name}</p>
          <div className="rm-chips">
            {payee.identifier1 && <span className="rm-chip">{payee.identifier1}</span>}
            {payee.identifier2 && <span className="rm-chip">{payee.identifier2}</span>}
            {payee.activityCode && <span className="rm-chip">{payee.activityCode}</span>}
            {payee.category && <span className="rm-chip">{payee.category}</span>}
            {payee.city && <span className="rm-chip">📍 {payee.city}</span>}
            {payee.payeeType === 'PERSON' && (
              <span className="rm-chip">👤 {t('payees.mode.person')}</span>
            )}
          </div>

          {/* IBANs */}
          {payee.ibans.length > 0 && (
            <div className="rm-iban-fields" style={{ marginTop: 10 }}>
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

          {/* Add IBAN */}
          <div style={{ marginTop: 8 }}>
            {showIbanInput ? (
              <div style={{ display: 'flex', gap: 6, alignItems: 'center' }}>
                <input
                  type="text"
                  autoFocus
                  value={ibanValue}
                  onChange={(e) => setIbanValue(e.target.value)}
                  onKeyDown={(e) => {
                    if (e.key === 'Enter') handleAddIban();
                    if (e.key === 'Escape') { setIbanValue(''); setShowIbanInput(false); }
                  }}
                  placeholder={t('payees.iban.inputPlaceholder')}
                  className="cm-fi"
                  style={{ flex: 1, fontFamily: 'monospace', fontSize: 13 }}
                />
                <button onClick={handleAddIban} className="cm-btn cm-btn-primary cm-btn-sm">
                  {t('payees.iban.add')}
                </button>
                <button onClick={() => { setIbanValue(''); setShowIbanInput(false); }} className="cm-btn cm-btn-ghost cm-btn-sm">
                  {t('payees.iban.cancel')}
                </button>
              </div>
            ) : (
              <button
                onClick={() => setShowIbanInput(true)}
                className="cm-btn cm-btn-ghost cm-btn-sm"
                style={{ fontSize: 12 }}
              >
                ＋ {t('payees.iban.addIban')}
              </button>
            )}
          </div>
        </div>

        {/* Col 3 — actions */}
        <div className="rm-recip-actions">
          {payee.hasPayouts ? (
            <div className="rm-action-with-tip">
              <button
                className={payee.active ? 'rm-btn-deactivate' : 'rm-btn-reactivate'}
                onClick={() => onToggleActive(payee.id, !payee.active)}
              >
                {payee.active ? '⏸' : '▶'}
              </button>
              <div className="rm-status-tooltip">
                {t('payees.list.hasPayoutsTooltip')}
              </div>
            </div>
          ) : (
            <div className="rm-action-with-tip">
              <button
                className="rm-btn-delete"
                onClick={() => setConfirmDeleteOpen(true)}
              >✕</button>
              <div className="rm-status-tooltip">
                {t('payees.list.delete')}
              </div>
            </div>
          )}
        </div>
      </div>

      {/* Transfer history */}
      <button
        className="rm-history-btn"
        aria-expanded={historyOpen}
        onClick={toggleHistory}
      >
        <span className="ht-left">
          📋 {t('payees.history.toggle')}
          {historyLoaded && payouts.length > 0 && (
            <span className="badge-count" style={{ background: 'var(--deep-indigo)', marginLeft: 6 }}>
              {payouts.length}
            </span>
          )}
        </span>
        <span className="ht-chev">▾</span>
      </button>

      <div className={`rm-history-body${historyOpen ? ' open' : ''}`}>
        {historyLoading ? (
          <div className="rm-empty-recip"><span className="rm-spinner" /></div>
        ) : payouts.length === 0 ? (
          <div className="rm-empty-recip">{t('payees.history.empty')}</div>
        ) : (
          <>
            {payouts.map((p) => {
              const date = new Date(p.createdAt).toLocaleDateString('fr-FR', {
                day: '2-digit', month: '2-digit', year: 'numeric',
              });
              const isDone = p.status === PayoutStatus.CONFIRMED;
              return (
                <div key={p.id} className="rm-hist-entry">
                  <span className="rm-hist-entry-date">{date}</span>
                  <span className="rm-hist-entry-ref">{p.label}</span>
                  <span className={`rm-hist-status ${isDone ? 'rm-hist-done' : 'rm-hist-pending'}`}>
                    {isDone ? t('payees.history.statusDone') : t('payees.history.statusPending')}
                  </span>
                  <span className="rm-hist-entry-amt">
                    {p.amount.toLocaleString('fr-FR', { minimumFractionDigits: 2 })} €
                  </span>
                </div>
              );
            })}
            <div className="rm-hist-total">
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
