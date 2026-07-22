'use client';

import { useState, useEffect, useCallback, useRef } from 'react';
import { useTranslations } from 'next-intl';
import { getMollieAuthUrl } from '@/lib/api/mollie-connect';
import { MolliePopupMessage } from '@/types/mollie-connect';
import { useToastStore } from '@/stores/toastStore';

interface MollieOnboardModalProps {
  isOpen: boolean;
  onClose: () => void;
  /** Called when the OAuth2 popup signals MOLLIE_KYC_CONNECTED — use to refresh connection status. */
  onConnected: () => void;
  /** Called whenever the OAuth2 popup closes (success, error, or manual close) — use to refresh connection status. */
  onPopupClosed?: () => void;
}

/**
 * Guides the user through the Mollie Connect OAuth2 onboarding flow.
 * Opens a popup window for the OAuth consent screen and listens for a postMessage
 * from the success popup page to update the connection state without a full page reload.
 */
export default function MollieOnboardModal({ isOpen, onClose, onConnected, onPopupClosed }: MollieOnboardModalProps) {
  const t = useTranslations('settings');
  const { addToast } = useToastStore();
  const [isConnecting, setIsConnecting] = useState(false);
  const popupRef = useRef<Window | null>(null);

  const closePopup = useCallback(() => {
    popupRef.current?.close();
    popupRef.current = null;
  }, []);

  /** Fetches the auth URL from the backend and opens the Mollie OAuth popup. */
  const handleConnect = useCallback(async () => {
    setIsConnecting(true);
    try {
      const { authUrl } = await getMollieAuthUrl();
      popupRef.current = window.open(authUrl, 'mollie-popup', 'width=520,height=700,noopener=no');
    } catch {
      addToast('error', 'mollieErrorFetch');
      setIsConnecting(false);
    }
  }, [addToast]);

  /** Listens for postMessage signals from the OAuth popup pages (success/error). */
  useEffect(() => {
    if (!isOpen) return;

    const handleMessage = (event: MessageEvent) => {
      if (event.origin !== window.location.origin) return;

      if (event.data?.type === MolliePopupMessage.CONNECTED) {
        closePopup();
        setIsConnecting(false);
        onConnected();
        onClose();
        addToast('success', 'mollieConnected');
      } else if (event.data?.type === MolliePopupMessage.ERROR) {
        closePopup();
        setIsConnecting(false);
        addToast('error', 'mollieError');
        onClose();
        onPopupClosed?.();
      }
    };

    window.addEventListener('message', handleMessage);
    return () => window.removeEventListener('message', handleMessage);
  }, [isOpen, onConnected, onClose, addToast, closePopup, onPopupClosed]);

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
          <h2 className="font-semibold text-text">{t('mollie.modal.title')}</h2>
          <button className="modal-close" onClick={onClose} aria-label="close">×</button>
        </div>
        <div className="modal-body">
          <p className="text-sm text-text-2 leading-relaxed mb-6">{t('mollie.modal.description')}</p>
          {isConnecting && (
            <div className="flex flex-col items-center gap-3 py-4">
              <div className="w-8 h-8 border-2 border-indigo-500 border-t-transparent rounded-full animate-spin" />
              <p className="text-sm text-text-2">{t('mollie.modal.waiting')}</p>
            </div>
          )}
        </div>
        {!isConnecting && (
          <div className="modal-footer">
            <button onClick={onClose} className="btn btn-ghost btn-md">
              {t('mollie.modal.cancel')}
            </button>
            <button onClick={handleConnect} className="btn btn-primary btn-md">
              {t('mollie.modal.connect')}
            </button>
          </div>
        )}
      </div>
    </div>
  );
}
