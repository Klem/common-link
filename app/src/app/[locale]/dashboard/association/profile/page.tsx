'use client';

import { useState, useCallback, useEffect, useRef } from 'react';
import { useTranslations } from 'next-intl';
import { useSearchParams } from 'next/navigation';
import { useForm } from 'react-hook-form';
import type { Path, RegisterOptions } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { z } from 'zod';
import { useAuthStore } from '@/stores/authStore';
import { useToastStore } from '@/stores/toastStore';
import { useAssociationProfile } from '@/hooks/dashboard/useAssociationProfile';
import { useMoneriumStatus } from '@/hooks/monerium/useMoneriumStatus';
import { useMollieKycStatus } from '@/hooks/mollie/useMollieKycStatus';
import { Topbar } from '@/components/dashboard/Topbar';
import { SetPasswordForm } from '@/components/auth/SetPasswordForm';
import MoneriumOnboardModal from '@/components/dashboard/MoneriumOnboardModal';
import MollieOnboardModal from '@/components/dashboard/MollieOnboardModal';
import { forceCompleteMollieOnboarding } from '@/lib/api/mollie-connect';
import { MollieOnboardingStatus } from '@/types/mollie-connect';
import { useSetPassword } from '@/hooks/auth/useSetPassword';
import { VerificationTab } from '@/components/settings/VerificationTab';
import { MandateTab } from '@/components/settings/MandateTab';
import { WidgetTab } from '@/components/settings/WidgetTab';
import { LandingTab } from '@/components/settings/LandingTab';
import { useMandate } from '@/hooks/dashboard/useMandate';
import { useDebouncedPatchSave } from '@/hooks/campaign/useDebouncedSave';
import { VerificationStatus } from '@/types/association';
import type {
  AssociationProfileDto,
  UpdateAssociationProfileRequest,
} from '@/types/association';

// ─── Schema ──────────────────────────────────────────────────────────────────

const CURRENT_YEAR = new Date().getFullYear();

const profileSchema = z.object({
  contactName: z
    .string()
    .optional()
    .refine((v) => !v || v.length >= 2, 'dashboard.association.profile.errors.contactNameMin'),
  siren: z
    .string()
    .optional()
    // 9 caractères alphanumériques — miroir du @Pattern côté back (Mollie refuse
    // le client-link en 422 si le registrationNumber n'a pas ce format).
    .refine((v) => !v || /^[A-Za-z0-9]{9}$/.test(v), 'dashboard.association.profile.errors.sirenFormat'),
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

/** Formulaire vide — baseline avant l'arrivée du profil. */
const EMPTY_PROFILE_FORM: ProfileFormData = {
  contactName: '',
  siren: '',
  creationYear: undefined,
  contactEmail: '',
  phone: '',
  addressLine1: '',
  legalObject: '',
  signerName: '',
  signerRole: '',
};

/** Projette le profil serveur sur les champs du formulaire. */
function toFormData(profile: AssociationProfileDto): ProfileFormData {
  return {
    contactName: profile.contactName ?? '',
    siren: profile.siren ?? '',
    creationYear: profile.creationYear ?? undefined,
    contactEmail: profile.contactEmail ?? '',
    phone: profile.phone ?? '',
    addressLine1: profile.addressLine1 ?? '',
    legalObject: profile.legalObject ?? '',
    signerName: profile.signerName ?? '',
    signerRole: profile.signerRole ?? '',
  };
}

type SettingsTab = 'infos' | 'verif' | 'bank' | 'mandate' | 'widget' | 'landing';

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
  const { addToast } = useToastStore();
  const searchParams = useSearchParams();
  const [activeTab, setActiveTab] = useState<SettingsTab>('infos');
  const [verifStatus, setVerifStatus] = useState<VerificationStatus | null>(null);
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
    canForceComplete: mollieCanForceComplete,
    isLoading: mollieLoading,
    refresh: refreshMollie,
  } = useMollieKycStatus();
  const { state: mandateState, isLoading: mandateLoading, uploadDoc, deleteDoc, sign, revoke, downloadPdf } =
    useMandate();

  // ─── Verrouillage séquentiel des onglets ───────────────────────────────────
  // Chaque onglet se débloque quand son prérequis DIRECT est terminé (= condition
  // du badge "ok" du prérequis, pour que lock et badge ne divergent jamais).
  const verifDone = (verifStatus ?? profile?.verificationStatus) === VerificationStatus.VERIFIED;
  const mandateDone = mandateState?.signed === true;
  // Nom et email de contact sont figés dès que le KYC Mollie est *lancé* — ils partent chez Mollie
  // à la création du client-link, avant le callback OAuth (qui seul crée la MollieConnection). Un état
  // OAuth encore vivant (`pending`) suffit donc à verrouiller. Miroir de `OnboardingGateService.isMollieKycStarted`.
  const contactLocked = mollieConnected === true || molliePending === true;
  // Même prédicat que le gate backend (`MollieConnection.canCollectDonations`) et que
  // `BankSetupStatus.COMPLETED` : un widget ne peut pas fonctionner sur une campagne non publiée,
  // donc le déverrouillage du widget ne doit jamais être plus permissif que la publication.
  const bankDone = Boolean(
    mollieConnected && !mollieBroken && onboardingStatus === MollieOnboardingStatus.COMPLETED && canReceivePayments,
  );
  const tabUnlocked: Record<SettingsTab, boolean> = {
    infos: true,
    verif: true,
    mandate: verifDone || (verifStatus ?? profile?.verificationStatus) === VerificationStatus.PENDING,
    bank: mandateDone,
    widget: bankDone,
    // Même prérequis que le widget : la landing page sert le même formulaire de don, sur la
    // même campagne publiée. Un gate plus permissif afficherait un lien renvoyant un 409.
    landing: bankDone,
  };

  // Deep-link ?tab= : n'active l'onglet demandé que s'il est déverrouillé, et
  // seulement une fois les données chargées (sinon un user déjà onboardé serait
  // renvoyé sur "infos" au premier render tant que les flags sont encore false).
  useEffect(() => {
    if (isLoading || mandateLoading || mollieLoading) return;
    const tab = searchParams.get('tab');
    if (
      tab === 'verif' || tab === 'bank' || tab === 'mandate' || tab === 'infos' ||
      tab === 'widget' || tab === 'landing'
    ) {
      setActiveTab(tabUnlocked[tab] ? tab : 'infos');
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [searchParams, isLoading, mandateLoading, mollieLoading, verifDone, mandateDone, bankDone]);

  const handlePopupClosed = useCallback(async () => {
    setMoneriumInterrupted(true);
    await refreshMonerium();
  }, [refreshMonerium]);

  const handleMolliePopupClosed = useCallback(async () => {
    setMollieInterrupted(true);
    await refreshMollie();
  }, [refreshMollie]);

  // DEV/STAGING only — simulates a Mollie KYC validation (button gated by mollieCanForceComplete).
  const handleMollieForceComplete = useCallback(async () => {
    try {
      await forceCompleteMollieOnboarding();
    } finally {
      await refreshMollie();
    }
  }, [refreshMollie]);

  const { onSubmit: submitPassword, loading: passwordLoading } = useSetPassword();

  const {
    register,
    handleSubmit,
    reset,
    trigger,
    getValues,
    formState: { isDirty, isSubmitting, errors },
  } = useForm<ProfileFormData>({
    resolver: zodResolver(profileSchema),
    // `defaultValues` (et non `values`) : une re-sync réactive sur `profile`
    // écraserait les caractères tapés pendant un autosave en vol. Le formulaire
    // est hydraté une seule fois, au premier profil reçu.
    defaultValues: EMPTY_PROFILE_FORM,
  });

  const hydrated = useRef(false);
  useEffect(() => {
    if (!profile || hydrated.current) return;
    hydrated.current = true;
    reset(toFormData(profile));
  }, [profile, reset]);

  /**
   * Autosave debouncé (800 ms) — même contrat que `CampaignInfoTab` :
   * patchs fusionnés, envoi silencieux (pas de toast), flush au démontage.
   * `reset(getValues())` remet la baseline de `isDirty` sur les valeurs courantes sans toucher à
   * la saisie en cours — mais seulement si le formulaire est entièrement valide. Rebaser alors
   * qu'un autre champ est invalide effacerait son message d'erreur et installerait sa valeur
   * invalide comme référence : `isDirty` retomberait à faux, le bouton Enregistrer s'éteindrait
   * et la saisie fautive ne partirait jamais.
   */
  const errorsRef = useRef(errors);
  errorsRef.current = errors;

  const { schedule, cancel } = useDebouncedPatchSave<UpdateAssociationProfileRequest>(
    async (patch) => {
      await updateProfile(patch, true);
      if (Object.keys(errorsRef.current).length === 0) reset(getValues());
    },
  );

  /**
   * Wrappe `register` pour planifier un autosave après chaque frappe.
   * Le patch n'est planifié que si le champ passe sa validation zod : un SIREN
   * à moitié tapé ne doit pas partir en PATCH (le back le refuserait en 422).
   */
  const autosave = <K extends Path<ProfileFormData>>(
    name: K,
    options?: RegisterOptions<ProfileFormData, K>,
  ) => {
    const registered = register(name, options);
    return {
      ...registered,
      onChange: async (event: Parameters<typeof registered.onChange>[0]) => {
        await registered.onChange(event);
        if (!(await trigger(name))) return;
        const value = getValues(name);
        const normalized =
          value === '' || (typeof value === 'number' && Number.isNaN(value)) ? undefined : value;
        schedule({ [name]: normalized } as Partial<UpdateAssociationProfileRequest>);
      },
    };
  };

  const onSubmit = handleSubmit(async (data) => {
    cancel();
    await updateProfile({
      contactName: data.contactName || undefined,
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
            (verifStatus ?? profile?.verificationStatus) === VerificationStatus.VERIFIED ? ' ok' :
            (verifStatus ?? profile?.verificationStatus) === VerificationStatus.PENDING ? ' pending' : ''
          }`}>
            {(verifStatus ?? profile?.verificationStatus) === VerificationStatus.VERIFIED
              ? t('association.profile.tabs.verifBadge.ok')
              : (verifStatus ?? profile?.verificationStatus) === VerificationStatus.PENDING
              ? t('association.profile.tabs.verifBadge.pending')
              : (verifStatus ?? profile?.verificationStatus) === VerificationStatus.REJECTED
              ? t('association.profile.tabs.verifBadge.rejected')
              : t('association.profile.tabs.verifBadge.todo')}
          </span>
        </button>
        <button
          className={`set-tab${activeTab === 'mandate' ? ' active' : ''}${tabUnlocked.mandate ? '' : ' locked'}`}
          onClick={() => tabUnlocked.mandate && setActiveTab('mandate')}
          disabled={!tabUnlocked.mandate}
          title={tabUnlocked.mandate ? undefined : t('association.profile.tabs.locked')}
        >
          {tabUnlocked.mandate ? '🧾' : '🔒'} {t('association.profile.tabs.mandate')}{' '}
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
          className={`set-tab${activeTab === 'bank' ? ' active' : ''}${tabUnlocked.bank ? '' : ' locked'}`}
          onClick={() => tabUnlocked.bank && setActiveTab('bank')}
          disabled={!tabUnlocked.bank}
          title={tabUnlocked.bank ? undefined : t('association.profile.tabs.locked')}
        >
          {tabUnlocked.bank ? '🏦' : '🔒'} {t('association.profile.tabs.bank')}{' '}
          <span className={`set-tab-badge${bankDone ? ' ok' : ''}`}>
            {bankDone
              ? t('association.profile.tabs.bankBadge.connected')
              : t('association.profile.tabs.bankBadge.notConnected')}
          </span>
        </button>
        <button
          className={`set-tab${activeTab === 'widget' ? ' active' : ''}${tabUnlocked.widget ? '' : ' locked'}`}
          onClick={() => tabUnlocked.widget && setActiveTab('widget')}
          disabled={!tabUnlocked.widget}
          title={tabUnlocked.widget ? undefined : t('association.profile.tabs.locked')}
        >
          {tabUnlocked.widget ? '🎁' : '🔒'} {t('association.profile.tabs.widget')}
        </button>
        <button
          className={`set-tab${activeTab === 'landing' ? ' active' : ''}${tabUnlocked.landing ? '' : ' locked'}`}
          onClick={() => tabUnlocked.landing && setActiveTab('landing')}
          disabled={!tabUnlocked.landing}
          title={tabUnlocked.landing ? undefined : t('association.profile.tabs.locked')}
        >
          {tabUnlocked.landing ? '🌐' : '🔒'} {t('association.profile.tabs.landing')}
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
                      <p className="fhint">{t('association.profile.readOnly.permanent')}</p>
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
                        disabled={verifDone}
                        {...autosave('siren')}
                      />
                      {verifDone && (
                        <p className="fhint">{t('association.profile.readOnly.verified')}</p>
                      )}
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
                      <p className="fhint">{t('association.profile.readOnly.permanent')}</p>
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
                        disabled={verifDone}
                        {...autosave('creationYear', { valueAsNumber: true })}
                      />
                      {verifDone && (
                        <p className="fhint">{t('association.profile.readOnly.verified')}</p>
                      )}
                      {errors.creationYear && (
                        <p className="fhint error">{t(errors.creationYear.message as Parameters<typeof t>[0])}</p>
                      )}
                    </div>
                  </div>

                  {/* Ligne 3 : Nom du contact * | Email de contact * */}
                  <div className="frow">
                    <div className="fg">
                      <label htmlFor="contactName" className="fl">
                        {t('association.profile.contactName')}<span className="text-red-500 ml-0.5">*</span>{' '}
                        <span className="tip" data-tip={t('association.profile.contactNameTip')}>?</span>
                      </label>
                      <input
                        id="contactName"
                        type="text"
                        className="fi"
                        placeholder={t('association.profile.contactNamePlaceholder')}
                        disabled={contactLocked}
                        {...autosave('contactName')}
                      />
                      {contactLocked && (
                        <p className="fhint">{t('association.profile.readOnly.mollieCompleted')}</p>
                      )}
                      {errors.contactName && (
                        <p className="fhint error">{t(errors.contactName.message as Parameters<typeof t>[0])}</p>
                      )}
                    </div>
                    <div className="fg">
                      <label htmlFor="contactEmail" className="fl">
                        {t('association.profile.contactEmail')}<span className="text-red-500 ml-0.5">*</span>{' '}
                        <span className="tip" data-tip={t('association.profile.contactEmailTip')}>?</span>
                      </label>
                      <input
                        id="contactEmail"
                        type="email"
                        className="fi"
                        disabled={contactLocked}
                        {...autosave('contactEmail')}
                      />
                      {contactLocked && (
                        <p className="fhint">{t('association.profile.readOnly.mollieCompleted')}</p>
                      )}
                      {errors.contactEmail && (
                        <p className="fhint error">{t(errors.contactEmail.message as Parameters<typeof t>[0])}</p>
                      )}
                    </div>
                  </div>

                  {/* Ligne 4 : Téléphone */}
                  <div className="frow">
                    <div className="fg">
                      <label htmlFor="phone" className="fl">
                        {t('association.profile.phone')}
                      </label>
                      <input
                        id="phone"
                        type="tel"
                        className="fi"
                        {...autosave('phone')}
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
                        {...autosave('addressLine1')}
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
                        {...autosave('legalObject')}
                      />
                    </div>
                  </div>

                  {/* Ligne 6 : Signataire nom | Fonction */}
                  <div className="frow">
                    <div className="fg">
                      <label htmlFor="signerName" className="fl">
                        {t('association.profile.signerName')}<span className="text-red-500 ml-0.5">*</span>{' '}
                        <span className="tip" data-tip={t('association.profile.signerNameTip')}>?</span>
                      </label>
                      <input
                        id="signerName"
                        type="text"
                        className="fi"
                        placeholder={t('association.profile.signerNamePlaceholder')}
                        {...autosave('signerName')}
                      />
                    </div>
                    <div className="fg">
                      <label htmlFor="signerRole" className="fl">
                        {t('association.profile.signerRole')}<span className="text-red-500 ml-0.5">*</span>{' '}
                        <span className="tip" data-tip={t('association.profile.signerRoleTip')}>?</span>
                      </label>
                      <input
                        id="signerRole"
                        type="text"
                        className="fi"
                        placeholder={t('association.profile.signerRolePlaceholder')}
                        {...autosave('signerRole')}
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
            onVerificationSubmitted={() => {
              setVerifStatus(VerificationStatus.PENDING);
              setActiveTab('mandate');
            }}
          />
        </div>
      )}

      {/* ══ Onglet : Compte bancaire ══════════════════════════════════════ */}
      {activeTab === 'bank' && (
        <div className="set-tab-content active">
          {/* Carte Monerium */}
          {MONERIUM_ENABLED && (
          <div className="card no-hover monerium-card mb-5">
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
          <div className="card no-hover mollie-kyc-card mb-5">
            <div className="card-h">
              <h3>{tM('mollie.title')}</h3>
            </div>
            <div className="card-b">
              <p className="monerium-desc">{tM('mollie.description')}</p>
              {mollieLoading ? (
                <div className="monerium-spinner" />
              ) : bankDone ? (
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
                  onClick={() => {
                    if (!profile?.contactEmail) {
                      addToast('error', 'mollieErrorMissingContactEmail');
                      return;
                    }
                    if (!profile?.contactName) {
                      addToast('error', 'mollieErrorMissingContactName');
                      return;
                    }
                    setShowMollieModal(true);
                  }}
                  className="btn btn-primary btn-sm"
                >
                  {tM('mollie.connect')}
                </button>
              )}
              {mollieCanForceComplete && mollieConnected && onboardingStatus !== 'COMPLETED' && (
                <button
                  type="button"
                  onClick={handleMollieForceComplete}
                  className="btn btn-ghost btn-sm mt-3"
                >
                  {tM('mollie.devForceComplete')}
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
            onSign={async (request) => { await sign(request); setActiveTab('bank'); }}
            signerName={profile?.signerName}
            signerRole={profile?.signerRole}
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

      {/* ══ Onglet : Landing page ════════════════════════════════════════ */}
      {activeTab === 'landing' && (
        <div className="set-tab-content active">
          <LandingTab
            profile={profile}
            onGoToWidget={() => setActiveTab('widget')}
            onConfigChanged={refreshProfile}
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
        onConnected={async () => {
          setMollieInterrupted(false);
          await refreshMollie();
          setActiveTab('widget');
        }}
        onPopupClosed={handleMolliePopupClosed}
        contactEmail={profile?.contactEmail}
        contactName={profile?.contactName}
      />
    </div>
  );
}
