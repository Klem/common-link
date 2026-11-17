'use client';

import { useEffect, useRef, useState } from 'react';
import type { ReactNode } from 'react';
import { useLocale, useTranslations } from 'next-intl';
import { getDisconnectPreview } from '@/lib/api/helloasso';
import { fmtEur } from '@/components/campaign/payoutDisplay';
import { formatDay } from './helloAssoFlow';

const strong = (chunks: ReactNode) => <strong>{chunks}</strong>;

interface DisconnectModalProps {
  /** When the client secret was discarded (shown in the reminder to regenerate the key). */
  secretWipedAt: string | null;
  /** True while `DELETE /connection` is in flight. */
  busy: boolean;
  onCancel: () => void;
  onConfirm: () => void;
}

/**
 * "Se déconnecter de HelloAsso ?" warning (mockup `disconnect-modal`): `role="alertdialog"`, focus on "Annuler" when
 * it opens; Escape, a click on the backdrop and "Annuler" close it. Reminds that donation buttons pause, that received
 * donations stay, and advises regenerating the key. Focus return is handled by the caller.
 *
 * When HelloAsso has not paid out some donations yet (CEO decision Q5), warns with their number and amount: they can
 * no longer be tracked once disconnected, and the CommonLink team can make them available on the bank statement line.
 * The disconnection stays possible; a failed preview simply shows no warning.
 */
export default function DisconnectModal({ secretWipedAt, busy, onCancel, onConfirm }: DisconnectModalProps) {
  const t = useTranslations('settings.helloasso.onboarding.disconnect');
  const locale = useLocale();
  const cancelRef = useRef<HTMLButtonElement>(null);
  const wipedOn = formatDay(secretWipedAt, locale);
  const [notYetPaidOut, setNotYetPaidOut] = useState<{ count: number; amount: number } | null>(null);

  useEffect(() => {
    let cancelled = false;
    getDisconnectPreview()
      .then((p) => { if (!cancelled && p.notYetPaidOutCount > 0) setNotYetPaidOut({ count: p.notYetPaidOutCount, amount: p.notYetPaidOutAmount }); })
      .catch(() => { /* no warning: the disconnection must stay possible */ });
    return () => { cancelled = true; };
  }, []);

  useEffect(() => {
    cancelRef.current?.focus();
    const onKey = (e: KeyboardEvent) => {
      if (e.key === 'Escape') onCancel();
    };
    document.addEventListener('keydown', onKey);
    return () => document.removeEventListener('keydown', onKey);
  }, [onCancel]);

  return (
    <div
      className="modal-backdrop"
      data-testid="hao-disconnect-backdrop"
      onClick={(e) => {
        if (e.target === e.currentTarget) onCancel();
      }}
    >
      <div className="modal" role="alertdialog" aria-modal="true" aria-labelledby="hao-dm-title" aria-describedby="hao-dm-body">
        <div className="modal-header">
          <h3 id="hao-dm-title">{t('title')}</h3>
          <button type="button" className="modal-close" aria-label={t('close')} onClick={onCancel}>
            <span aria-hidden="true">×</span>
          </button>
        </div>
        <div className="modal-body" id="hao-dm-body">
          <div className="alert alert-warning hao-alert-bottom">
            <span className="alert-icon" aria-hidden="true">⏸️</span>
            <div>{t.rich('pause', { strong })}</div>
          </div>
          {notYetPaidOut && (
            <div className="alert alert-warning hao-alert-bottom" data-testid="hao-disconnect-not-paid-out">
              <span className="alert-icon" aria-hidden="true">💶</span>
              <div>
                {t.rich('notYetPaidOut', { strong, count: notYetPaidOut.count, amount: fmtEur(notYetPaidOut.amount) })}{' '}
                {t('notYetPaidOutRelease', { count: notYetPaidOut.count })}
              </div>
            </div>
          )}
          <p className="small">{t('erase')}</p>
          <p className="small hao-note-spaced">{wipedOn ? t('secret', { date: wipedOn }) : t('secretNoDate')}</p>
        </div>
        <div className="modal-footer">
          <button ref={cancelRef} type="button" className="btn btn-ghost btn-sm" onClick={onCancel}>
            {t('cancel')}
          </button>
          <button type="button" className="btn btn-coral btn-sm" onClick={onConfirm} disabled={busy}>
            {busy ? t('confirming') : t('confirm')}
          </button>
        </div>
      </div>
    </div>
  );
}
