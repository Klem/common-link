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

  const fieldClass =
    'w-full bg-bg-3 border border-border text-text px-3 py-[10px] rounded-[8px] font-body text-[13.5px] outline-none transition-[border-color] duration-200 placeholder:text-muted focus:border-green/40';
  const labelClass =
    'text-[11px] font-semibold text-text-2 uppercase tracking-[0.06em] block mb-[5px]';

  return (
    <form onSubmit={onFormSubmit} noValidate className="flex flex-col gap-[9px]">
      <div>
        <label htmlFor="email" className={labelClass}>
          {t('login.email.label')}
        </label>
        <input
          id="email"
          type="email"
          autoComplete="email"
          placeholder={t('login.email.placeholder')}
          className={fieldClass}
          {...register('email')}
        />
        {errors.email && (
          <p className="text-[11.5px] text-red mt-1">
            {t(errors.email.message as Parameters<typeof t>[0])}
          </p>
        )}
      </div>

      <div>
        <label htmlFor="password" className={labelClass}>
          {t('login.password.label')}
        </label>
        <input
          id="password"
          type="password"
          autoComplete="current-password"
          placeholder={t('login.password.placeholder')}
          className={fieldClass}
          {...register('password')}
        />
        {errors.password && (
          <p className="text-[11.5px] text-red mt-1">
            {t(errors.password.message as Parameters<typeof t>[0])}
          </p>
        )}
      </div>

      <div className="text-right -mt-[2px] mb-[2px]">
        <a
          href="#"
          onClick={(e) => e.preventDefault()}
          className="text-[11.5px] text-muted no-underline transition-colors duration-200 hover:text-cyan"
        >
          {t('login.forgotPassword')}
        </a>
      </div>

      {error && <p className="text-[11.5px] text-red">{error}</p>}

      <button
        type="submit"
        disabled={!isValid || loading}
        className="w-full py-[13px] bg-green text-black border-none rounded-md font-display text-[14px] font-bold cursor-pointer transition-all duration-200 tracking-[0.02em] hover:bg-[#00d4b0] hover:-translate-y-px hover:shadow-[0_6px_20px_rgba(0,184,154,.25)] disabled:opacity-[.38] disabled:cursor-not-allowed disabled:translate-y-0 disabled:shadow-none"
      >
        {loading ? '⏳' : t('login.submit')}
      </button>
    </form>
  );
}
