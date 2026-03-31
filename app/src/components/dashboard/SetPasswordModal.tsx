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
      addToast('success', 'dashboard.setPassword.success');
      onClose();
    } catch {
      addToast('error', 'common.errors.genericError');
    } finally {
      setLoading(false);
    }
  };

  const handleSkip = () => {
    onClose();
  };

  return (
    <div className="fixed inset-0 bg-black/60 backdrop-blur-sm z-50 flex items-center justify-center">
      <div className="bg-bg-2 border border-border rounded-[14px] p-[28px] max-w-[420px] w-full mx-4">
        <h2 className="font-display text-[18px] font-bold text-text mb-[6px]">
          {t('setPassword.title')}
        </h2>
        <p className="text-[12.5px] text-text-2 leading-[1.65] mb-[18px]">
          {t('setPassword.subtitle')}
        </p>
        <SetPasswordForm
          onSubmit={handleSubmit}
          onSkip={handleSkip}
          loading={loading}
        />
      </div>
    </div>
  );
}
