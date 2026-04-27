'use client';

import { useState, useEffect, useCallback, useRef } from 'react';
import { useTranslations } from 'next-intl';
import { getMoneriumAuthUrl } from '@/lib/api/monerium';
import { useToastStore } from '@/stores/toastStore';

interface MoneriumOnboardModalProps {
  isOpen: boolean;
  onClose: () => void;
  /** Called when the OAuth2 popup signals MONERIUM_CONNECTED — use to refresh connection status. */
  onConnected: () => void;
}

/**
 * Guides the user through the Monerium OAuth2 PKCE onboarding flow.
 * Opens a popup window for the OAuth consent screen and listens for a postMessage
 * from the success popup page to update the connection state without a full page reload.
 */
export default function MoneriumOnboardModal({ isOpen, onClose, onConnected }: MoneriumOnboardModalProps) {
  const t = useTranslations('dashboard');
  const { addToast } = useToastStore();
  const [isConnecting, setIsConnecting] = useState(false);
  const popupRef = useRef<Window | null>(null);

  const closePopup = useCallback(() => {
    popupRef.current?.close();
    popupRef.current = null;
  }, []);

  /** Fetches the PKCE auth URL from the backend and opens the Monerium OAuth popup. */
  const handleConnect = useCallback(async () => {
    setIsConnecting(true);
    try {
      const { authUrl } = await getMoneriumAuthUrl();
      popupRef.current = window.open(authUrl, 'monerium-popup', 'width=520,height=700,noopener=no');
    } catch {
      addToast('error', 'dashboard.profile.monerium.errorFetch');
      setIsConnecting(false);
    }
  }, [addToast]);

  /** Listens for postMessage signals from the OAuth popup pages (success/error). */
  useEffect(() => {
    if (!isOpen) return;

    const handleMessage = (event: MessageEvent) => {
      if (event.origin !== window.location.origin) return;

      if (event.data?.type === 'MONERIUM_CONNECTED') {
        closePopup();
        setIsConnecting(false);
        onConnected();
        onClose();
        addToast('success', 'dashboard.profile.monerium.connected');
      } else if (event.data?.type === 'MONERIUM_ERROR') {
        closePopup();
        setIsConnecting(false);
        addToast('error', 'dashboard.profile.monerium.error');
      }
    };

    window.addEventListener('message', handleMessage);
    return () => window.removeEventListener('message', handleMessage);
  }, [isOpen, onConnected, onClose, addToast, closePopup]);

  /** Clean up the popup when the modal is dismissed externally. */
  useEffect(() => {
    if (!isOpen) {
      closePopup();
      setIsConnecting(false);
    }
  }, [isOpen, closePopup]);

  if (!isOpen) return null;

  return (
    <div
      className="fixed inset-0 z-50 flex items-center justify-center bg-black/60 backdrop-blur-sm"
      onClick={onClose}
    >
      <div
        className="bg-bg-2 border border-border rounded-[14px] p-[28px] w-full max-w-[440px] mx-4"
        onClick={(e) => e.stopPropagation()}
      >
        <div className="flex items-center gap-2 mb-3">
          <h2 className="text-[17px] font-semibold text-text">
            {t('profile.monerium.title')}
          </h2>
          <span className="text-[11px] font-medium px-2 py-0.5 rounded-full bg-indigo-100 text-indigo-600 dark:bg-indigo-900/30 dark:text-indigo-400">
            {t('profile.monerium.badge')}
          </span>
        </div>

        <p className="text-[14px] text-text-2 mb-6 leading-relaxed">
          {t('profile.monerium.description')}
        </p>

        {isConnecting ? (
          <div className="flex flex-col items-center gap-3 py-4">
            <div className="w-8 h-8 border-2 border-indigo-500 border-t-transparent rounded-full animate-spin" />
            <p className="text-[13px] text-text-2">{t('profile.monerium.waiting')}</p>
          </div>
        ) : (
          <div className="flex gap-3">
            <button
              onClick={onClose}
              className="flex-1 py-2.5 rounded-[10px] border border-border text-[14px] font-medium text-text-2 hover:bg-bg transition-colors"
            >
              {t('profile.monerium.cancel')}
            </button>
            <button
              onClick={handleConnect}
              className="flex-1 py-2.5 rounded-[10px] bg-indigo-600 text-white text-[14px] font-medium hover:bg-indigo-700 transition-colors"
            >
              {t('profile.monerium.connect')}
            </button>
          </div>
        )}
      </div>
    </div>
  );
}
