'use client';

import { useState } from 'react';
import { useTranslations } from 'next-intl';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { z } from 'zod';
import { useAuthStore } from '@/stores/authStore';
import { useDonorProfile } from '@/hooks/dashboard/useDonorProfile';
import { Topbar } from '@/components/dashboard/Topbar';
import { SetPasswordForm } from '@/components/auth/SetPasswordForm';
import { useSetPassword } from '@/hooks/auth/useSetPassword';

// ─── Schema ──────────────────────────────────────────────────────────────────

const profileSchema = z.object({
  displayName: z.string().optional(),
  anonymous: z.boolean(),
});

type ProfileFormData = z.infer<typeof profileSchema>;

// ─── Avatar helpers ───────────────────────────────────────────────────────────

function getInitials(displayName: string | null, email: string): string {
  const name = displayName?.trim();
  if (name) {
    const parts = name.split(/\s+/);
    if (parts.length >= 2) return (parts[0][0] + parts[parts.length - 1][0]).toUpperCase();
    return parts[0][0].toUpperCase();
  }
  return email[0].toUpperCase();
}

function formatDate(isoDate: string): string {
  return new Intl.DateTimeFormat(undefined, { year: 'numeric', month: 'long' }).format(
    new Date(isoDate),
  );
}

// ─── Provider label map ───────────────────────────────────────────────────────

const PROVIDER_KEYS = {
  GOOGLE: 'donor.profile.security.google',
  EMAIL: 'donor.profile.security.email',
  MAGIC_LINK: 'donor.profile.security.magicLink',
} as const;

// ─── Page ─────────────────────────────────────────────────────────────────────

export default function DonorProfilePage() {
  const t = useTranslations('dashboard');
  const user = useAuthStore((s) => s.user);
  const { profile, isLoading, updateProfile } = useDonorProfile();
  const [showPasswordModal, setShowPasswordModal] = useState(false);
  const { onSubmit: submitPassword, loading: passwordLoading } = useSetPassword();

  const {
    register,
    handleSubmit,
    reset,
    watch,
    setValue,
    formState: { isDirty, isSubmitting },
  } = useForm<ProfileFormData>({
    resolver: zodResolver(profileSchema),
    values: {
      displayName: profile?.displayName ?? '',
      anonymous: profile?.anonymous ?? false,
    },
  });

  const anonymousValue = watch('anonymous');

  const onSubmit = handleSubmit(async (data) => {
    await updateProfile({
      displayName: data.displayName || undefined,
      anonymous: data.anonymous,
    });
    reset(data);
  });

  const handlePasswordSubmit = async (password: string): Promise<void> => {
    await submitPassword(password);
    setShowPasswordModal(false);
  };

  if (!user) return null;

  const initials = getInitials(profile?.displayName ?? null, user.email);
  const displayedName = profile?.displayName || user.email;

  // ─── Shared classes ───────────────────────────────────────────────────────

  const fieldClass =
    'w-full bg-bg-3 border border-border text-text px-3 py-[10px] rounded-[8px] font-body text-[13.5px] outline-none transition-[border-color] duration-200 placeholder:text-muted focus:border-green/40';
  const labelClass =
    'text-[11px] font-semibold text-text-2 uppercase tracking-[0.06em] block mb-[5px]';
  const cardClass = 'bg-bg-2 border border-border rounded-[14px] p-[22px] mb-[18px]';

  return (
    <div>
      <Topbar title={t('donor.profile.title')} />

      {/* ── Profile header ──────────────────────────────────────────────── */}
      <div className="flex items-center gap-[18px] mb-[28px]">
        {/* Avatar */}
        <div className="w-[64px] h-[64px] rounded-full bg-gradient-to-br from-green to-cyan flex items-center justify-center font-display font-extrabold text-[22px] text-black flex-shrink-0">
          {initials}
        </div>

        <div>
          <h2 className="font-display font-bold text-[20px] text-text leading-tight">
            {displayedName}
          </h2>
          <p className="text-[13px] text-text-2 mt-[2px]">{user.email}</p>
          <div className="flex items-center gap-[8px] mt-[6px]">
            <span className="bg-green/12 text-green rounded-full px-[9px] py-[3px] text-[11px] font-semibold">
              {t('roles.donor')}
            </span>
            <span className="text-[11px] text-text-2">
              {t('donor.profile.memberSince', { date: formatDate(user.createdAt) })}
            </span>
          </div>
        </div>
      </div>

      {/* ── Profile form card ───────────────────────────────────────────── */}
      <div className={cardClass}>
        {isLoading ? (
          <p className="text-[13px] text-text-2">{t('donor.profile.loading')}</p>
        ) : (
          <form onSubmit={onSubmit} noValidate className="flex flex-col gap-[14px]">
            {/* Display name */}
            <div>
              <label htmlFor="displayName" className={labelClass}>
                {t('donor.profile.displayName')}
              </label>
              <input
                id="displayName"
                type="text"
                placeholder={t('donor.profile.displayNamePlaceholder')}
                className={fieldClass}
                {...register('displayName')}
              />
            </div>

            {/* Email (read-only) */}
            <div>
              <label htmlFor="email" className={labelClass}>
                {t('donor.profile.email')}
              </label>
              <input
                id="email"
                type="email"
                value={user.email}
                disabled
                className={`${fieldClass} opacity-50 cursor-not-allowed`}
              />
            </div>

            {/* Anonymous toggle */}
            <div className="flex items-center justify-between py-[6px]">
              <div>
                <p className="text-[13px] text-text font-medium">{t('donor.profile.anonymous')}</p>
                <p className="text-[11.5px] text-text-2 mt-[2px]">
                  {t('donor.profile.anonymousHint')}
                </p>
              </div>
              <button
                type="button"
                role="switch"
                aria-checked={anonymousValue}
                onClick={() => setValue('anonymous', !anonymousValue, { shouldDirty: true })}
                className={`relative w-[42px] h-[24px] rounded-full transition-colors duration-200 flex-shrink-0 ${
                  anonymousValue ? 'bg-green' : 'bg-muted'
                }`}
              >
                <span
                  className={`absolute top-[3px] w-[18px] h-[18px] rounded-full bg-text transition-all duration-200 ${
                    anonymousValue ? 'left-[21px]' : 'left-[3px]'
                  }`}
                />
              </button>
            </div>

            {/* Actions */}
            <div className="flex gap-[10px] pt-[4px]">
              <button
                type="submit"
                disabled={!isDirty || isSubmitting}
                className="py-[10px] px-[20px] bg-green text-black rounded-md font-display text-[13px] font-bold transition-all duration-200 hover:bg-[#00d4b0] disabled:opacity-40 disabled:cursor-not-allowed"
              >
                {t('donor.profile.save')}
              </button>
              <button
                type="button"
                onClick={() => reset()}
                disabled={!isDirty}
                className="py-[10px] px-[20px] bg-transparent border border-border text-text-2 rounded-md font-display text-[13px] font-bold transition-colors duration-150 hover:border-text-2 disabled:opacity-40 disabled:cursor-not-allowed"
              >
                {t('donor.profile.cancel')}
              </button>
            </div>
          </form>
        )}
      </div>

      {/* ── Security card ───────────────────────────────────────────────── */}
      <div className={cardClass}>
        <h3 className="font-display font-bold text-[15px] text-text mb-[14px]">
          {t('donor.profile.security.title')}
        </h3>

        <div className="flex items-center justify-between">
          <div>
            <p className="text-[11px] font-semibold text-text-2 uppercase tracking-[0.06em] mb-[3px]">
              {t('donor.profile.security.loginMethod')}
            </p>
            <p className="text-[13.5px] text-text">
              {t(PROVIDER_KEYS[user.provider] as Parameters<typeof t>[0])}
            </p>
          </div>

          <button
            type="button"
            onClick={() => setShowPasswordModal(true)}
            className="text-[13px] text-green font-semibold bg-green/10 px-[14px] py-[8px] rounded-[8px] hover:bg-green/20 transition-colors duration-150"
          >
            {t('donor.profile.security.changePassword')}
          </button>
        </div>
      </div>

      {/* ── SetPassword modal ────────────────────────────────────────────── */}
      {showPasswordModal && (
        <div
          className="fixed inset-0 z-50 flex items-center justify-center bg-bg/80 backdrop-blur-sm"
          onClick={() => setShowPasswordModal(false)}
        >
          <div
            className="bg-bg-2 border border-border rounded-[18px] p-[28px] w-full max-w-[380px] mx-4"
            onClick={(e) => e.stopPropagation()}
          >
            <h3 className="font-display font-bold text-[17px] text-text mb-[16px]">
              {t('donor.profile.security.changePassword')}
            </h3>
            <SetPasswordForm
              onSubmit={handlePasswordSubmit}
              onSkip={() => setShowPasswordModal(false)}
              loading={passwordLoading}
            />
          </div>
        </div>
      )}
    </div>
  );
}
