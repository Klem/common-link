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
import { Topbar } from '@/components/dashboard';

// ─── Schema ──────────────────────────────────────────────────────────────────

const settingsSchema = z.object({
  firstName: z.string().max(128).optional(),
  lastName: z.string().max(128).optional(),
  displayName: z.string().max(255).optional(),
  anonymous: z.boolean(),
  notifyMonthlyReport: z.boolean(),
  notifyNewPayout: z.boolean(),
  notifyGoalReached: z.boolean(),
  notifySuggestions: z.boolean(),
});

type SettingsFormData = z.infer<typeof settingsSchema>;

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
  return new Intl.DateTimeFormat('fr-FR', { year: 'numeric', month: 'long' }).format(
    new Date(isoDate),
  );
}

// ─── Provider label map ───────────────────────────────────────────────────────

const PROVIDER_KEYS = {
  GOOGLE: 'donor.settings.security.google',
  EMAIL: 'donor.settings.security.email',
  MAGIC_LINK: 'donor.settings.security.magicLink',
} as const;

// ─── Notification toggle row ───────────────────────────────────────────────────

function NotificationToggle({
  label,
  hint,
  checked,
  onChange,
}: {
  label: string;
  hint: string;
  checked: boolean;
  onChange: (value: boolean) => void;
}) {
  return (
    <div className="flex items-center justify-between py-1">
      <div>
        <p className="text-sm text-text font-medium">{label}</p>
        <p className="text-xs text-text-2 mt-0.5">{hint}</p>
      </div>
      <button
        type="button"
        role="switch"
        aria-checked={checked}
        aria-label={label}
        onClick={() => onChange(!checked)}
        className={`relative w-[42px] h-[24px] rounded-full transition-colors duration-200 flex-shrink-0 ${
          checked ? 'bg-green' : 'bg-muted'
        }`}
      >
        <span
          className={`absolute top-[3px] w-[18px] h-[18px] rounded-full bg-text transition-all duration-200 ${
            checked ? 'left-[21px]' : 'left-[3px]'
          }`}
        />
      </button>
    </div>
  );
}

// ─── Page ─────────────────────────────────────────────────────────────────────

export default function DonorSettingsPage() {
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
  } = useForm<SettingsFormData>({
    resolver: zodResolver(settingsSchema),
    values: {
      firstName: profile?.firstName ?? '',
      lastName: profile?.lastName ?? '',
      displayName: profile?.displayName ?? '',
      anonymous: profile?.anonymous ?? false,
      notifyMonthlyReport: profile?.notifyMonthlyReport ?? true,
      notifyNewPayout: profile?.notifyNewPayout ?? true,
      notifyGoalReached: profile?.notifyGoalReached ?? true,
      notifySuggestions: profile?.notifySuggestions ?? false,
    },
  });

  const anonymousValue = watch('anonymous');
  const notifyMonthlyReport = watch('notifyMonthlyReport');
  const notifyNewPayout = watch('notifyNewPayout');
  const notifyGoalReached = watch('notifyGoalReached');
  const notifySuggestions = watch('notifySuggestions');

  const onSubmit = handleSubmit(async (data) => {
    await updateProfile({
      firstName: data.firstName,
      lastName: data.lastName,
      displayName: data.displayName || undefined,
      anonymous: data.anonymous,
      notifyMonthlyReport: data.notifyMonthlyReport,
      notifyNewPayout: data.notifyNewPayout,
      notifyGoalReached: data.notifyGoalReached,
      notifySuggestions: data.notifySuggestions,
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
      <Topbar title={t('donor.settings.title')} />

      <div className="page">
        <div className="page-head">
          <div>
            <h1>{t('donor.settings.title')}</h1>
          </div>
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
                {t('donor.settings.memberSince', { date: formatDate(user.createdAt) })}
              </span>
            </div>
          </div>
          <button type="button" className="btn btn-ghost btn-sm">
            {t('donor.settings.changePhoto')}
          </button>
        </div>

        {/* ── Right: Form + Notifications + Security ────────────────────── */}
        <div className="flex flex-col gap-6">
          {/* Identity form card */}
          <div className="card card-no-hover">
            <div className="card-b">
              {isLoading ? (
                <p className="text-sm text-text-2" aria-live="polite">
                  {t('donor.settings.loading')}
                </p>
              ) : (
                <form onSubmit={onSubmit} noValidate className="flex flex-col gap-4">
                  <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
                    <div className="form-group mb-0">
                      <label htmlFor="firstName" className="form-label">
                        {t('donor.settings.firstName')}
                      </label>
                      <input
                        id="firstName"
                        type="text"
                        className="form-input"
                        {...register('firstName')}
                      />
                    </div>
                    <div className="form-group mb-0">
                      <label htmlFor="lastName" className="form-label">
                        {t('donor.settings.lastName')}
                      </label>
                      <input
                        id="lastName"
                        type="text"
                        className="form-input"
                        {...register('lastName')}
                      />
                    </div>
                  </div>

                  <div className="form-group">
                    <label htmlFor="displayName" className="form-label">
                      {t('donor.settings.displayName')}
                    </label>
                    <input
                      id="displayName"
                      type="text"
                      placeholder={t('donor.settings.displayNamePlaceholder')}
                      className="form-input"
                      {...register('displayName')}
                    />
                  </div>

                  <div className="form-group">
                    <label htmlFor="email" className="form-label">
                      {t('donor.settings.email')}
                    </label>
                    <input
                      id="email"
                      type="email"
                      value={user.email}
                      disabled
                      className="form-input"
                    />
                  </div>

                  <NotificationToggle
                    label={t('donor.settings.anonymous')}
                    hint={t('donor.settings.anonymousHint')}
                    checked={anonymousValue}
                    onChange={(value) => setValue('anonymous', value, { shouldDirty: true })}
                  />

                  <div className="border-t border-border pt-4 mt-2">
                    <h3 className="font-display font-bold text-sm text-text mb-3">
                      {t('donor.settings.notifications.title')}
                    </h3>
                    <div className="flex flex-col gap-1">
                      <NotificationToggle
                        label={t('donor.settings.notifications.monthlyReport')}
                        hint={t('donor.settings.notifications.monthlyReportHint')}
                        checked={notifyMonthlyReport}
                        onChange={(value) =>
                          setValue('notifyMonthlyReport', value, { shouldDirty: true })
                        }
                      />
                      <NotificationToggle
                        label={t('donor.settings.notifications.newPayout')}
                        hint={t('donor.settings.notifications.newPayoutHint')}
                        checked={notifyNewPayout}
                        onChange={(value) =>
                          setValue('notifyNewPayout', value, { shouldDirty: true })
                        }
                      />
                      <NotificationToggle
                        label={t('donor.settings.notifications.goalReached')}
                        hint={t('donor.settings.notifications.goalReachedHint')}
                        checked={notifyGoalReached}
                        onChange={(value) =>
                          setValue('notifyGoalReached', value, { shouldDirty: true })
                        }
                      />
                      <NotificationToggle
                        label={t('donor.settings.notifications.suggestions')}
                        hint={t('donor.settings.notifications.suggestionsHint')}
                        checked={notifySuggestions}
                        onChange={(value) =>
                          setValue('notifySuggestions', value, { shouldDirty: true })
                        }
                      />
                    </div>
                  </div>

                  <div className="flex gap-3 pt-1">
                    <button
                      type="submit"
                      disabled={!isDirty || isSubmitting}
                      className="btn btn-primary btn-md disabled:opacity-40 disabled:cursor-not-allowed"
                    >
                      {t('donor.settings.save')}
                    </button>
                    <button
                      type="button"
                      onClick={() => reset()}
                      disabled={!isDirty}
                      className="btn btn-ghost btn-md disabled:opacity-40 disabled:cursor-not-allowed"
                    >
                      {t('donor.settings.cancel')}
                    </button>
                  </div>
                </form>
              )}
            </div>
          </div>

          {/* Security card */}
          <div className="card card-no-hover">
            <div className="card-b">
              <h3 className="font-display font-bold text-base text-text mb-4">
                {t('donor.settings.security.title')}
              </h3>
              <div className="flex items-center justify-between">
                <div>
                  <p className="form-label">{t('donor.settings.security.loginMethod')}</p>
                  <p className="text-sm text-text">
                    {t(PROVIDER_KEYS[user.provider] as Parameters<typeof t>[0])}
                  </p>
                </div>
                <button
                  type="button"
                  onClick={() => setShowPasswordModal(true)}
                  className="btn btn-ghost btn-sm"
                >
                  {t('donor.settings.security.changePassword')}
                </button>
              </div>
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
              {t('donor.settings.security.changePassword')}
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
