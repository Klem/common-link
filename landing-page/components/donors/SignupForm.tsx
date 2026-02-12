'use client';

import { useState } from 'react';
import { useTranslations } from 'next-intl';

export function SignupForm() {
  const t = useTranslations('donors.signup');
  const [submitted, setSubmitted] = useState(false);

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    setSubmitted(true);
  };

  if (submitted) {
    return (
      <div className="max-w-[480px] mx-auto bg-white border border-border rounded-xl p-10 shadow-lg text-center">
        <div className="text-5xl mb-6">✨</div>
        <h3 className="mb-4">{t('successTitle')}</h3>
        <p className="text-foreground-muted max-w-[340px] mx-auto mb-6">
          {t('successText')}
        </p>
        <p className="font-ui text-[0.9rem] text-foreground">
          {t('successLaunch')}
        </p>
      </div>
    );
  }

  return (
    <div className="max-w-[480px] mx-auto bg-white border border-border rounded-xl p-10 shadow-lg">
      <h3 className="text-center mb-8">{t('cardTitle')}</h3>

      {/* OAuth buttons */}
      <button
        type="button"
        className="w-full flex items-center justify-center gap-3 font-ui text-[0.9rem] font-medium bg-white border-2 border-border px-5 py-3.5 rounded-md cursor-pointer transition-all duration-200 hover:border-primary hover:shadow-sm mb-3"
      >
        <svg viewBox="0 0 24 24" width="20" height="20">
          <path d="M22.56 12.25c0-.78-.07-1.53-.2-2.25H12v4.26h5.92c-.26 1.37-1.04 2.53-2.21 3.31v2.77h3.57c2.08-1.92 3.28-4.74 3.28-8.09z" fill="#4285F4"/>
          <path d="M12 23c2.97 0 5.46-.98 7.28-2.66l-3.57-2.77c-.98.66-2.23 1.06-3.71 1.06-2.86 0-5.29-1.93-6.16-4.53H2.18v2.84C3.99 20.53 7.7 23 12 23z" fill="#34A853"/>
          <path d="M5.84 14.09c-.22-.66-.35-1.36-.35-2.09s.13-1.43.35-2.09V7.07H2.18C1.43 8.55 1 10.22 1 12s.43 3.45 1.18 4.93l2.85-2.22.81-.62z" fill="#FBBC05"/>
          <path d="M12 5.38c1.62 0 3.06.56 4.21 1.64l3.15-3.15C17.45 2.09 14.97 1 12 1 7.7 1 3.99 3.47 2.18 7.07l3.66 2.84c.87-2.6 3.3-4.53 6.16-4.53z" fill="#EA4335"/>
        </svg>
        {t('google')}
      </button>

      <button
        type="button"
        className="w-full flex items-center justify-center gap-3 font-ui text-[0.9rem] font-medium bg-white border-2 border-border px-5 py-3.5 rounded-md cursor-pointer transition-all duration-200 hover:border-primary hover:shadow-sm mb-6"
      >
        <svg viewBox="0 0 24 24" width="20" height="20" fill="currentColor">
          <path d="M17.05 20.28c-.98.95-2.05.8-3.08.35-1.09-.46-2.09-.48-3.24 0-1.44.62-2.2.44-3.06-.35C2.79 15.25 3.51 7.59 9.05 7.31c1.35.07 2.29.74 3.08.8 1.18-.24 2.31-.93 3.57-.84 1.51.12 2.65.72 3.4 1.8-3.12 1.87-2.38 5.98.48 7.13-.57 1.5-1.31 2.99-2.54 4.09l.01-.01zM12.03 7.25c-.15-2.23 1.66-4.07 3.74-4.25.29 2.58-2.34 4.5-3.74 4.25z"/>
        </svg>
        {t('apple')}
      </button>

      {/* Divider */}
      <div className="flex items-center gap-4 mb-6">
        <div className="flex-1 h-px bg-border" />
        <span className="font-ui text-[0.8rem] text-foreground-muted">{t('divider')}</span>
        <div className="flex-1 h-px bg-border" />
      </div>

      {/* Email form */}
      <form onSubmit={handleSubmit}>
        <div className="mb-4">
          <label className="block font-ui text-[0.85rem] font-medium text-foreground-dark mb-1.5">
            {t('firstName')}
          </label>
          <input
            type="text"
            required
            placeholder={t('firstNamePlaceholder')}
            className="w-full font-ui text-[0.9rem] bg-white border-2 border-border rounded-md px-4 py-3 text-foreground-dark outline-none transition-colors focus:border-secondary placeholder:text-foreground-muted"
          />
        </div>
        <div className="mb-4">
          <label className="block font-ui text-[0.85rem] font-medium text-foreground-dark mb-1.5">
            {t('email')}
          </label>
          <input
            type="email"
            required
            placeholder={t('emailPlaceholder')}
            className="w-full font-ui text-[0.9rem] bg-white border-2 border-border rounded-md px-4 py-3 text-foreground-dark outline-none transition-colors focus:border-secondary placeholder:text-foreground-muted"
          />
        </div>
        <div className="mb-6">
          <label className="block font-ui text-[0.85rem] font-medium text-foreground-dark mb-1.5">
            {t('password')}
          </label>
          <input
            type="password"
            required
            minLength={8}
            placeholder={t('passwordPlaceholder')}
            className="w-full font-ui text-[0.9rem] bg-white border-2 border-border rounded-md px-4 py-3 text-foreground-dark outline-none transition-colors focus:border-secondary placeholder:text-foreground-muted"
          />
        </div>
        <button
          type="submit"
          className="w-full font-ui text-[0.95rem] font-semibold bg-primary text-white border-none px-6 py-4 rounded-md cursor-pointer transition-colors duration-200 hover:bg-primary-light flex items-center justify-center"
        >
          {t('submit')}
        </button>
      </form>

      <p className="font-ui text-[0.78rem] text-foreground-muted text-center mt-6 leading-relaxed">
        {t('note')}
      </p>
    </div>
  );
}
