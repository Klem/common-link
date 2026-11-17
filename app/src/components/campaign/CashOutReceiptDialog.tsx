'use client';

import { useEffect, useRef, useState } from 'react';
import { useTranslations } from 'next-intl';
import { fmtDate, fmtEur } from '@/components/campaign/payoutDisplay';
import { fundsErrorCode } from '@/lib/api/campaignFunds';
import { httpStatusOf } from '@/lib/api/helloasso';
import { useToastStore } from '@/stores/toastStore';
import { FundsErrorCode } from '@/types/campaignFunds';
import type { CampaignCashOut } from '@/types/campaignFunds';

interface Props {
  /** The payout to confirm; null = closed. */
  cashOut: CampaignCashOut | null;
  onClose: () => void;
  /** "Transfer received" call (`useCampaignFunds.confirmReceipt`); rejects with the server error. */
  onConfirm: (idCashOut: number) => Promise<void>;
}

/**
 * "Avez-vous reçu ce virement ?" — the association confirms a HelloAsso payout reached its bank account, which makes
 * the payout's donations usable before the settlement delay ends. Shows the issuer, the date, the net amount on the
 * statement (omitted while unknown), what becomes available for this campaign, the rest for its other campaigns or
 * forms (omitted while unknown), and when the funds would become available on their own.
 *
 * The server re-reads the payout at HelloAsso: its refusals (not paid out yet, HelloAsso link gone, payout gone,
 * HelloAsso down) are explained in the dialog, never as a generic toast. A success closes it with a toast.
 * Escape and a click on the backdrop mean "Pas encore"; the focus starts on "Pas encore" and stays in the dialog.
 */
export function CashOutReceiptDialog({ cashOut, onClose, onConfirm }: Props) {
  const t = useTranslations('dashboard.campaigns.payments.funds.receipt');
  const addToast = useToastStore((s) => s.addToast);
  const [submitting, setSubmitting] = useState(false);
  const [errorKey, setErrorKey] = useState<string | null>(null);
  const dialogRef = useRef<HTMLDivElement>(null);
  const notYetRef = useRef<HTMLButtonElement>(null);
  const open = cashOut !== null;

  useEffect(() => {
    if (!open) return;
    setErrorKey(null);
    const previous = document.activeElement as HTMLElement | null;
    notYetRef.current?.focus();
    return () => previous?.focus?.();
  }, [open]);

  useEffect(() => {
    if (!open) return;
    const onKey = (e: KeyboardEvent) => {
      if (e.key === 'Escape') { onClose(); return; }
      if (e.key !== 'Tab' || !dialogRef.current) return;
      const focusable = Array.from(dialogRef.current.querySelectorAll<HTMLElement>('button:not([disabled])'));
      if (focusable.length === 0) return;
      const first = focusable[0];
      const last = focusable[focusable.length - 1];
      if (e.shiftKey && document.activeElement === first) { e.preventDefault(); last.focus(); }
      else if (!e.shiftKey && document.activeElement === last) { e.preventDefault(); first.focus(); }
    };
    document.addEventListener('keydown', onKey);
    return () => document.removeEventListener('keydown', onKey);
  }, [open, onClose]);

  const confirm = async () => {
    if (!cashOut || submitting) return;
    setSubmitting(true);
    setErrorKey(null);
    try {
      await onConfirm(cashOut.idCashOut);
      addToast('success', 'cashOutConfirmed');
      onClose();
    } catch (error) {
      const code = fundsErrorCode(error);
      if (code === FundsErrorCode.CASH_OUT_NOT_CASHED_OUT) setErrorKey('errors.notCashedOut');
      else if (code === FundsErrorCode.HELLOASSO_NOT_LINKED) setErrorKey('errors.notLinked');
      else if (httpStatusOf(error) === 404) setErrorKey('errors.notFound');
      else setErrorKey('errors.unavailable');
    } finally {
      setSubmitting(false);
    }
  };

  const rest = cashOut && cashOut.netTotal != null ? cashOut.netTotal - cashOut.campaignAmount : 0;

  return (
    <div className={`cm-ov${open ? ' open' : ''}`} onClick={(e) => { if (e.target === e.currentTarget) onClose(); }}>
      {cashOut && (
        <div
          ref={dialogRef}
          className="cm-modal"
          role="dialog"
          aria-modal="true"
          aria-labelledby="cashout-ov-title"
          aria-describedby="cashout-ov-sub"
        >
          <div className="cashout-ov-icon" aria-hidden="true">🏦</div>
          <h2 id="cashout-ov-title" className="cashout-ov-title">{t('title')}</h2>
          <div id="cashout-ov-sub" className="cashout-ov-sub">{t('subtitle')}</div>

          <div className="cashout-ov-details">
            <div className="cashout-ov-row"><span>{t('issuer')}</span><span>{t('issuerValue')}</span></div>
            {cashOut.cashOutDate && (
              <div className="cashout-ov-row"><span>{t('issueDate')}</span><span>{fmtDate(cashOut.cashOutDate)}</span></div>
            )}
            {cashOut.netTotal != null && (
              <div className="cashout-ov-row"><span>{t('statementAmount')}</span><strong>{fmtEur(cashOut.netTotal)}</strong></div>
            )}
          </div>

          <div className="cashout-ov-effect">
            {t.rich('effect', { amount: fmtEur(cashOut.campaignAmount), strong: (chunks) => <strong>{chunks}</strong> })}
            {rest > 0 && <> {t('rest', { amount: fmtEur(rest) })}</>}
          </div>

          {cashOut.autoAvailableOn && (
            <div className="cashout-ov-auto">{t('auto', { date: fmtDate(cashOut.autoAvailableOn) })}</div>
          )}

          {errorKey && <div className="cashout-ov-error" role="alert">{t(errorKey)}</div>}

          <div className="cashout-ov-actions">
            <button ref={notYetRef} type="button" className="btn btn-secondary" onClick={onClose}>{t('notYet')}</button>
            <button type="button" className="btn btn-primary" onClick={confirm} disabled={submitting}>
              <span aria-hidden="true">✓</span> {t('confirm')}
            </button>
          </div>
        </div>
      )}
    </div>
  );
}
