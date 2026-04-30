'use client';

import { useEffect, useState } from 'react';
import { useTranslations } from 'next-intl';
import { useToastStore } from '@/stores/toastStore';
import type { Toast as ToastType } from '@/stores/toastStore';

interface ToastItemProps {
  toast: ToastType;
}

function ToastItem({ toast }: ToastItemProps) {
  const t = useTranslations('common');
  const removeToast = useToastStore((s) => s.removeToast);
  const [exiting, setExiting] = useState(false);

  useEffect(() => {
    const exitTimer = setTimeout(() => setExiting(true), 4500);
    const removeTimer = setTimeout(() => removeToast(toast.id), 5000);
    return () => {
      clearTimeout(exitTimer);
      clearTimeout(removeTimer);
    };
  }, [toast.id, removeToast]);

  const alertVariant = { success: 'alert-success', error: 'alert-error', warning: 'alert-warning', info: 'alert-info' }[toast.type] ?? 'alert-info';
  const borderClass = { success: 'border-green', error: 'border-red', warning: 'border-yellow', info: 'border-border' }[toast.type] ?? 'border-border';
  const icon = { success: '✅', error: '🚫', warning: '⚠️', info: '💡' }[toast.type] ?? '💡';

  return (
    <div
      role="alert"
      aria-live="polite"
      className={`alert ${alertVariant} ${borderClass} toast-item animate-slide-in-toast ${
        exiting ? 'opacity-0 translate-y-2' : 'opacity-100 translate-y-0'
      }`}
    >
      <span className="alert-icon">{icon}</span>
      <div>{t(toast.messageKey as Parameters<typeof t>[0])}</div>
    </div>
  );
}

export function Toast() {
  const toasts = useToastStore((s) => s.toasts);
  if (toasts.length === 0) return null;

  return (
    <div className="toast-container">
      {toasts.map((toast) => (
        <ToastItem key={toast.id} toast={toast} />
      ))}
    </div>
  );
}
