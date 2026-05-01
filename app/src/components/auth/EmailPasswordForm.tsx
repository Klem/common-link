'use client';

import { useTranslations } from 'next-intl';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { z } from 'zod';

const loginSchema = z.object({
  email: z.string().email('errors.emailInvalid'),
  password: z.string().min(8, 'errors.passwordTooShort'),
});

type LoginFormData = z.infer<typeof loginSchema>;

interface EmailPasswordFormProps {
  onSubmit: (email: string, password: string) => Promise<void> | void;
  loading?: boolean;
  error?: string;
}

export function EmailPasswordForm({ onSubmit, loading = false, error }: EmailPasswordFormProps) {
  const t = useTranslations('auth');

  const {
    register,
    handleSubmit,
    formState: { errors, isValid },
  } = useForm<LoginFormData>({
    resolver: zodResolver(loginSchema),
    mode: 'onChange',
  });

  const onFormSubmit = handleSubmit(({ email, password }) => onSubmit(email, password));

  return (
    <form onSubmit={onFormSubmit} noValidate className="flex flex-col">
      <div className="form-group">
        <label htmlFor="email" className="form-label">
          {t('login.email.label')}
        </label>
        <input
          id="email"
          type="email"
          autoComplete="email"
          placeholder={t('login.email.placeholder')}
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
        <label htmlFor="password" className="form-label">
          {t('login.password.label')}
        </label>
        <input
          id="password"
          type="password"
          autoComplete="current-password"
          placeholder={t('login.password.placeholder')}
          className="form-input"
          {...register('password')}
        />
        {errors.password && (
          <p className="form-error">
            {t(errors.password.message as Parameters<typeof t>[0])}
          </p>
        )}
      </div>

      <div className="text-right -mt-3 mb-3">
        <a
          href="#"
          onClick={(e) => e.preventDefault()}
          className="text-green hover:text-green-dim text-sm font-medium no-underline transition-colors duration-200"
        >
          {t('login.forgotPassword')}
        </a>
      </div>

      {error && <p className="form-error mb-3">{error}</p>}

      <button
        type="submit"
        disabled={!isValid || loading}
        className="btn btn-primary btn-md w-full"
      >
        {loading ? '⏳' : t('login.submit')}
      </button>
    </form>
  );
}
