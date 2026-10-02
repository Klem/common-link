'use client';

import { useEffect, useState } from 'react';
import { useTranslations } from 'next-intl';
import { Link, usePathname } from '@/i18n/navigation';
import { APP_URL } from '@/lib/constants';

/**
 * Barre de navigation partagée.
 * Markup et classes repris à l'identique de la maquette (`nav.main-nav`,
 * `CommonLink UI V2 Julian.html`). Ne pas remplacer par des classes Tailwind :
 * le CSS de la maquette est la source de vérité.
 *
 * Ajout hors maquette : le bouton `.nav-toggle` et le conteneur `.nav-menu`,
 * qui n'existent que sous 900px (la maquette ne prévoit pas de menu mobile).
 * `.nav-menu` est en `display:contents` en desktop, donc `.nav-links` et
 * `.nav-actions` y restent les items flex directs de `.main-nav` : le rendu
 * desktop est strictement celui de la maquette.
 */
export function Header() {
  const t = useTranslations('nav');
  const pathname = usePathname();
  const [menuOpen, setMenuOpen] = useState(false);

  // Une navigation ne démonte pas le header : sans cela le panneau resterait
  // ouvert par-dessus la page d'arrivée.
  useEffect(() => {
    setMenuOpen(false);
  }, [pathname]);

  const navLinks = [
    { href: '/' as const, label: t('accueil') },
    { href: '/donateurs' as const, label: t('donors') },
    { href: '/associations' as const, label: t('associations') },
    { href: '/projets' as const, label: t('projects') },
    { href: '/tarifs' as const, label: t('tarifs') },
  ];

  return (
    <nav
      className={menuOpen ? 'main-nav open' : 'main-nav'}
      id="global-nav"
      aria-label={t('main')}
    >
      <Link href="/" className="nav-logo" aria-label={t('home')}>
        <span className="cl-logo" />
        <span className="nav-logo-text">
          Common<em>Link</em>
        </span>
      </Link>
      <button
        type="button"
        className="nav-toggle"
        aria-label={menuOpen ? t('menuClose') : t('menuOpen')}
        aria-expanded={menuOpen}
        aria-controls="global-nav-menu"
        onClick={() => setMenuOpen((open) => !open)}
      >
        <span className="nav-toggle-bar" />
        <span className="nav-toggle-bar" />
        <span className="nav-toggle-bar" />
      </button>
      <div className="nav-menu" id="global-nav-menu">
        <div className="nav-links" id="global-nav-links">
          {navLinks.map((link) => (
            <Link
              key={link.href}
              href={link.href}
              className={pathname === link.href ? 'active' : undefined}
            >
              {link.label}
            </Link>
          ))}
        </div>
        <div className="nav-actions">
          <a className="btn btn-outline btn-sm nav-login" href={APP_URL}>
            {t('login')}
          </a>
          <a className="btn btn-primary btn-sm" href={APP_URL}>
            {t('donate')}
          </a>
        </div>
      </div>
    </nav>
  );
}
