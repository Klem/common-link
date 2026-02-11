'use client';

import { useState } from 'react';
import { useTranslations } from 'next-intl';
import { FormInput, FormTextarea, FormSelect } from '@/components/ui/FormInput';
import { Button } from '@/components/ui/Button';

export function ContactForm() {
  const t = useTranslations('partners.contact');
  const [submitted, setSubmitted] = useState(false);

  const typeOptions = Array.from({ length: 5 }).map((_, i) => ({
    value: t(`typeOptions.${i}.value`),
    label: t(`typeOptions.${i}.label`),
  }));

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    setSubmitted(true);
  };

  return (
    <section className="py-24">
      <div className="max-w-container mx-auto px-8">
        <div className="max-w-[540px] mx-auto bg-white border border-border rounded-xl p-12 shadow-lg">
          <h3 className="text-center mb-8">{t('title')}</h3>

          {submitted ? (
            <div className="text-center py-8">
              <div className="w-14 h-14 mx-auto mb-4 bg-success/10 rounded-full flex items-center justify-center">
                <svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="var(--color-success)" strokeWidth="2">
                  <polyline points="20 6 9 17 4 12" />
                </svg>
              </div>
              <p className="font-ui text-foreground-dark font-semibold">
                {t('success')}
              </p>
            </div>
          ) : (
            <form onSubmit={handleSubmit}>
              <FormInput
                label={t('name')}
                name="name"
                placeholder={t('namePlaceholder')}
                required
              />
              <FormInput
                label={t('email')}
                name="email"
                type="email"
                placeholder={t('emailPlaceholder')}
                required
              />
              <FormInput
                label={t('org')}
                name="org"
                placeholder={t('orgPlaceholder')}
              />
              <FormSelect
                label={t('type')}
                name="type"
                options={typeOptions}
              />
              <FormTextarea
                label={t('message')}
                name="message"
                placeholder={t('messagePlaceholder')}
              />
              <Button
                type="submit"
                variant="primary"
                size="lg"
                className="w-full justify-center"
              >
                {t('submit')}
              </Button>
            </form>
          )}

          {!submitted && (
            <p className="font-ui text-[0.78rem] text-foreground-muted text-center mt-6 leading-relaxed">
              {t('note')}
            </p>
          )}
        </div>
      </div>
    </section>
  );
}
