import type { MetadataRoute } from 'next';

const BASE_URL = 'https://www.common-link.org';

const routes: { fr: string; en: string; priority: number }[] = [
  { fr: '/', en: '/', priority: 1 },
  { fr: '/donateurs', en: '/donors', priority: 0.9 },
  { fr: '/associations', en: '/associations', priority: 0.9 },
  { fr: '/tarifs', en: '/tarifs', priority: 0.8 },
  { fr: '/transparence', en: '/transparence', priority: 0.7 },
  { fr: '/faq', en: '/faq', priority: 0.5 },
  { fr: '/contact', en: '/contact', priority: 0.4 },
  { fr: '/mentions-legales', en: '/mentions-legales', priority: 0.2 },
  { fr: '/conditions-generales-utilisation', en: '/conditions-generales-utilisation', priority: 0.2 },
  { fr: '/conditions-generales-utilisation-associations', en: '/conditions-generales-utilisation-associations', priority: 0.2 },
  { fr: '/contrat-type', en: '/contrat-type', priority: 0.2 },
  { fr: '/reclamations', en: '/reclamations', priority: 0.2 },
  { fr: '/politique-confidentialite', en: '/politique-confidentialite', priority: 0.2 },
  { fr: '/politique-cookies', en: '/politique-cookies', priority: 0.2 },
];

export default function sitemap(): MetadataRoute.Sitemap {
  return routes.map(({ fr, en, priority }) => ({
    url: `${BASE_URL}${fr}`,
    lastModified: new Date(),
    priority,
    alternates: {
      languages: {
        fr: `${BASE_URL}${fr}`,
        en: `${BASE_URL}/en${en === '/' ? '' : en}`,
      },
    },
  }));
}
