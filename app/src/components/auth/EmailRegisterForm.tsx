'use client';

import { useTranslations } from 'next-intl';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { z } from 'zod';

const registerSchema = z
  .object({
    email: z.string().email('errors.emailInvalid'),
    password: z.string().min(8, 'errors.passwordTooShort'),
    confirm: z.string(),
  })
  .refine((data) => data.password === data.confirm, {
    message: 'errors.passwordMismatch',
    path: ['confirm'],
  });

type RegisterFormData = z.infer<typeof registerSchema>;

interface EmailRegisterFormProps {
  onSubmit: (email: string, password: string) => Promise<void> | void;
  loading?: boolean;
  error?: string;
  submitLabel: string;
}

export function EmailRegisterForm({
  onSubmit,
  loading = false,
  error,
  submitLabel,
}: EmailRegisterFormProps) {
  const t = useTranslations('auth');

  const {
    register,
    handleSubmit,
    formState: { errors, isValid },
  } = useForm<RegisterFormData>({
    resolver: zodResolver(registerSchema),
    mode: 'onChange',
  });

  const onFormSubmit = handleSubmit(({ email, password }) => onSubmit(email, password));

  return (
    <form onSubmit={onFormSubmit} noValidate className="flex flex-col">
      <div className="form-group">
        <label htmlFor="reg-email" className="form-label">
          {t('signup.emailPassword.email.label')}
        </label>
        <input
          id="reg-email"
          type="email"
          autoComplete="email"
          placeholder={t('signup.emailPassword.email.placeholder')}
          className="form-input"
          {...register('email')}
        />
        {errors.email && (
          <p className="form-error">
            {t(errors.email.message as Parameters<typeof t>[0])}
          </p>
        )}
      </div>

      <div className="form-group">
        <label htmlFor="reg-password" className="form-label">
          {t('signup.emailPassword.password.label')}
        </label>
        <input
          id="reg-password"
          type="password"
          autoComplete="new-password"
          placeholder={t('signup.emailPassword.password.placeholder')}
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
        <label htmlFor="reg-confirm" className="form-label">
          {t('signup.emailPassword.confirm.label')}
        </label>
        <input
          id="reg-confirm"
          type="password"
          autoComplete="new-password"
          placeholder={t('signup.emailPassword.confirm.placeholder')}
          className="form-input"
          {...register('confirm')}
        />
        {errors.confirm && (
          <p className="form-error">
            {t(errors.confirm.message as Parameters<typeof t>[0])}
          </p>
        )}
      </div>

      {error && <p className="form-error mb-3">{error}</p>}

      <button
        type="submit"
        disabled={!isValid || loading}
        className="btn btn-primary btn-md w-full"
      >
        {loading ? '⏳' : submitLabel}
      </button>
    </form>
  );
}
