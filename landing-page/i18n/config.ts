export const locales = ['fr', 'en'] as const;
export const defaultLocale = 'fr' as const;
export type Locale = (typeof locales)[number];

export const pathnames = {
  '/': '/',
  '/associations': {
    fr: '/associations',
    en: '/associations',
  },
  '/donateurs': {
    fr: '/donateurs',
    en: '/donors',
  },
} as const;
