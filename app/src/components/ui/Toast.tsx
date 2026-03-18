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

  const borderClass = { success: 'border-green', error: 'border-red', warning: 'border-yellow' }[toast.type];
  const textClass = { success: 'text-green', error: 'text-red', warning: 'text-yellow' }[toast.type];

  return (
    <div
      role="alert"
      aria-live="polite"
      className={`bg-bg-2 border ${borderClass} rounded-[11px] px-[17px] py-[13px] max-w-[290px] shadow-[0_8px_40px_rgba(0,0,0,.6)] transition-all duration-500 animate-slide-in-toast ${
        exiting ? 'opacity-0 translate-y-2' : 'opacity-100 translate-y-0'
      }`}
    >
      <div className={`font-display text-[13.5px] font-bold ${textClass}`}>
        {t(toast.messageKey as Parameters<typeof t>[0])}
      </div>
    </div>
  );
}

export function Toast() {
  const toasts = useToastStore((s) => s.toasts);
  if (toasts.length === 0) return null;

  return (
    <div className="fixed bottom-5 right-5 z-50 flex flex-col gap-2 items-end">
      {toasts.map((toast) => (
        <ToastItem key={toast.id} toast={toast} />
      ))}
    </div>
  );
}
