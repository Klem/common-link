'use client';

import { useState, useCallback, useEffect } from 'react';
import { useTranslations } from 'next-intl';
import { useSearchParams } from 'next/navigation';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { z } from 'zod';
import { useAuthStore } from '@/stores/authStore';
import { useAssociationProfile } from '@/hooks/dashboard/useAssociationProfile';
import { useMoneriumStatus } from '@/hooks/monerium/useMoneriumStatus';
import { useMollieKycStatus } from '@/hooks/mollie/useMollieKycStatus';
import { Topbar } from '@/components/dashboard/Topbar';
import { SetPasswordForm } from '@/components/auth/SetPasswordForm';
import MoneriumOnboardModal from '@/components/dashboard/MoneriumOnboardModal';
import MollieOnboardModal from '@/components/dashboard/MollieOnboardModal';
import { useSetPassword } from '@/hooks/auth/useSetPassword';
import { VerificationTab } from '@/components/settings/VerificationTab';
import { MandateTab } from '@/components/settings/MandateTab';
import { WidgetTab } from '@/components/settings/WidgetTab';
import { useMandate } from '@/hooks/dashboard/useMandate';

// ─── Schema ──────────────────────────────────────────────────────────────────

const CURRENT_YEAR = new Date().getFullYear();

const profileSchema = z.object({
  siren: z
    .string()
    .optional()
    .refine((v) => !v || /^\d{9}$/.test(v), 'dashboard.association.profile.errors.sirenFormat'),
  creationYear: z
    .number()
    .int()
    .min(1800, 'dashboard.association.profile.errors.creationYearMin')
    .max(CURRENT_YEAR, 'dashboard.association.profile.errors.creationYearMax')
    .optional()
    .or(z.nan().transform(() => undefined)),
  contactEmail: z
    .string()
    .optional()
    .refine((v) => !v || z.string().email().safeParse(v).success, 'dashboard.association.profile.errors.contactEmailFormat'),
  phone: z
    .string()
    .optional()
    .refine((v) => !v || /^[0-9 +().\-]{6,20}$/.test(v), 'dashboard.association.profile.errors.phoneFormat'),
  addressLine1: z.string().max(255).optional(),
  legalObject: z.string().max(2000).optional(),
  signerName: z.string().max(255).optional(),
  signerRole: z.string().max(100).optional(),
});

type ProfileFormData = z.infer<typeof profileSchema>;

type SettingsTab = 'infos' | 'verif' | 'bank' | 'mandate' | 'widget';

const PROVIDER_KEYS = {
  GOOGLE: 'association.profile.security.google',
  EMAIL: 'association.profile.security.email',
  MAGIC_LINK: 'association.profile.security.magicLink',
} as const;

// Monerium est temporairement masqué de l'UI — code conservé intégralement, réactivation = passer à true.
const MONERIUM_ENABLED = false

// ─── Page ─────────────────────────────────────────────────────────────────────

export default function AssociationProfilePage() {
  const t = useTranslations('dashboard');
  const tM = useTranslations('settings');
  const user = useAuthStore((s) => s.user);
  const { profile, isLoading, updateProfile, refreshProfile } = useAssociationProfile();
  const searchParams = useSearchParams();
  const [activeTab, setActiveTab] = useState<SettingsTab>('infos');
  const [verifStatus, setVerifStatus] = useState<string | null>(null);

  useEffect(() => {
    const tab = searchParams.get('tab');
    if (tab === 'verif' || tab === 'bank' || tab === 'mandate' || tab === 'infos' || tab === 'widget') {
      setActiveTab(tab as SettingsTab);
    }
  }, [searchParams]);
  const [showPasswordModal, setShowPasswordModal] = useState(false);
  const [showMoneriumModal, setShowMoneriumModal] = useState(false);
  const [moneriumInterrupted, setMoneriumInterrupted] = useState(false);
  const { connected, pending, isLoading: moneriumLoading, refresh: refreshMonerium } =
    useMoneriumStatus(MONERIUM_ENABLED);
  const [showMollieModal, setShowMollieModal] = useState(false);
  const [mollieInterrupted, setMollieInterrupted] = useState(false);
  const {
    connected: mollieConnected,
    pending: molliePending,
    broken: mollieBroken,
    onboardingStatus,
    canReceivePayments,
    dashboardUrl: mollieDashboardUrl,
    isLoading: mollieLoading,
    refresh: refreshMollie,
  } = useMollieKycStatus();
  const { state: mandateState, isLoading: mandateLoading, uploadDoc, deleteDoc, sign, revoke, downloadPdf } =
    useMandate();

  const handlePopupClosed = useCallback(async () => {
    setMoneriumInterrupted(true);
    await refreshMonerium();
  }, [refreshMonerium]);

  const handleMolliePopupClosed = useCallback(async () => {
    setMollieInterrupted(true);
    await refreshMollie();
  }, [refreshMollie]);

  const { onSubmit: submitPassword, loading: passwordLoading } = useSetPassword();

  const {
    register,
    handleSubmit,
    reset,
    formState: { isDirty, isSubmitting, errors },
  } = useForm<ProfileFormData>({
    resolver: zodResolver(profileSchema),
    values: {
      siren: profile?.siren ?? '',
      creationYear: profile?.creationYear ?? undefined,
      contactEmail: profile?.contactEmail ?? '',
      phone: profile?.phone ?? '',
      addressLine1: profile?.addressLine1 ?? '',
      legalObject: profile?.legalObject ?? '',
      signerName: profile?.signerName ?? '',
      signerRole: profile?.signerRole ?? '',
    },
  });

  const onSubmit = handleSubmit(async (data) => {
    await updateProfile({
      siren: data.siren || undefined,
      creationYear: data.creationYear,
      contactEmail: data.contactEmail || undefined,
      phone: data.phone || undefined,
      addressLine1: data.addressLine1 || undefined,
      legalObject: data.legalObject || undefined,
      signerName: data.signerName || undefined,
      signerRole: data.signerRole || undefined,
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
          <span className={`set-tab-badge${
            (verifStatus ?? profile?.verificationStatus) === 'VERIFIED' ? ' ok' :
            (verifStatus ?? profile?.verificationStatus) === 'PENDING' ? ' pending' : ''
          }`}>
            {(verifStatus ?? profile?.verificationStatus) === 'VERIFIED'
              ? t('association.profile.tabs.verifBadge.ok')
              : (verifStatus ?? profile?.verificationStatus) === 'PENDING'
              ? t('association.profile.tabs.verifBadge.pending')
              : (verifStatus ?? profile?.verificationStatus) === 'REJECTED'
              ? t('association.profile.tabs.verifBadge.rejected')
              : t('association.profile.tabs.verifBadge.todo')}
          </span>
        </button>
        <button
          className={`set-tab${activeTab === 'bank' ? ' active' : ''}`}
          onClick={() => setActiveTab('bank')}
        >
          🏦 {t('association.profile.tabs.bank')}{' '}
          <span className={`set-tab-badge${(mollieConnected && canReceivePayments) ? ' ok' : ''}`}>
            {(mollieConnected && canReceivePayments)
              ? t('association.profile.tabs.bankBadge.connected')
              : t('association.profile.tabs.bankBadge.notConnected')}
          </span>
        </button>
        <button
          className={`set-tab${activeTab === 'mandate' ? ' active' : ''}`}
          onClick={() => setActiveTab('mandate')}
        >
          🧾 {t('association.profile.tabs.mandate')}{' '}
          <span
            className={`set-tab-badge${
              mandateState?.signed
                ? ' ok'
                : !mandateState?.blocked && mandateState?.mandateDocs.filter((d) => d.uploaded).length === 2
                ? ' pending'
                : ''
            }`}
          >
            {mandateState?.signed
              ? t('association.profile.tabs.mandateBadge.active')
              : !mandateState?.blocked && mandateState?.mandateDocs.filter((d) => d.uploaded).length === 2
              ? t('association.profile.tabs.mandateBadge.readyToSign')
              : t('association.profile.tabs.mandateBadge.notSigned')}
          </span>
        </button>
        <button
          className={`set-tab${activeTab === 'widget' ? ' active' : ''}`}
          onClick={() => setActiveTab('widget')}
        >
          🎁 {t('association.profile.tabs.widget')}
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
                <p className="profile-loading">
                  {t('association.profile.loading')}
                </p>
              ) : (
                <form onSubmit={onSubmit} noValidate>
                  {/* Ligne 1 : Nom (read-only) | N° RNA */}
                  <div className="frow">
                    <div className="fg">
                      <label className="fl">{t('association.profile.name')}</label>
                      <input className="fi" type="text" value={profile?.name ?? ''} disabled />
                    </div>
                    <div className="fg">
                      <label htmlFor="siren" className="fl">
                        {t('association.profile.siren')}
                      </label>
                      <input
                        id="siren"
                        type="text"
                        className="fi"
                        placeholder="123456789"
                        {...register('siren')}
                      />
                      {errors.siren && (
                        <p className="fhint error">{t(errors.siren.message as Parameters<typeof t>[0])}</p>
                      )}
                    </div>
                  </div>

                  {/* Ligne 2 : SIRET (read-only) | Année de création */}
                  <div className="frow">
                    <div className="fg">
                      <label className="fl">{t('association.profile.identifier')}</label>
                      <input className="fi" type="text" value={profile?.identifier ?? ''} disabled />
                    </div>
                    <div className="fg">
                      <label htmlFor="creationYear" className="fl">
                        {t('association.profile.creationYear')}
                      </label>
                      <input
                        id="creationYear"
                        type="number"
                        className="fi"
                        min={1800}
                        max={CURRENT_YEAR}
                        {...register('creationYear', { valueAsNumber: true })}
                      />
                      {errors.creationYear && (
                        <p className="fhint error">{t(errors.creationYear.message as Parameters<typeof t>[0])}</p>
                      )}
                    </div>
                  </div>

                  {/* Ligne 3 : Email de contact | Téléphone */}
                  <div className="frow">
                    <div className="fg">
                      <label htmlFor="contactEmail" className="fl">
                        {t('association.profile.contactEmail')}
                      </label>
                      <input
                        id="contactEmail"
                        type="email"
                        className="fi"
                        {...register('contactEmail')}
                      />
                      {errors.contactEmail && (
                        <p className="fhint error">{t(errors.contactEmail.message as Parameters<typeof t>[0])}</p>
                      )}
                    </div>
                    <div className="fg">
                      <label htmlFor="phone" className="fl">
                        {t('association.profile.phone')}
                      </label>
                      <input
                        id="phone"
                        type="tel"
                        className="fi"
                        {...register('phone')}
                      />
                      {errors.phone && (
                        <p className="fhint error">{t(errors.phone.message as Parameters<typeof t>[0])}</p>
                      )}
                    </div>
                  </div>

                  {/* Ligne 4 : Adresse postale (full width) */}
                  <div className="frow">
                    <div className="fg" style={{ flex: '1 1 100%' }}>
                      <label htmlFor="addressLine1" className="fl">
                        {t('association.profile.addressLine1')}
                      </label>
                      <input
                        id="addressLine1"
                        type="text"
                        className="fi"
                        placeholder={t('association.profile.addressLine1Placeholder')}
                        {...register('addressLine1')}
                      />
                    </div>
                  </div>

                  {/* Ligne 5 : Objet social (full width) */}
                  <div className="frow">
                    <div className="fg" style={{ flex: '1 1 100%' }}>
                      <label htmlFor="legalObject" className="fl">
                        {t('association.profile.legalObject')}
                      </label>
                      <input
                        id="legalObject"
                        type="text"
                        className="fi"
                        placeholder={t('association.profile.legalObjectPlaceholder')}
                        {...register('legalObject')}
                      />
                    </div>
                  </div>

                  {/* Ligne 6 : Signataire nom | Fonction */}
                  <div className="frow">
                    <div className="fg">
                      <label htmlFor="signerName" className="fl">
                        {t('association.profile.signerName')}
                      </label>
                      <input
                        id="signerName"
                        type="text"
                        className="fi"
                        placeholder={t('association.profile.signerNamePlaceholder')}
                        {...register('signerName')}
                      />
                    </div>
                    <div className="fg">
                      <label htmlFor="signerRole" className="fl">
                        {t('association.profile.signerRole')}
                      </label>
                      <input
                        id="signerRole"
                        type="text"
                        className="fi"
                        placeholder={t('association.profile.signerRolePlaceholder')}
                        {...register('signerRole')}
                      />
                    </div>
                  </div>

                  <div className="frow-actions">
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
          <VerificationTab
            onGoToVerif={() => setActiveTab('verif')}
            onVerificationSubmitted={() => setVerifStatus('PENDING')}
          />
        </div>
      )}

      {/* ══ Onglet : Compte bancaire ══════════════════════════════════════ */}
      {activeTab === 'bank' && (
        <div className="set-tab-content active">
          {/* Carte Monerium */}
          {MONERIUM_ENABLED && (
          <div className="card no-hover monerium-card">
            <div className="card-h">
              <h3>{t('association.profile.monerium.title')}</h3>
              <span className="badge badge-info">{t('association.profile.monerium.badge')}</span>
            </div>
            <div className="card-b">
              <p className="monerium-desc">
                {t('association.profile.monerium.description')}
              </p>
              {moneriumLoading ? (
                <div className="monerium-spinner" />
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
          )}

          {/* Carte Mollie */}
          <div className="card no-hover mollie-kyc-card">
            <div className="card-h">
              <h3>{tM('mollie.title')}</h3>
            </div>
            <div className="card-b">
              <p className="monerium-desc">{tM('mollie.description')}</p>
              {mollieLoading ? (
                <div className="monerium-spinner" />
              ) : mollieConnected && !mollieBroken && onboardingStatus === 'COMPLETED' && canReceivePayments ? (
                <span className="badge badge-active">{tM('mollie.status.completed')}</span>
              ) : mollieBroken ? (
                <div className="flex items-center gap-3">
                  <span className="badge badge-error">{tM('mollie.status.broken')}</span>
                  <button
                    type="button"
                    onClick={() => setShowMollieModal(true)}
                    className="btn btn-secondary btn-sm"
                  >
                    {tM('mollie.reconnect')}
                  </button>
                </div>
              ) : mollieConnected && onboardingStatus === 'IN_REVIEW' ? (
                <span className="badge badge-warning">{tM('mollie.status.inReview')}</span>
              ) : mollieConnected && onboardingStatus === 'NEEDS_DATA' ? (
                <div className="flex items-center gap-3">
                  <span className="badge badge-warning">{tM('mollie.status.needsData')}</span>
                  {mollieDashboardUrl && (
                    <a
                      href={mollieDashboardUrl}
                      target="_blank"
                      rel="noopener noreferrer"
                      className="btn btn-primary btn-sm"
                    >
                      {tM('mollie.completeOnboarding')}
                    </a>
                  )}
                </div>
              ) : mollieInterrupted ? (
                <button
                  type="button"
                  onClick={() => {
                    setMollieInterrupted(false);
                    setShowMollieModal(true);
                  }}
                  className="btn btn-secondary btn-sm"
                >
                  {tM('mollie.tryAgain')}
                </button>
              ) : molliePending ? (
                <span className="badge badge-warning">{tM('mollie.status.pending')}</span>
              ) : (
                <button
                  type="button"
                  onClick={() => setShowMollieModal(true)}
                  className="btn btn-primary btn-sm"
                >
                  {tM('mollie.connect')}
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
              <div className="security-row">
                <div>
                  <p className="fl">{t('association.profile.security.loginMethod')}</p>
                  <p className="security-value">
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

      {/* ══ Onglet : Mandat fiscal ══════════════════════════════════════════ */}
      {activeTab === 'mandate' && (
        <div className="set-tab-content active">
          <MandateTab
            state={mandateState}
            isLoading={mandateLoading}
            onGoToVerif={() => setActiveTab('verif')}
            onUploadDoc={uploadDoc}
            onDeleteDoc={deleteDoc}
            onSign={sign}
            onRevoke={revoke}
            onDownloadPdf={downloadPdf}
          />
        </div>
      )}

      {/* ══ Onglet : Widget de don ═══════════════════════════════════════ */}
      {activeTab === 'widget' && (
        <div className="set-tab-content active">
          <WidgetTab
            profile={profile}
            onTokenChanged={refreshProfile}
          />
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

      {/* ── Mollie onboard modal ─────────────────────────────────────────── */}
      <MollieOnboardModal
        isOpen={showMollieModal}
        onClose={() => setShowMollieModal(false)}
        onConnected={() => {
          setMollieInterrupted(false);
          refreshMollie();
        }}
        onPopupClosed={handleMolliePopupClosed}
        contactEmail={profile?.contactEmail}
      />
    </div>
  );
}
