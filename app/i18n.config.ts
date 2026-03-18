const i18nConfig = {
  defaultLocale: 'en' as const,
  locales: ['fr', 'en'] as const,
};

export type Locale = (typeof i18nConfig.locales)[number];
export default i18nConfig;
