'use client';

import { useEffect } from 'react';
import { useTranslations } from 'next-intl';

interface ConfirmDialogProps {
  /** Whether the dialog is visible. */
  isOpen: boolean;
  /** Title shown in the dialog header. */
  title: string;
  /** Body message shown below the title. */
  message: string;
  /** Label for the confirm button (default: i18n "confirm"). */
  confirmLabel?: string;
  /** Label for the cancel button (default: i18n "cancel"). */
  cancelLabel?: string;
  /** Visual variant: default = primary, danger = coral/red. */
  variant?: 'default' | 'danger';
  /** Called when the user confirms the action. */
  onConfirm: () => void;
  /** Called when the user cancels or dismisses the dialog. */
  onCancel: () => void;
}

/**
 * Lightweight modal asking the user to confirm a destructive action.
 *
 * Closes on Escape key or backdrop click.
 * The confirm button is styled in red to signal a destructive operation.
 */
export function ConfirmDialog({
  isOpen,
  title,
  message,
  confirmLabel,
  cancelLabel,
  variant = 'danger',
  onConfirm,
  onCancel,
}: ConfirmDialogProps) {
  const t = useTranslations('common');

  useEffect(() => {
    if (!isOpen) return;
    const handler = (e: KeyboardEvent) => {
      if (e.key === 'Escape') onCancel();
    };
    window.addEventListener('keydown', handler);
    return () => window.removeEventListener('keydown', handler);
  }, [isOpen, onCancel]);

  if (!isOpen) return null;

  const confirmBtnClass = variant === 'danger' ? 'btn btn-coral btn-sm' : 'btn btn-primary btn-sm';

  return (
    <div
      className="modal-backdrop"
      onClick={onCancel}
      role="dialog"
      aria-modal="true"
    >
      <div
        className="modal"
        onClick={(e) => e.stopPropagation()}
      >
        <div className="modal-header">
          <h3>{title}</h3>
          <button className="modal-close" onClick={onCancel} aria-label={t('cancel')}>×</button>
        </div>
        <div className="modal-body">
          <p className="text-sm text-text-2">{message}</p>
        </div>
        <div className="modal-footer">
          <button onClick={onCancel} className="btn btn-ghost btn-sm">
            {cancelLabel ?? t('cancel')}
          </button>
          <button onClick={onConfirm} className={confirmBtnClass}>
            {confirmLabel ?? t('confirm')}
          </button>
        </div>
      </div>
    </div>
  );
}
