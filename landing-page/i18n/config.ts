export const locales = ['fr'] as const;
export const defaultLocale = 'fr' as const;
export type Locale = (typeof locales)[number];
