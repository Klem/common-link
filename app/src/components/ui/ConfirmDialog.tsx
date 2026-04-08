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

  return (
    <div
      className="fixed inset-0 bg-black/50 backdrop-blur-[4px] z-50 flex items-center justify-center p-4"
      onClick={onCancel}
      role="dialog"
      aria-modal="true"
    >
      <div
        className="bg-bg-2 border border-border rounded-xl p-[24px] max-w-[420px] w-full"
        onClick={(e) => e.stopPropagation()}
      >
        <h2 className="font-display font-bold text-[16px] text-text mb-[8px]">{title}</h2>
        <p className="text-[13px] text-text-2 mb-[20px]">{message}</p>
        <div className="flex justify-end gap-[8px]">
          <button
            onClick={onCancel}
            className="text-[13px] text-text-2 hover:text-text border border-border rounded-[8px] px-4 py-2 transition-colors"
          >
            {t('cancel')}
          </button>
          <button
            onClick={onConfirm}
            className="text-[13px] text-white bg-red hover:opacity-90 rounded-[8px] px-4 py-2 transition-opacity font-semibold"
          >
            {confirmLabel ?? t('confirm')}
          </button>
        </div>
      </div>
    </div>
  );
}
