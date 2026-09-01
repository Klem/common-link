import { create } from 'zustand';

/**
 * A single toast notification entry displayed by the global `Toast` component.
 * `messageKey` is always an i18n key — never a raw string.
 */
export interface Toast {
  /** Auto-generated UUID used as a React list key and for targeted removal. */
  id: string;
  type: 'success' | 'error' | 'warning';
  /** i18n key resolved by `useTranslations` in the Toast UI component. */
  messageKey: string;
  /** Values interpolated into `messageKey`'s placeholders (e.g. `{seconds}`), if any. */
  values?: Record<string, string | number>;
}

/**
 * Zustand state shape for the global toast notification queue.
 */
interface ToastState {
  /** Ordered list of active toasts. */
  toasts: Toast[];
  /**
   * Enqueues a new toast notification.
   * @param type - Visual severity of the notification.
   * @param messageKey - i18n key for the message text.
   * @param values - Values for `messageKey`'s placeholders, if it has any.
   */
  addToast: (type: Toast['type'], messageKey: string, values?: Record<string, string | number>) => void;
  /**
   * Removes a toast from the queue by its id.
   * Called by the Toast component after its auto-dismiss timer fires.
   * @param id - The UUID of the toast to remove.
   */
  removeToast: (id: string) => void;
}

/**
 * Global Zustand store for toast notifications.
 * Consumed by the `Toast` UI component rendered in the root layout and by
 * the Axios response interceptor for API-level error toasts.
 */
export const useToastStore = create<ToastState>((set) => ({
  toasts: [],
  addToast: (type, messageKey, values) =>
    set((state) => ({
      toasts: [
        ...state.toasts,
        { id: crypto.randomUUID(), type, messageKey, values },
      ],
    })),
  removeToast: (id) =>
    set((state) => ({
      toasts: state.toasts.filter((t) => t.id !== id),
    })),
}));
