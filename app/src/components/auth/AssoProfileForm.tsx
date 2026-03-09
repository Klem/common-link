'use client';

import { useTranslations } from 'next-intl';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { z } from 'zod';
import type { AssoResult } from './AssoSearch';

const profileSchema = z.object({
  contact: z.string().min(2, 'errors.emailRequired'),
  description: z.string().optional(),
});

type ProfileFormData = z.infer<typeof profileSchema>;

export interface AssoProfileData {
  contact: string;
  description?: string;
}

interface AssoProfileFormProps {
  asso: AssoResult;
  onSubmit: (data: AssoProfileData) => Promise<void> | void;
  loading?: boolean;
}

export function AssoProfileForm({ asso, onSubmit, loading = false }: AssoProfileFormProps) {
  const t = useTranslations('auth');

  const {
    register,
    handleSubmit,
    formState: { errors, isValid },
  } = useForm<ProfileFormData>({
    resolver: zodResolver(profileSchema),
    mode: 'onChange',
  });

  const onFormSubmit = handleSubmit((data) => onSubmit(data));

  const preFilled =
    'w-full bg-bg-3/60 border text-text-2 px-3 py-[10px] rounded-[8px] font-body text-[13.5px] outline-none cursor-default';
  const fieldClass =
    'w-full bg-bg-3 border border-border text-text px-3 py-[10px] rounded-[8px] font-body text-[13.5px] outline-none transition-[border-color] duration-200 placeholder:text-muted focus:border-green/40';
  const labelClass =
    'text-[11px] font-semibold text-text-2 uppercase tracking-[0.06em] block mb-[5px]';

  return (
    <form onSubmit={onFormSubmit} noValidate className="flex flex-col gap-[11px]">
      {/* Pre-fill notice */}
      <div
        className="flex items-center gap-[7px] px-3 py-2 rounded-[7px] text-[12px] text-text-2 mb-1"
        style={{ background: 'rgba(0,184,154,.06)', border: '1px solid rgba(0,184,154,.15)' }}
      >
        <span className="text-green text-[14px]">✓</span>
        Informations pré-remplies depuis le répertoire officiel — modifiables après inscription.
      </div>

      <div className="grid grid-cols-2 gap-[9px]">
        <div>
          <label className={labelClass}>{t('signup.association.profile.name.label')}</label>
          <input
            type="text"
            value={asso.nom}
            disabled
            className={preFilled}
            style={{ borderColor: 'rgba(0,184,154,.25)', background: 'rgba(0,184,154,.04)' }}
          />
        </div>
        <div>
          <label className={labelClass}>{t('signup.association.profile.siren.label')}</label>
          <input
            type="text"
            value={asso.siren}
            disabled
            className={preFilled}
            style={{ borderColor: 'rgba(0,184,154,.25)', background: 'rgba(0,184,154,.04)' }}
          />
        </div>
      </div>

      <div className="grid grid-cols-2 gap-[9px]">
        <div>
          <label className={labelClass}>Ville</label>
          <input
            type="text"
            value={asso.ville}
            disabled
            className={preFilled}
            style={{ borderColor: 'rgba(0,184,154,.25)', background: 'rgba(0,184,154,.04)' }}
          />
        </div>
        <div>
          <label className={labelClass}>Code postal</label>
          <input
            type="text"
            value={asso.codePostal}
            disabled
            className={preFilled}
            style={{ borderColor: 'rgba(0,184,154,.25)', background: 'rgba(0,184,154,.04)' }}
          />
        </div>
      </div>

      <div>
        <label htmlFor="contact" className={labelClass}>
          {t('signup.association.profile.contact.label')}{' '}
          <span className="text-red normal-case tracking-normal">*</span>
        </label>
        <input
          id="contact"
          type="text"
          placeholder={t('signup.association.profile.contact.placeholder')}
          className={fieldClass}
          {...register('contact')}
        />
        {errors.contact && (
          <p className="text-[11.5px] text-red mt-1">{errors.contact.message}</p>
        )}
      </div>

      <div>
        <label htmlFor="description" className={labelClass}>
          {t('signup.association.profile.description.label')}{' '}
          <span className="text-muted normal-case tracking-normal font-normal">
            (optionnel)
          </span>
        </label>
        <input
          id="description"
          type="text"
          placeholder={t('signup.association.profile.description.placeholder')}
          className={fieldClass}
          {...register('description')}
        />
      </div>

      <button
        type="submit"
        disabled={!isValid || loading}
        className="w-full py-[13px] bg-green text-black border-none rounded-md font-display text-[14px] font-bold cursor-pointer transition-all duration-200 tracking-[0.02em] hover:bg-[#00d4b0] hover:-translate-y-px hover:shadow-[0_6px_20px_rgba(0,184,154,.25)] disabled:opacity-[.38] disabled:cursor-not-allowed disabled:translate-y-0 disabled:shadow-none"
      >
        {loading ? '⏳' : t('signup.association.profile.submit')}
      </button>

      <p className="text-center text-[11.5px] text-muted">
        Aucun engagement. Tout complétable après inscription.
      </p>
    </form>
  );
}
