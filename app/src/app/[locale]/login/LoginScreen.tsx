'use client';

import { useState } from 'react';
import { useTranslations } from 'next-intl';
import { useRouter, usePathname } from 'next/navigation';
import { useLocale } from 'next-intl';
import {
  AuthCard,
  ViewTabs,
  RoleToggle,
  CtxHint,
  GoogleButton,
  Divider,
  EmailPasswordForm,
  EmailRegisterForm,
  MagicLinkForm,
  AssoSearch,
  AssoProfileForm,
  StepIndicator,
  LoginProgressOverlay,
  SetPasswordForm,
} from '@/components/auth';
import type { AssoResult } from '@/components/auth';
import { useGoogleAuth } from '@/hooks/auth/useGoogleAuth';
import { useMagicLink } from '@/hooks/auth/useMagicLink';
import { useEmailLogin } from '@/hooks/auth/useEmailLogin';
import { useEmailRegister } from '@/hooks/auth/useEmailRegister';
import { useMagicLinkVerify } from '@/hooks/auth/useMagicLinkVerify';
import { useSetPassword } from '@/hooks/auth/useSetPassword';
import api from '@/lib/api';
import type { AssoProfileData } from '@/components/auth';

type View = 'login' | 'signup';
type UserRole = 'ASSOCIATION' | 'DONOR';
type PageState = 'auth' | 'setPassword';
type OverlayProvider = 'google' | 'magic' | 'email';

interface LoginScreenProps {
  initialView: View;
  initialRole: UserRole;
  magicLinkToken: string | null;
}

export function LoginScreen({ initialView, initialRole, magicLinkToken }: LoginScreenProps) {
  const t = useTranslations('auth');
  const locale = useLocale();
  const router = useRouter();
  const pathname = usePathname();

  const [activeView, setActiveView] = useState<View>(initialView);
  const [activeRole, setActiveRole] = useState<UserRole>(initialRole);
  const [pageState, setPageState] = useState<PageState>('auth');
  const [showOverlay, setShowOverlay] = useState(!!magicLinkToken);
  const [overlayProvider, setOverlayProvider] = useState<OverlayProvider>(
    magicLinkToken ? 'magic' : 'google',
  );
  const [assoStep, setAssoStep] = useState<1 | 2 | 3>(1);
  const [selectedAsso, setSelectedAsso] = useState<AssoResult | null>(null);
  const [assoProfileLoading, setAssoProfileLoading] = useState(false);

  // Hooks
  const googleAuth = useGoogleAuth();
  const magicLink = useMagicLink();
  const emailLogin = useEmailLogin();
  const emailRegister = useEmailRegister();
  const setPassword = useSetPassword();

  // Magic link token verification (triggered when ?token is present)
  const { status: verifyStatus, error: verifyError } = useMagicLinkVerify(
    magicLinkToken,
    () => setPageState('setPassword'),
  );

  const handleViewChange = (view: View) => {
    setActiveView(view);
    setAssoStep(1);
    setSelectedAsso(null);
    magicLink.reset();
  };

  const handleRoleChange = (role: UserRole) => {
    setActiveRole(role);
    magicLink.reset();
  };

  // ─── Google handlers ──────────────────────────────────────────────────────

  const handleGoogleLogin = async (idToken: string) => {
    setOverlayProvider('google');
    setShowOverlay(true);
    try {
      await googleAuth.login(idToken);
    } catch {
      setShowOverlay(false);
    }
  };

  const handleGoogleSignUp = async (idToken: string) => {
    setOverlayProvider('google');
    setShowOverlay(true);
    try {
      await googleAuth.signUp(idToken, activeRole);
      setShowOverlay(false);
      setPageState('setPassword');
    } catch {
      setShowOverlay(false);
    }
  };

  const handleGoogleSignUpAsso = async (idToken: string) => {
    setOverlayProvider('google');
    setShowOverlay(true);
    try {
      await googleAuth.signUp(idToken, 'ASSOCIATION');
      setShowOverlay(false);
      setAssoStep(3);
    } catch {
      setShowOverlay(false);
    }
  };

  // ─── Email register ───────────────────────────────────────────────────────

  const handleEmailRegisterDonor = async (email: string, password: string) => {
    setOverlayProvider('email');
    setShowOverlay(true);
    try {
      await emailRegister.register(email, password, 'DONOR');
      router.push(`/${locale}/dashboard/donor`);
    } catch {
      setShowOverlay(false);
    }
  };

  const handleEmailRegisterAsso = async (email: string, password: string) => {
    setOverlayProvider('email');
    setShowOverlay(true);
    try {
      await emailRegister.register(email, password, 'ASSOCIATION');
      setShowOverlay(false);
      setAssoStep(3);
    } catch {
      setShowOverlay(false);
    }
  };

  // ─── Email login ──────────────────────────────────────────────────────────

  const handleEmailSubmit = async (email: string, password: string) => {
    setOverlayProvider('email');
    await emailLogin.onSubmit(email, password);
    // On success emailLogin redirects; on error it sets emailLogin.error
  };

  // ─── Association profile submit ───────────────────────────────────────────

  const handleAssoProfileSubmit = async (data: AssoProfileData) => {
    setAssoProfileLoading(true);
    try {
      await api.patch('/api/user/me/association-profile', {
        ...data,
        siren: selectedAsso?.siren,
        nom: selectedAsso?.nom,
        ville: selectedAsso?.ville,
        codePostal: selectedAsso?.codePostal,
      });
      router.push(`/${locale}/dashboard/association`);
    } catch {
      // error handled by toast interceptor
    } finally {
      setAssoProfileLoading(false);
    }
  };

  // ─── Render: magic link verification overlay ──────────────────────────────

  if (showOverlay || verifyStatus === 'verifying') {
    return <LoginProgressOverlay provider={overlayProvider} />;
  }

  // ─── Render: set password (after Google signup or magic link verify) ───────

  if (pageState === 'setPassword') {
    return (
      <div className="min-h-screen bg-bg flex items-center justify-center p-4">
        <div className="w-full max-w-[400px]">
          <div className="flex items-center gap-[10px] justify-center font-display text-[20px] font-extrabold text-green mb-7">
            <div
              className="w-[34px] h-[34px] rounded-[9px] flex items-center justify-center text-[16px] logo-icon-bg"
            >
              🌍
            </div>
            CommonLink
          </div>
          <AuthCard>
            <h2 className="font-display text-[18px] font-bold text-text mb-1">
              {t('setPassword.title')}
            </h2>
            <SetPasswordForm
              onSubmit={setPassword.onSubmit}
              onSkip={setPassword.onSkip}
              loading={setPassword.loading}
            />
          </AuthCard>
        </div>
      </div>
    );
  }

  // ─── Render: auth page ─────────────────────────────────────────────────────

  const assoStepLabels = [
    t('signup.association.steps.search'),
    t('signup.association.steps.connect'),
    t('signup.association.steps.profile'),
  ];

  const isAssoSignup = activeView === 'signup' && activeRole === 'ASSOCIATION';
  const isDonorSignup = activeView === 'signup' && activeRole === 'DONOR';

  return (
    <div className="min-h-screen bg-bg flex items-center justify-center p-4">
      <div className="w-full max-w-[400px]">
        {/* Logo */}
        <div className="flex items-center gap-[10px] justify-center font-display text-[20px] font-extrabold text-green mb-7">
          <div
            className="w-[34px] h-[34px] rounded-[9px] flex items-center justify-center text-[16px] logo-icon-bg"
          >
            🌍
          </div>
          CommonLink
        </div>

        <AuthCard>
          <ViewTabs value={activeView} onChange={handleViewChange} />

          {/* ── LOGIN ─────────────────────────────────────────────────────── */}
          {activeView === 'login' && (
            <>
              <h2 className="font-display text-[18px] font-bold text-text mb-4">
                {t('login.title')}
              </h2>

              {/*<GoogleButton*/}
              {/*  onSuccess={handleGoogleLogin}*/}
              {/*  label={t('login.google')}*/}
              {/*  loading={googleAuth.loading}*/}
              {/*/>*/}

              {googleAuth.error && (
                <p className="text-[11.5px] text-red mt-2">
                  {t(googleAuth.error as Parameters<typeof t>[0])}
                </p>
              )}

              <Divider />

              <EmailPasswordForm
                onSubmit={handleEmailSubmit}
                loading={emailLogin.loading}
                error={emailLogin.error ? t(emailLogin.error as Parameters<typeof t>[0]) : undefined}
              />

              <p className="text-center text-[11.5px] text-muted mt-4">
                {t('login.orMagicLink')}
              </p>

              <div className="mt-3">
                <MagicLinkForm onSubmit={(email) => magicLink.sendLink(email)} role="DONOR" />
              </div>
            </>
          )}

          {/* ── SIGNUP — DONOR ─────────────────────────────────────────────── */}
          {isDonorSignup && (
            <>
              <RoleToggle value={activeRole} onChange={handleRoleChange} />
              <CtxHint variant="donor" />

              <h2 className="font-display text-[18px] font-bold text-text mb-4">
                {t('signup.donor.title')}
              </h2>

              {/*<GoogleButton*/}
              {/*  onSuccess={handleGoogleSignUp}*/}
              {/*  label={t('signup.donor.google')}*/}
              {/*  loading={googleAuth.loading}*/}
              {/*/>*/}

              {googleAuth.error && (
                <p className="text-[11.5px] text-red mt-2">
                  {t(googleAuth.error as Parameters<typeof t>[0])}
                </p>
              )}

              <Divider />

              <MagicLinkForm
                onSubmit={(email) => magicLink.sendLink(email, 'DONOR')}
                role="DONOR"
              />

              <Divider />

              <EmailRegisterForm
                onSubmit={handleEmailRegisterDonor}
                loading={emailRegister.loading}
                error={emailRegister.error ? t(emailRegister.error as Parameters<typeof t>[0]) : undefined}
                submitLabel={t('signup.emailPassword.submit')}
              />
            </>
          )}

          {/* ── SIGNUP — ASSOCIATION ───────────────────────────────────────── */}
          {isAssoSignup && (
            <>
              <RoleToggle value={activeRole} onChange={handleRoleChange} />
              <CtxHint variant="association" />

              <h2 className="font-display text-[18px] font-bold text-text mb-4">
                {t('signup.association.title')}
              </h2>

              <StepIndicator steps={assoStepLabels} currentStep={assoStep} />

              {/* Step 1 — Search */}
              {assoStep === 1 && (
                <AssoSearch
                  onSelect={(asso) => {
                    setSelectedAsso(asso);
                    setAssoStep(2);
                  }}
                />
              )}

              {/* Step 2 — Auth method */}
              {assoStep === 2 && (
                <div className="flex flex-col gap-4">
                  <p className="text-[13px] text-text-2">{t('signup.association.connect.title')}</p>

                  <GoogleButton
                    onSuccess={handleGoogleSignUpAsso}
                    label={t('signup.association.connect.google')}
                    loading={googleAuth.loading}
                  />

                  {googleAuth.error && (
                    <p className="text-[11.5px] text-red">
                      {t(googleAuth.error as Parameters<typeof t>[0])}
                    </p>
                  )}

                  <Divider />

                  <MagicLinkForm
                    onSubmit={(email) => magicLink.sendLink(email, 'ASSOCIATION')}
                    role="ASSOCIATION"
                  />

                  <Divider />

                  <EmailRegisterForm
                    onSubmit={handleEmailRegisterAsso}
                    loading={emailRegister.loading}
                    error={emailRegister.error ? t(emailRegister.error as Parameters<typeof t>[0]) : undefined}
                    submitLabel={t('signup.emailPassword.submitContinue')}
                  />

                  <button
                    type="button"
                    onClick={() => setAssoStep(1)}
                    className="text-[11.5px] text-muted bg-transparent border-none cursor-pointer py-1 transition-colors duration-200 hover:text-cyan text-left"
                  >
                    ← {t('signup.association.steps.search')}
                  </button>
                </div>
              )}

              {/* Step 3 — Profile */}
              {assoStep === 3 && selectedAsso && (
                <AssoProfileForm
                  asso={selectedAsso}
                  onSubmit={handleAssoProfileSubmit}
                  loading={assoProfileLoading}
                />
              )}
            </>
          )}

          {/* ── Error from magic link verify ─────────────────────────────── */}
          {verifyStatus === 'error' && verifyError && (
            <div
              className="mt-4 px-3 py-[10px] rounded-[8px] text-[12.5px] text-red bg-red/[.08] border border-red/25"
            >
              {t(verifyError as Parameters<typeof t>[0])}
            </div>
          )}

          {/* ── Legal footer ─────────────────────────────────────────────── */}
          <p className="text-center text-[10.5px] text-muted mt-5 leading-relaxed">
            {t('gdpr')}
          </p>
        </AuthCard>
      </div>
    </div>
  );
}
