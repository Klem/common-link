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

type SettingsTab = 'infos' | 'verif' | 'bank';

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
  const [activeTab, setActiveTab] = useState<SettingsTab>('infos');
  const [showPasswordModal, setShowPasswordModal] = useState(false);
  const [showMoneriumModal, setShowMoneriumModal] = useState(false);
  const [moneriumInterrupted, setMoneriumInterrupted] = useState(false);
  const { connected, pending, isLoading: moneriumLoading, refresh: refreshMonerium } =
    useMoneriumStatus();

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

  return (
    <div className="page">
      <Topbar title={t('association.profile.title')} />

      {/* ── Page header ──────────────────────────────────────────────────── */}
      <div className="page-head">
        <div>
          <h1>{t('association.profile.title')}</h1>
          <p>{t('association.profile.subtitle')}</p>
        </div>
      </div>

      {/* ── Settings sub-tabs ────────────────────────────────────────────── */}
      <div className="set-tabs">
        <button
          className={`set-tab${activeTab === 'infos' ? ' active' : ''}`}
          onClick={() => setActiveTab('infos')}
        >
          📋 {t('association.profile.tabs.infos')}
        </button>
        <button
          className={`set-tab${activeTab === 'verif' ? ' active' : ''}`}
          onClick={() => setActiveTab('verif')}
        >
          ✓ {t('association.profile.tabs.verif')}{' '}
          <span className={`set-tab-badge${profile?.verified ? ' ok' : ''}`}>
            {profile?.verified
              ? t('association.profile.tabs.verifBadge.ok')
              : t('association.profile.tabs.verifBadge.todo')}
          </span>
        </button>
        <button
          className={`set-tab${activeTab === 'bank' ? ' active' : ''}`}
          onClick={() => setActiveTab('bank')}
        >
          🏦 {t('association.profile.tabs.bank')}{' '}
          <span className={`set-tab-badge${connected ? ' ok' : ''}`}>
            {connected
              ? t('association.profile.tabs.bankBadge.connected')
              : t('association.profile.tabs.bankBadge.notConnected')}
          </span>
        </button>
      </div>

      {/* ══ Onglet : Informations ═════════════════════════════════════════ */}
      {activeTab === 'infos' && (
        <div className="set-tab-content active">
          <div className="card no-hover">
            <div className="card-h">
              <h3>{t('association.profile.infos.title')}</h3>
            </div>
            <div className="card-b">
              {isLoading ? (
                <p style={{ fontSize: '14px', color: 'var(--slate-lavender)' }}>
                  {t('association.profile.loading')}
                </p>
              ) : (
                <form onSubmit={onSubmit} noValidate>
                  {/* Ligne 1 : Nom association + SIREN */}
                  <div className="frow">
                    <div className="fg">
                      <label className="fl">{t('association.profile.name')}</label>
                      <input className="fi" type="text" value={profile?.name ?? ''} disabled />
                    </div>
                    <div className="fg">
                      <label className="fl">{t('association.profile.identifier')}</label>
                      <input className="fi" type="text" value={profile?.identifier ?? ''} disabled />
                    </div>
                  </div>

                  {/* Ligne 2 : Nom du contact + Ville */}
                  <div className="frow">
                    <div className="fg">
                      <label htmlFor="contactName" className="fl">
                        {t('association.profile.contactName')}
                      </label>
                      <input
                        id="contactName"
                        type="text"
                        className="fi"
                        placeholder={t('association.profile.contactNamePlaceholder')}
                        {...register('contactName')}
                      />
                      {errors.contactName && (
                        <p style={{ fontSize: '12px', color: 'var(--warm-coral)', marginTop: '4px' }}>
                          {errors.contactName.message}
                        </p>
                      )}
                    </div>
                    <div className="fg">
                      <label htmlFor="city" className="fl">
                        {t('association.profile.city')}
                      </label>
                      <input id="city" type="text" className="fi" {...register('city')} />
                    </div>
                  </div>

                  {/* Ligne 3 : Code postal */}
                  <div className="frow">
                    <div className="fg">
                      <label htmlFor="postalCode" className="fl">
                        {t('association.profile.postalCode')}
                      </label>
                      <input
                        id="postalCode"
                        type="text"
                        className="fi"
                        {...register('postalCode')}
                      />
                      {errors.postalCode && (
                        <p style={{ fontSize: '12px', color: 'var(--warm-coral)', marginTop: '4px' }}>
                          {errors.postalCode.message}
                        </p>
                      )}
                    </div>
                    <div className="fg" />
                  </div>

                  {/* Description : pleine largeur */}
                  <div className="fg">
                    <label htmlFor="description" className="fl">
                      {t('association.profile.description')}
                    </label>
                    <textarea
                      id="description"
                      className="fi"
                      placeholder={t('association.profile.descriptionPlaceholder')}
                      {...register('description')}
                    />
                  </div>

                  <div style={{ display: 'flex', justifyContent: 'flex-end', gap: '12px' }}>
                    <button
                      type="button"
                      onClick={() => reset()}
                      disabled={!isDirty}
                      className="btn btn-secondary btn-sm"
                    >
                      {t('association.profile.cancel')}
                    </button>
                    <button
                      type="submit"
                      disabled={!isDirty || isSubmitting}
                      className="btn btn-primary btn-sm"
                    >
                      {t('association.profile.save')}
                    </button>
                  </div>
                </form>
              )}
            </div>
          </div>
        </div>
      )}

      {/* ══ Onglet : Vérification ════════════════════════════════════════ */}
      {activeTab === 'verif' && (
        <div className="set-tab-content active">
          <div className="card no-hover">
            <div className="card-h">
              <h3>{t('association.profile.verification.title')}</h3>
            </div>
            <div className="card-b">
              {profile?.verified ? (
                <p style={{ fontSize: '14px', color: 'var(--teal-dark)', marginBottom: '12px' }}>
                  ✓ {t('association.profile.verification.verified')}
                </p>
              ) : (
                <p style={{ fontSize: '14px', color: 'var(--slate-lavender)', marginBottom: '12px' }}>
                  {t('association.profile.verification.pendingText')}
                </p>
              )}
              <p className="fhint">{t('association.profile.verification.comingSoon')}</p>
            </div>
          </div>
        </div>
      )}

      {/* ══ Onglet : Compte bancaire ══════════════════════════════════════ */}
      {activeTab === 'bank' && (
        <div className="set-tab-content active">
          {/* Carte Monerium */}
          <div className="card no-hover" style={{ marginBottom: '20px' }}>
            <div className="card-h">
              <h3>{t('association.profile.monerium.title')}</h3>
              <span className="badge badge-info">{t('association.profile.monerium.badge')}</span>
            </div>
            <div className="card-b">
              <p style={{ fontSize: '14px', color: 'var(--slate-lavender)', marginBottom: '16px', lineHeight: '1.6' }}>
                {t('association.profile.monerium.description')}
              </p>
              {moneriumLoading ? (
                <div style={{ width: '20px', height: '20px', border: '2px solid var(--deep-indigo)', borderTopColor: 'transparent', borderRadius: '50%', animation: 'spin 0.8s linear infinite' }} />
              ) : connected ? (
                <span className="badge badge-active">
                  {t('association.profile.monerium.connectedStatus')}
                </span>
              ) : moneriumInterrupted ? (
                <button
                  type="button"
                  onClick={() => {
                    setMoneriumInterrupted(false);
                    setShowMoneriumModal(true);
                  }}
                  className="btn btn-secondary btn-sm"
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
                  className="btn btn-primary btn-sm"
                >
                  {t('association.profile.monerium.connect')}
                </button>
              )}
            </div>
          </div>

          {/* Carte Sécurité */}
          <div className="card no-hover">
            <div className="card-h">
              <h3>{t('association.profile.security.title')}</h3>
            </div>
            <div className="card-b">
              <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
                <div>
                  <p className="fl">{t('association.profile.security.loginMethod')}</p>
                  <p style={{ fontSize: '14px' }}>
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
        </div>
      )}

      {/* ── SetPassword modal ────────────────────────────────────────────── */}
      {showPasswordModal && (
        <div className="ov" onClick={() => setShowPasswordModal(false)}>
          <div className="mod" onClick={(e) => e.stopPropagation()}>
            <div className="mod-h">
              <h3>{t('association.profile.security.changePassword')}</h3>
              <button className="mod-x" onClick={() => setShowPasswordModal(false)}>✕</button>
            </div>
            <div className="mod-b">
              <SetPasswordForm
                onSubmit={handlePasswordSubmit}
                onSkip={() => setShowPasswordModal(false)}
                loading={passwordLoading}
              />
            </div>
          </div>
        </div>
      )}

      {/* ── Monerium onboard modal ───────────────────────────────────────── */}
      <MoneriumOnboardModal
        isOpen={showMoneriumModal}
        onClose={() => setShowMoneriumModal(false)}
        onConnected={() => {
          setMoneriumInterrupted(false);
          refreshMonerium();
        }}
        onPopupClosed={handlePopupClosed}
      />
    </div>
  );
}
