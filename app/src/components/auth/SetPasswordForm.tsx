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

  const fieldClass =
    'w-full bg-bg-3 border border-border text-text px-3 py-[10px] rounded-[8px] font-body text-[13.5px] outline-none transition-[border-color] duration-200 placeholder:text-muted focus:border-green/40';
  const labelClass =
    'text-[11px] font-semibold text-text-2 uppercase tracking-[0.06em] block mb-[5px]';

  return (
    <form onSubmit={onFormSubmit} noValidate className="flex flex-col gap-[9px]">
      <p className="text-[12.5px] text-text-2 leading-[1.65] mb-1">
        {t('setPassword.subtitle')}
      </p>

      <div>
        <label htmlFor="new-password" className={labelClass}>
          {t('setPassword.password.label')}
        </label>
        <input
          id="new-password"
          type="password"
          autoComplete="new-password"
          placeholder={t('setPassword.password.placeholder')}
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
        <label htmlFor="confirm-password" className={labelClass}>
          {t('setPassword.confirm.label')}
        </label>
        <input
          id="confirm-password"
          type="password"
          autoComplete="new-password"
          placeholder={t('setPassword.confirm.placeholder')}
          className={fieldClass}
          {...register('confirm')}
        />
        {errors.confirm && (
          <p className="text-[11.5px] text-red mt-1">
            {t(errors.confirm.message as Parameters<typeof t>[0])}
          </p>
        )}
      </div>

      <button
        type="submit"
        disabled={!isValid || loading}
        className="w-full py-[13px] bg-green text-black border-none rounded-md font-display text-[14px] font-bold cursor-pointer transition-all duration-200 tracking-[0.02em] hover:bg-[#00d4b0] hover:-translate-y-px hover:shadow-[0_6px_20px_rgba(0,184,154,.25)] disabled:opacity-[.38] disabled:cursor-not-allowed disabled:translate-y-0 disabled:shadow-none"
      >
        {loading ? '⏳' : t('setPassword.submit')}
      </button>

      <button
        type="button"
        onClick={onSkip}
        className="text-center text-[11.5px] text-muted bg-transparent border-none cursor-pointer py-1 transition-colors duration-200 hover:text-cyan"
      >
        {t('setPassword.skip')}
      </button>
    </form>
  );
}
