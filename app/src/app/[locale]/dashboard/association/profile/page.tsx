'use client';

import { useState, useCallback } from 'react';
import { useTranslations } from 'next-intl';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { z } from 'zod';
import { useAuthStore } from '@/stores/authStore';
import { useAssociationProfile } from '@/hooks/dashboard/useAssociationProfile';
import { useMoneriumStatus } from '@/hooks/monerium/useMoneriumStatus';
import { Topbar } from '@/components/dashboard/Topbar';
import { SetPasswordForm } from '@/components/auth/SetPasswordForm';
import MoneriumOnboardModal from '@/components/dashboard/MoneriumOnboardModal';
import { useSetPassword } from '@/hooks/auth/useSetPassword';

// ─── Schema ──────────────────────────────────────────────────────────────────

const profileSchema = z.object({
  contactName: z.string().min(2, 'dashboard.association.profile.errors.contactNameMin'),
  city: z.string().optional(),
  postalCode: z
    .string()
    .optional()
    .refine((v) => !v || /^\d{5}$/.test(v), 'dashboard.association.profile.errors.postalCodeFormat'),
  description: z.string().optional(),
});

type ProfileFormData = z.infer<typeof profileSchema>;

// ─── Helpers ──────────────────────────────────────────────────────────────────

function getInitial(name: string): string {
  return name.trim()[0]?.toUpperCase() ?? '?';
}

function formatDate(isoDate: string): string {
  return new Intl.DateTimeFormat(undefined, { year: 'numeric', month: 'long' }).format(
    new Date(isoDate),
  );
}

const PROVIDER_KEYS = {
  GOOGLE: 'association.profile.security.google',
  EMAIL: 'association.profile.security.email',
  MAGIC_LINK: 'association.profile.security.magicLink',
} as const;

// ─── Page ─────────────────────────────────────────────────────────────────────

export default function AssociationProfilePage() {
  const t = useTranslations('dashboard');
  const user = useAuthStore((s) => s.user);
  const { profile, isLoading, updateProfile } = useAssociationProfile();
  const [showPasswordModal, setShowPasswordModal] = useState(false);
  const [showMoneriumModal, setShowMoneriumModal] = useState(false);
  const [moneriumInterrupted, setMoneriumInterrupted] = useState(false);
  const { connected, pending, isLoading: moneriumLoading, refresh: refreshMonerium } = useMoneriumStatus();

  const handlePopupClosed = useCallback(async () => {
    setMoneriumInterrupted(true);
    await refreshMonerium();
  }, [refreshMonerium]);
  const { onSubmit: submitPassword, loading: passwordLoading } = useSetPassword();

  const {
    register,
    handleSubmit,
    reset,
    formState: { isDirty, isSubmitting, errors },
  } = useForm<ProfileFormData>({
    resolver: zodResolver(profileSchema),
    values: {
      contactName: profile?.contactName ?? '',
      city: profile?.city ?? '',
      postalCode: profile?.postalCode ?? '',
      description: profile?.description ?? '',
    },
  });

  const onSubmit = handleSubmit(async (data) => {
    await updateProfile({
      contactName: data.contactName || undefined,
      city: data.city || undefined,
      postalCode: data.postalCode || undefined,
      description: data.description || undefined,
    });
    reset(data);
  });

  const handlePasswordSubmit = async (password: string): Promise<void> => {
    await submitPassword(password);
    setShowPasswordModal(false);
  };

  if (!user) return null;

  const associationName = profile?.name ?? user.displayName ?? user.email;
  const initial = getInitial(associationName);

  return (
    <div>
      <Topbar title={t('association.profile.title')} />

      {/* ── Profile header ──────────────────────────────────────────────── */}
      <div className="flex items-center gap-5 mb-8">
        <div className="avatar avatar-lg bg-gradient-to-br from-green to-cyan text-black flex-shrink-0">
          {initial}
        </div>

        <div>
          <h2 className="font-display font-bold text-xl leading-tight">{associationName}</h2>
          <p className="text-sm text-text-2 mt-0.5">{user.email}</p>

          <div className="flex items-center gap-2 mt-1.5 flex-wrap">
            <span className="badge badge-warning">{t('roles.association')}</span>

            {profile?.verified ? (
              <span className="badge badge-active">{t('association.profile.verified')}</span>
            ) : (
              <span className="badge badge-neutral">{t('association.profile.pending')}</span>
            )}

            {profile?.identifier && (
              <span className="font-mono text-xs text-text-2">SIREN: {profile.identifier}</span>
            )}

            <span className="text-xs text-text-2">
              {t('association.profile.memberSince', { date: formatDate(user.createdAt) })}
            </span>
          </div>
        </div>
      </div>

      {/* ── Profile form card ───────────────────────────────────────────── */}
      <div className="card card-no-hover mb-4">
        <div className="card-body">
          {isLoading ? (
            <p className="text-sm text-text-2">{t('association.profile.loading')}</p>
          ) : (
            <form onSubmit={onSubmit} noValidate>
              <div className="form-group">
                <label className="form-label">{t('association.profile.name')}</label>
                <input
                  type="text"
                  value={profile?.name ?? ''}
                  disabled
                  className="form-input"
                />
              </div>

              <div className="form-group">
                <label className="form-label">{t('association.profile.identifier')}</label>
                <input
                  type="text"
                  value={profile?.identifier ?? ''}
                  disabled
                  className="form-input"
                />
              </div>

              <div className="form-group">
                <label htmlFor="contactName" className="form-label">
                  {t('association.profile.contactName')}
                </label>
                <input
                  id="contactName"
                  type="text"
                  placeholder={t('association.profile.contactNamePlaceholder')}
                  className={`form-input${errors.contactName ? ' error' : ''}`}
                  {...register('contactName')}
                />
                {errors.contactName && (
                  <p className="form-error">{errors.contactName.message}</p>
                )}
              </div>

              <div className="form-group">
                <label htmlFor="city" className="form-label">
                  {t('association.profile.city')}
                </label>
                <input
                  id="city"
                  type="text"
                  className="form-input"
                  {...register('city')}
                />
              </div>

              <div className="form-group">
                <label htmlFor="postalCode" className="form-label">
                  {t('association.profile.postalCode')}
                </label>
                <input
                  id="postalCode"
                  type="text"
                  className={`form-input${errors.postalCode ? ' error' : ''}`}
                  {...register('postalCode')}
                />
                {errors.postalCode && (
                  <p className="form-error">{errors.postalCode.message}</p>
                )}
              </div>

              <div className="form-group">
                <label htmlFor="description" className="form-label">
                  {t('association.profile.description')}
                </label>
                <textarea
                  id="description"
                  placeholder={t('association.profile.descriptionPlaceholder')}
                  className="form-input"
                  {...register('description')}
                />
              </div>

              <div className="flex gap-3 pt-1">
                <button
                  type="submit"
                  disabled={!isDirty || isSubmitting}
                  className="btn btn-primary btn-md"
                >
                  {t('association.profile.save')}
                </button>
                <button
                  type="button"
                  onClick={() => reset()}
                  disabled={!isDirty}
                  className="btn btn-ghost btn-md"
                >
                  {t('association.profile.cancel')}
                </button>
              </div>
            </form>
          )}
        </div>
      </div>

      {/* ── Security card ───────────────────────────────────────────────── */}
      <div className="card card-no-hover mb-4">
        <div className="card-body">
          <h3 className="font-display font-bold text-sm mb-4">
            {t('association.profile.security.title')}
          </h3>

          <div className="flex items-center justify-between">
            <div>
              <p className="form-label">{t('association.profile.security.loginMethod')}</p>
              <p className="text-sm text-text">
                {t(PROVIDER_KEYS[user.provider] as Parameters<typeof t>[0])}
              </p>
            </div>

            <button
              type="button"
              onClick={() => setShowPasswordModal(true)}
              className="btn btn-secondary btn-sm"
            >
              {t('association.profile.security.changePassword')}
            </button>
          </div>
        </div>
      </div>

      {/* ── Verification info card ──────────────────────────────────────── */}
      <div className="card card-no-hover mb-4">
        <div className="card-body">
          <h3 className="font-display font-bold text-sm mb-2">
            {t('association.profile.verification.title')}
          </h3>
          {profile?.verified ? (
            <p className="text-sm text-green">{t('association.profile.verification.verified')}</p>
          ) : (
            <p className="text-sm text-text-2">
              {t('association.profile.verification.pendingText')}
            </p>
          )}
        </div>
      </div>

      {/* ── Monerium wallet card ─────────────────────────────────────────── */}
      <div className="card card-no-hover mb-4 border-dashed border-indigo-500/40">
        <div className="card-body">
          <div className="flex items-center gap-2 mb-3">
            <h3 className="font-display font-bold text-sm">
              {t('association.profile.monerium.title')}
            </h3>
            <span className="badge badge-info">{t('association.profile.monerium.badge')}</span>
          </div>
          <p className="text-sm text-text-2 mb-4">
            {t('association.profile.monerium.description')}
          </p>
          {moneriumLoading ? (
            <div className="w-5 h-5 border-2 border-indigo-500 border-t-transparent rounded-full animate-spin" />
          ) : connected ? (
            <span className="badge badge-active">
              {t('association.profile.monerium.connectedStatus')}
            </span>
          ) : moneriumInterrupted ? (
            <button
              type="button"
              onClick={() => { setMoneriumInterrupted(false); setShowMoneriumModal(true); }}
              className="btn btn-ghost btn-sm text-yellow"
            >
              {t('association.profile.monerium.tryAgain')}
            </button>
          ) : pending ? (
            <span className="badge badge-warning">
              {t('association.profile.monerium.pendingStatus')}
            </span>
          ) : (
            <button
              type="button"
              onClick={() => setShowMoneriumModal(true)}
              className="btn btn-indigo btn-sm"
            >
              {t('association.profile.monerium.connect')}
            </button>
          )}
        </div>
      </div>

      {/* ── SetPassword modal ────────────────────────────────────────────── */}
      {showPasswordModal && (
        <div
          className="fixed inset-0 z-50 flex items-center justify-center bg-bg/80 backdrop-blur-sm"
          onClick={() => setShowPasswordModal(false)}
        >
          <div
            className="bg-bg-2 border border-border rounded-[18px] p-7 w-full max-w-[380px] mx-4"
            onClick={(e) => e.stopPropagation()}
          >
            <h3 className="font-display font-bold text-lg mb-4">
              {t('association.profile.security.changePassword')}
            </h3>
            <SetPasswordForm
              onSubmit={handlePasswordSubmit}
              onSkip={() => setShowPasswordModal(false)}
              loading={passwordLoading}
            />
          </div>
        </div>
      )}

      {/* ── Monerium onboard modal ───────────────────────────────────────── */}
      <MoneriumOnboardModal
        isOpen={showMoneriumModal}
        onClose={() => setShowMoneriumModal(false)}
        onConnected={() => { setMoneriumInterrupted(false); refreshMonerium(); }}
        onPopupClosed={handlePopupClosed}
      />
    </div>
  );
}
