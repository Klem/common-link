'use client';

import { useState } from 'react';
import { useTranslations } from 'next-intl';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { z } from 'zod';
import { useAuthStore } from '@/stores/authStore';
import { useAssociationProfile } from '@/hooks/dashboard/useAssociationProfile';
import { Topbar } from '@/components/dashboard/Topbar';
import { SetPasswordForm } from '@/components/auth/SetPasswordForm';
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

  // ─── Shared classes ───────────────────────────────────────────────────────

  const fieldClass =
    'w-full bg-bg-3 border border-border text-text px-3 py-[10px] rounded-[8px] font-body text-[13.5px] outline-none transition-[border-color] duration-200 placeholder:text-muted focus:border-green/40';
  const fieldReadonlyClass =
    'w-full bg-bg-3/50 border border-border text-text-2 px-3 py-[10px] rounded-[8px] font-body text-[13.5px] opacity-60 cursor-not-allowed';
  const labelClass =
    'text-[11px] font-semibold text-text-2 uppercase tracking-[0.06em] block mb-[5px]';
  const cardClass = 'bg-bg-2 border border-border rounded-[14px] p-[22px] mb-[18px]';
  const errorClass = 'text-[11.5px] text-red mt-[4px]';

  return (
    <div>
      <Topbar title={t('association.profile.title')} />

      {/* ── Profile header ──────────────────────────────────────────────── */}
      <div className="flex items-center gap-[18px] mb-[28px]">
        {/* Avatar */}
        <div className="w-[64px] h-[64px] rounded-full bg-gradient-to-br from-green to-cyan flex items-center justify-center font-display font-extrabold text-[22px] text-black flex-shrink-0">
          {initial}
        </div>

        <div>
          <h2 className="font-display font-bold text-[20px] text-text leading-tight">
            {associationName}
          </h2>
          <p className="text-[13px] text-text-2 mt-[2px]">{user.email}</p>

          <div className="flex items-center gap-[8px] mt-[6px] flex-wrap">
            {/* Role chip */}
            <span className="bg-yellow/12 text-yellow rounded-full px-[9px] py-[3px] text-[11px] font-semibold">
              {t('roles.association')}
            </span>

            {/* Verified badge */}
            {profile?.verified ? (
              <span className="bg-green/12 text-green rounded-full px-[9px] py-[3px] text-[11px] font-semibold">
                {t('association.profile.verified')}
              </span>
            ) : (
              <span className="bg-muted/30 text-text-2 rounded-full px-[9px] py-[3px] text-[11px] font-semibold">
                {t('association.profile.pending')}
              </span>
            )}

            {/* SIREN */}
            {profile?.identifier && (
              <span className="font-mono text-[11px] text-text-2">
                SIREN: {profile.identifier}
              </span>
            )}

            {/* Member since */}
            <span className="text-[11px] text-text-2">
              {t('association.profile.memberSince', { date: formatDate(user.createdAt) })}
            </span>
          </div>
        </div>
      </div>

      {/* ── Profile form card ───────────────────────────────────────────── */}
      <div className={cardClass}>
        {isLoading ? (
          <p className="text-[13px] text-text-2">{t('association.profile.loading')}</p>
        ) : (
          <form onSubmit={onSubmit} noValidate className="flex flex-col gap-[14px]">
            {/* Name (read-only) */}
            <div>
              <label className={labelClass}>{t('association.profile.name')}</label>
              <input
                type="text"
                value={profile?.name ?? ''}
                disabled
                className={fieldReadonlyClass}
              />
            </div>

            {/* SIREN (read-only) */}
            <div>
              <label className={labelClass}>{t('association.profile.identifier')}</label>
              <input
                type="text"
                value={profile?.identifier ?? ''}
                disabled
                className={fieldReadonlyClass}
              />
            </div>

            {/* Contact name */}
            <div>
              <label htmlFor="contactName" className={labelClass}>
                {t('association.profile.contactName')}
              </label>
              <input
                id="contactName"
                type="text"
                placeholder={t('association.profile.contactNamePlaceholder')}
                className={fieldClass}
                {...register('contactName')}
              />
              {errors.contactName && (
                <p className={errorClass}>{errors.contactName.message}</p>
              )}
            </div>

            {/* City */}
            <div>
              <label htmlFor="city" className={labelClass}>
                {t('association.profile.city')}
              </label>
              <input
                id="city"
                type="text"
                className={fieldClass}
                {...register('city')}
              />
            </div>

            {/* Postal code */}
            <div>
              <label htmlFor="postalCode" className={labelClass}>
                {t('association.profile.postalCode')}
              </label>
              <input
                id="postalCode"
                type="text"
                className={fieldClass}
                {...register('postalCode')}
              />
              {errors.postalCode && (
                <p className={errorClass}>{errors.postalCode.message}</p>
              )}
            </div>

            {/* Description */}
            <div>
              <label htmlFor="description" className={labelClass}>
                {t('association.profile.description')}
              </label>
              <textarea
                id="description"
                rows={4}
                placeholder={t('association.profile.descriptionPlaceholder')}
                className={`${fieldClass} resize-none`}
                {...register('description')}
              />
            </div>

            {/* Actions */}
            <div className="flex gap-[10px] pt-[4px]">
              <button
                type="submit"
                disabled={!isDirty || isSubmitting}
                className="py-[10px] px-[20px] bg-green text-black rounded-md font-display text-[13px] font-bold transition-all duration-200 hover:bg-[#00d4b0] disabled:opacity-40 disabled:cursor-not-allowed"
              >
                {t('association.profile.save')}
              </button>
              <button
                type="button"
                onClick={() => reset()}
                disabled={!isDirty}
                className="py-[10px] px-[20px] bg-transparent border border-border text-text-2 rounded-md font-display text-[13px] font-bold transition-colors duration-150 hover:border-text-2 disabled:opacity-40 disabled:cursor-not-allowed"
              >
                {t('association.profile.cancel')}
              </button>
            </div>
          </form>
        )}
      </div>

      {/* ── Security card ───────────────────────────────────────────────── */}
      <div className={cardClass}>
        <h3 className="font-display font-bold text-[15px] text-text mb-[14px]">
          {t('association.profile.security.title')}
        </h3>

        <div className="flex items-center justify-between">
          <div>
            <p className="text-[11px] font-semibold text-text-2 uppercase tracking-[0.06em] mb-[3px]">
              {t('association.profile.security.loginMethod')}
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
            {t('association.profile.security.changePassword')}
          </button>
        </div>
      </div>

      {/* ── Verification info card ──────────────────────────────────────── */}
      <div className={cardClass}>
        <h3 className="font-display font-bold text-[15px] text-text mb-[10px]">
          {t('association.profile.verification.title')}
        </h3>
        {profile?.verified ? (
          <p className="text-[13px] text-green">{t('association.profile.verification.verified')}</p>
        ) : (
          <p className="text-[13px] text-text-2">
            {t('association.profile.verification.pendingText')}
          </p>
        )}
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
    </div>
  );
}
