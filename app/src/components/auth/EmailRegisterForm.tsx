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

  const fieldClass =
    'w-full bg-bg-3 border border-border text-text px-3 py-[10px] rounded-[8px] font-body text-[13.5px] outline-none transition-[border-color] duration-200 placeholder:text-muted focus:border-green/40';
  const labelClass =
    'text-[11px] font-semibold text-text-2 uppercase tracking-[0.06em] block mb-[5px]';

  return (
    <form onSubmit={onFormSubmit} noValidate className="flex flex-col gap-[9px]">
      <div>
        <label htmlFor="reg-email" className={labelClass}>
          {t('signup.emailPassword.email.label')}
        </label>
        <input
          id="reg-email"
          type="email"
          autoComplete="email"
          placeholder={t('signup.emailPassword.email.placeholder')}
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
        <label htmlFor="reg-password" className={labelClass}>
          {t('signup.emailPassword.password.label')}
        </label>
        <input
          id="reg-password"
          type="password"
          autoComplete="new-password"
          placeholder={t('signup.emailPassword.password.placeholder')}
          className={fieldClass}
          {...register('password')}
        />
        {errors.password && (
          <p className="text-[11.5px] text-red mt-1">
            {t(errors.password.message as Parameters<typeof t>[0])}
          </p>
        )}
      </div>

      <div>
        <label htmlFor="reg-confirm" className={labelClass}>
          {t('signup.emailPassword.confirm.label')}
        </label>
        <input
          id="reg-confirm"
          type="password"
          autoComplete="new-password"
          placeholder={t('signup.emailPassword.confirm.placeholder')}
          className={fieldClass}
          {...register('confirm')}
        />
        {errors.confirm && (
          <p className="text-[11.5px] text-red mt-1">
            {t(errors.confirm.message as Parameters<typeof t>[0])}
          </p>
        )}
      </div>

      {error && <p className="text-[11.5px] text-red">{error}</p>}

      <button
        type="submit"
        disabled={!isValid || loading}
        className="w-full py-[13px] bg-green text-black border-none rounded-md font-display text-[14px] font-bold cursor-pointer transition-all duration-200 tracking-[0.02em] hover:bg-[#00d4b0] hover:-translate-y-px hover:shadow-[0_6px_20px_rgba(0,184,154,.25)] disabled:opacity-[.38] disabled:cursor-not-allowed disabled:translate-y-0 disabled:shadow-none"
      >
        {loading ? '⏳' : submitLabel}
      </button>
    </form>
  );
}
