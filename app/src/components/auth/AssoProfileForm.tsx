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

  return (
    <form onSubmit={onFormSubmit} noValidate className="flex flex-col">
      <div className="flex items-center gap-[7px] px-3 py-2 rounded-[7px] text-[12px] text-text-2 mb-3 bg-green/[.06] border border-green/[.15]">
        <span className="text-green text-[14px]">✓</span>
        Informations pré-remplies depuis le répertoire officiel — modifiables après inscription.
      </div>

      <div className="grid grid-cols-2 gap-3">
        <div className="form-group">
          <label className="form-label">{t('signup.association.profile.name.label')}</label>
          <input type="text" value={asso.nom} disabled className="form-input" />
        </div>
        <div className="form-group">
          <label className="form-label">{t('signup.association.profile.siren.label')}</label>
          <input type="text" value={asso.siren} disabled className="form-input" />
        </div>
      </div>

      <div className="grid grid-cols-2 gap-3">
        <div className="form-group">
          <label className="form-label">Ville</label>
          <input type="text" value={asso.ville} disabled className="form-input" />
        </div>
        <div className="form-group">
          <label className="form-label">Code postal</label>
          <input type="text" value={asso.codePostal} disabled className="form-input" />
        </div>
      </div>

      <div className="form-group">
        <label htmlFor="contact" className="form-label">
          {t('signup.association.profile.contact.label')}{' '}
          <span className="required">*</span>
        </label>
        <input
          id="contact"
          type="text"
          placeholder={t('signup.association.profile.contact.placeholder')}
          className="form-input"
          {...register('contact')}
        />
        {errors.contact && (
          <p className="form-error">{errors.contact.message}</p>
        )}
      </div>

      <div className="form-group">
        <label htmlFor="description" className="form-label">
          {t('signup.association.profile.description.label')}{' '}
          <span className="text-text-2 font-normal normal-case tracking-normal">(optionnel)</span>
        </label>
        <textarea
          id="description"
          placeholder={t('signup.association.profile.description.placeholder')}
          className="form-input"
          {...register('description')}
        />
      </div>

      <button
        type="submit"
        disabled={!isValid || loading}
        className="btn btn-primary btn-md w-full"
      >
        {loading ? '⏳' : t('signup.association.profile.submit')}
      </button>

      <p className="text-center text-xs text-text-2 mt-3">
        Aucun engagement. Tout complétable après inscription.
      </p>
    </form>
  );
}
