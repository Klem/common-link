'use client';

import { useState } from 'react';
import { useTranslations } from 'next-intl';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { z } from 'zod';
import { useAuthStore } from '@/stores/authStore';
import { useDonorProfile } from '@/hooks/dashboard/useDonorProfile';
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

  return (
    <div>
      <div className="mb-8">
        <h1 className="font-display font-black text-2xl md:text-3xl">{t('donor.profile.title')}</h1>
      </div>

      <div className="grid grid-cols-1 lg:grid-cols-[280px_1fr] gap-6">
        {/* ── Left: Avatar card ─────────────────────────────────────────── */}
        <div className="card card-no-hover p-6 flex flex-col items-center text-center gap-4 h-fit">
          <div className="avatar avatar-lg avatar-teal font-display font-extrabold">
            {initials}
          </div>
          <div>
            <p className="font-display font-bold text-lg text-text leading-tight">{displayedName}</p>
            <p className="text-sm text-text-2 mt-1">{user.email}</p>
            <div className="flex items-center justify-center gap-2 mt-3 flex-wrap">
              <span className="chip green">{t('roles.donor')}</span>
              <span className="text-xs text-text-2">
                {t('donor.profile.memberSince', { date: formatDate(user.createdAt) })}
              </span>
            </div>
          </div>
          <button type="button" className="btn btn-ghost btn-sm">
            {t('donor.profile.changePhoto')}
          </button>
        </div>

        {/* ── Right: Form + Security ────────────────────────────────────── */}
        <div className="flex flex-col gap-6">
          {/* Profile form card */}
          <div className="card card-no-hover">
            <div className="card-body">
              {isLoading ? (
                <p className="text-sm text-text-2">{t('donor.profile.loading')}</p>
              ) : (
                <form onSubmit={onSubmit} noValidate className="flex flex-col gap-4">
                  <div className="form-group">
                    <label htmlFor="displayName" className="form-label">
                      {t('donor.profile.displayName')}
                    </label>
                    <input
                      id="displayName"
                      type="text"
                      placeholder={t('donor.profile.displayNamePlaceholder')}
                      className="form-input"
                      {...register('displayName')}
                    />
                  </div>

                  <div className="form-group">
                    <label htmlFor="email" className="form-label">
                      {t('donor.profile.email')}
                    </label>
                    <input
                      id="email"
                      type="email"
                      value={user.email}
                      disabled
                      className="form-input"
                    />
                  </div>

                  <div className="flex items-center justify-between py-1">
                    <div>
                      <p className="text-sm text-text font-medium">{t('donor.profile.anonymous')}</p>
                      <p className="text-xs text-text-2 mt-0.5">{t('donor.profile.anonymousHint')}</p>
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

                  <div className="flex gap-3 pt-1">
                    <button
                      type="submit"
                      disabled={!isDirty || isSubmitting}
                      className="btn btn-primary btn-md disabled:opacity-40 disabled:cursor-not-allowed"
                    >
                      {t('donor.profile.save')}
                    </button>
                    <button
                      type="button"
                      onClick={() => reset()}
                      disabled={!isDirty}
                      className="btn btn-ghost btn-md disabled:opacity-40 disabled:cursor-not-allowed"
                    >
                      {t('donor.profile.cancel')}
                    </button>
                  </div>
                </form>
              )}
            </div>
          </div>

          {/* Security card */}
          <div className="card card-no-hover">
            <div className="card-body">
              <h3 className="font-display font-bold text-base text-text mb-4">
                {t('donor.profile.security.title')}
              </h3>
              <div className="flex items-center justify-between">
                <div>
                  <p className="form-label">{t('donor.profile.security.loginMethod')}</p>
                  <p className="text-sm text-text">
                    {t(PROVIDER_KEYS[user.provider] as Parameters<typeof t>[0])}
                  </p>
                </div>
                <button
                  type="button"
                  onClick={() => setShowPasswordModal(true)}
                  className="btn btn-ghost btn-sm"
                >
                  {t('donor.profile.security.changePassword')}
                </button>
              </div>
            </div>
          </div>
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
