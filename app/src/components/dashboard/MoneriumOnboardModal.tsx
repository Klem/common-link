'use client';

import { useState, useEffect, useCallback, useRef } from 'react';
import { useTranslations } from 'next-intl';
import { getMoneriumAuthUrl } from '@/lib/api/monerium';
import { MoneriumPopupMessage } from '@/types/monerium';
import { useToastStore } from '@/stores/toastStore';

interface MoneriumOnboardModalProps {
  isOpen: boolean;
  onClose: () => void;
  /** Called when the OAuth2 popup signals MONERIUM_CONNECTED — use to refresh connection status. */
  onConnected: () => void;
  /** Called whenever the OAuth2 popup closes (success, error, or manual close) — use to refresh connection status. */
  onPopupClosed?: () => void;
}

/**
 * Guides the user through the Monerium OAuth2 PKCE onboarding flow.
 * Opens a popup window for the OAuth consent screen and listens for a postMessage
 * from the success popup page to update the connection state without a full page reload.
 */
export default function MoneriumOnboardModal({ isOpen, onClose, onConnected, onPopupClosed }: MoneriumOnboardModalProps) {
  const t = useTranslations('dashboard.association');
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
      addToast('error', 'moneriumErrorFetch');
      setIsConnecting(false);
    }
  }, [addToast]);

  /** Listens for postMessage signals from the OAuth popup pages (success/error). */
  useEffect(() => {
    if (!isOpen) return;

    const handleMessage = (event: MessageEvent) => {
      if (event.origin !== window.location.origin) return;

      if (event.data?.type === MoneriumPopupMessage.CONNECTED) {
        closePopup();
        setIsConnecting(false);
        onConnected();
        onClose();
        addToast('success', 'moneriumConnected');
      } else if (event.data?.type === MoneriumPopupMessage.ERROR) {
        closePopup();
        setIsConnecting(false);
        addToast('error', 'moneriumError');
        onClose();
        onPopupClosed?.();
      }
    };

    window.addEventListener('message', handleMessage);
    return () => window.removeEventListener('message', handleMessage);
  }, [isOpen, onConnected, onClose, addToast, closePopup]);

  /** Detects when the user manually closes the popup window and refreshes the status. */
  useEffect(() => {
    if (!isConnecting) return;
    const poll = setInterval(() => {
      if (popupRef.current?.closed) {
        clearInterval(poll);
        popupRef.current = null;
        setIsConnecting(false);
        onClose();
        onPopupClosed?.();
      }
    }, 500);
    return () => clearInterval(poll);
  }, [isConnecting, onClose, onPopupClosed]);

  /** Clean up the popup when the modal is dismissed externally. */
  useEffect(() => {
    if (!isOpen) {
      closePopup();
      setIsConnecting(false);
    }
  }, [isOpen, closePopup]);

  if (!isOpen) return null;

  return (
    <div className="modal-backdrop" onClick={onClose}>
      <div className="modal" onClick={(e) => e.stopPropagation()}>
        <div className="modal-header">
          <div className="flex items-center gap-2">
            <h2 className="font-semibold text-text">{t('profile.monerium.title')}</h2>
            <span className="badge badge-info">{t('profile.monerium.badge')}</span>
          </div>
          <button className="modal-close" onClick={onClose} aria-label="close">×</button>
        </div>
        <div className="modal-body">
          <p className="text-sm text-text-2 leading-relaxed mb-6">{t('profile.monerium.description')}</p>
          {isConnecting && (
            <div className="flex flex-col items-center gap-3 py-4">
              <div className="w-8 h-8 border-2 border-indigo-500 border-t-transparent rounded-full animate-spin" />
              <p className="text-sm text-text-2">{t('profile.monerium.waiting')}</p>
            </div>
          )}
        </div>
        {!isConnecting && (
          <div className="modal-footer">
            <button onClick={onClose} className="btn btn-ghost btn-md">
              {t('profile.monerium.cancel')}
            </button>
            <button onClick={handleConnect} className="btn btn-primary btn-md">
              {t('profile.monerium.connect')}
            </button>
          </div>
        )}
      </div>
    </div>
  );
}
