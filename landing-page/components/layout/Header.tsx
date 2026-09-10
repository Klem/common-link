'use client';

import { useTranslations } from 'next-intl';
import { Link, usePathname } from '@/i18n/navigation';
import { APP_URL } from '@/lib/constants';

/**
 * Barre de navigation partagée.
 * Markup et classes repris à l'identique de la maquette (`nav.main-nav`,
 * `CommonLink UI V2 Julian.html`). Ne pas remplacer par des classes Tailwind :
 * le CSS de la maquette est la source de vérité.
 */
export function Header() {
  const t = useTranslations('nav');
  const pathname = usePathname();

  const navLinks = [
    { href: '/' as const, label: t('accueil') },
    { href: '/donateurs' as const, label: t('donors') },
    { href: '/associations' as const, label: t('associations') },
    { href: APP_URL, label: t('projects'), external: true },
    { href: '/tarifs' as const, label: t('tarifs') },
  ];

  return (
    <nav className="main-nav" id="global-nav">
      <Link href="/" className="nav-logo" aria-label={t('home')}>
        <span className="cl-logo" />
        <span className="nav-logo-text">
          Common<em>Link</em>
        </span>
      </Link>
      <div className="nav-links" id="global-nav-links">
        {navLinks.map((link) =>
          link.external ? (
            <a key={link.href} href={link.href}>
              {link.label}
            </a>
          ) : (
            <Link
              key={link.href}
              href={link.href}
              className={pathname === link.href ? 'active' : undefined}
            >
              {link.label}
            </Link>
          )
        )}
      </div>
      <div className="nav-actions">
        <a className="btn btn-outline btn-sm nav-login" href={APP_URL}>
          {t('login')}
        </a>
        <a className="btn btn-primary btn-sm" href={APP_URL}>
          {t('donate')}
        </a>
      </div>
    </nav>
  );
}
