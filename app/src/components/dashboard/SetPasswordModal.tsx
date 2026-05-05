'use client';

import { useState } from 'react';
import { useTranslations } from 'next-intl';
import { SetPasswordForm } from '@/components/auth/SetPasswordForm';
import { useToastStore } from '@/stores/toastStore';
import api from '@/lib/api';

interface SetPasswordModalProps {
  isOpen: boolean;
  onClose: () => void;
}

export function SetPasswordModal({ isOpen, onClose }: SetPasswordModalProps) {
  const t = useTranslations('dashboard');
  const { addToast } = useToastStore();
  const [loading, setLoading] = useState(false);

  if (!isOpen) return null;

  const handleSubmit = async (password: string): Promise<void> => {
    setLoading(true);
    try {
      await api.patch('/api/user/me/password', { password, confirmPassword: password });
      addToast('success', 'setPasswordUpdated');
      onClose();
    } catch {
      addToast('error', 'errors.genericError');
    } finally {
      setLoading(false);
    }
  };

  const handleSkip = () => {
    onClose();
  };

  return (
    <div className="modal-backdrop">
      <div className="modal">
        <div className="modal-header">
          <h2 className="font-display font-bold text-text">{t('setPassword.title')}</h2>
          <button className="modal-close" onClick={onClose} aria-label="close">×</button>
        </div>
        <div className="modal-body">
          <p className="text-sm text-text-2 leading-relaxed mb-4">{t('setPassword.subtitle')}</p>
          <SetPasswordForm onSubmit={handleSubmit} onSkip={handleSkip} loading={loading} />
        </div>
      </div>
    </div>
  );
}
