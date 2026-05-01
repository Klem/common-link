'use client';

import { useTranslations } from 'next-intl';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { z } from 'zod';

const setPasswordSchema = z
  .object({
    password: z.string().min(8, 'errors.passwordTooShort'),
    confirm: z.string(),
  })
  .refine((data) => data.password === data.confirm, {
    message: 'errors.passwordMismatch',
    path: ['confirm'],
  });

type SetPasswordFormData = z.infer<typeof setPasswordSchema>;

interface SetPasswordFormProps {
  onSubmit: (password: string) => Promise<void> | void;
  onSkip: () => void;
  loading?: boolean;
}

export function SetPasswordForm({ onSubmit, onSkip, loading = false }: SetPasswordFormProps) {
  const t = useTranslations('auth');

  const {
    register,
    handleSubmit,
    formState: { errors, isValid },
  } = useForm<SetPasswordFormData>({
    resolver: zodResolver(setPasswordSchema),
    mode: 'onChange',
  });

  const onFormSubmit = handleSubmit(({ password }) => onSubmit(password));

  return (
    <form onSubmit={onFormSubmit} noValidate className="flex flex-col">
      <p className="text-xs text-text-2 leading-relaxed mb-3">
        {t('setPassword.subtitle')}
      </p>

      <div className="form-group">
        <label htmlFor="new-password" className="form-label">
          {t('setPassword.password.label')}
        </label>
        <input
          id="new-password"
          type="password"
          autoComplete="new-password"
          placeholder={t('setPassword.password.placeholder')}
          className="form-input"
          {...register('password')}
        />
        {errors.password && (
          <p className="form-error">
            {t(errors.password.message as Parameters<typeof t>[0])}
          </p>
        )}
      </div>

      <div className="form-group">
        <label htmlFor="confirm-password" className="form-label">
          {t('setPassword.confirm.label')}
        </label>
        <input
          id="confirm-password"
          type="password"
          autoComplete="new-password"
          placeholder={t('setPassword.confirm.placeholder')}
          className="form-input"
          {...register('confirm')}
        />
        {errors.confirm && (
          <p className="form-error">
            {t(errors.confirm.message as Parameters<typeof t>[0])}
          </p>
        )}
      </div>

      <button
        type="submit"
        disabled={!isValid || loading}
        className="btn btn-primary btn-md w-full"
      >
        {loading ? '⏳' : t('setPassword.submit')}
      </button>

      <button
        type="button"
        onClick={onSkip}
        className="btn btn-ghost btn-md w-full mt-2"
      >
        {t('setPassword.skip')}
      </button>
    </form>
  );
}
