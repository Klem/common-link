import { useTranslations } from 'next-intl';
import { Link } from '@/i18n/navigation';

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

export function LegalSubnav({ active }: { active: (typeof links)[number]['key'] }) {
  const t = useTranslations('footer.legal.links');
  const tc = useTranslations('common');

  return (
    <div className="bg-white border-b border-border sticky top-16 z-40">
      <div className="max-w-container mx-auto px-8 py-3">
        <div className="text-[0.75rem] text-foreground-muted mb-2">
          <Link href="/" className="hover:text-primary">{tc('home')}</Link> › {tc('legalInfo')}
        </div>
        <nav className="flex items-center gap-5 overflow-x-auto whitespace-nowrap [-ms-overflow-style:none] [scrollbar-width:none] [&::-webkit-scrollbar]:hidden">
          {links.map((link) => (
            <Link
              key={link.key}
              href={link.href}
              className={`text-[0.85rem] font-ui font-medium flex-shrink-0 transition-colors ${
                active === link.key
                  ? 'text-primary font-semibold'
                  : 'text-foreground-muted hover:text-primary'
              }`}
            >
              {t(link.key)}
            </Link>
          ))}
        </nav>
      </div>
    </div>
  );
}
