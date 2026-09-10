import { useTranslations } from 'next-intl';
import { Link } from '@/i18n/navigation';

/**
 * Sous-navigation des pages réglementaires (fil d'ariane + pastilles).
 * Markup et classes repris à l'identique de la maquette (`div.legal-subnav`,
 * pages p6 et p13 à p20). Ne pas remplacer par des classes Tailwind :
 * le CSS de la maquette est la source de vérité.
 */
const links = [
  { href: '/mentions-legales', key: 'mentions' },
  { href: '/transparence', key: 'transparency' },
  { href: '/conditions-generales-utilisation', key: 'cguDonors' },
  { href: '/conditions-generales-utilisation-associations', key: 'cguAssociations' },
  { href: '/contrat-type', key: 'modelAgreement' },
  { href: '/reclamations', key: 'complaints' },
  { href: '/politique-confidentialite', key: 'privacy' },
  { href: '/politique-cookies', key: 'cookies' },
  { href: '/contact', key: 'contact' },
] as const;

export type LegalSubnavKey = (typeof links)[number]['key'];

export function LegalSubnav({ active }: { active: LegalSubnavKey }) {
  const t = useTranslations('legal.subnav');
  const tc = useTranslations('common');

  return (
    <div className="legal-subnav">
      <div className="max-w">
        <span className="legal-crumb">
          <Link href="/">{tc('home')}</Link> › {tc('legalInfo')}
        </span>
        <div className="legal-subnav-links">
          {links.map((link) => (
            <Link
              key={link.key}
              href={link.href}
              className={active === link.key ? 'active' : undefined}
            >
              {t(link.key)}
            </Link>
          ))}
        </div>
      </div>
    </div>
  );
}
