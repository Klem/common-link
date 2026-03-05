import { create } from 'zustand';

export interface Toast {
  id: string;
  type: 'success' | 'error' | 'warning';
  messageKey: string;
}

interface ToastState {
  toasts: Toast[];
  addToast: (type: Toast['type'], messageKey: string) => void;
  removeToast: (id: string) => void;
}

export const useToastStore = create<ToastState>((set) => ({
  toasts: [],
  addToast: (type, messageKey) =>
    set((state) => ({
      toasts: [
        ...state.toasts,
        { id: crypto.randomUUID(), type, messageKey },
      ],
    })),
  removeToast: (id) =>
    set((state) => ({
      toasts: state.toasts.filter((t) => t.id !== id),
    })),
}));
