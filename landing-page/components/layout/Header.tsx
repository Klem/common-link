'use client';

import { useEffect, useState } from 'react';
import { useTranslations } from 'next-intl';
import { Link, usePathname } from '@/i18n/navigation';
import { APP_URL } from '@/lib/constants';

const LINK_STYLE = { padding: '12px 14px', borderRadius: '9999px', fontWeight: '600', fontSize: '15px', textDecoration: 'none' };
const LINK_ACTIVE = { ...LINK_STYLE, color: '#171744', background: '#E6E4F4' };
const LINK_IDLE = { ...LINK_STYLE, color: '#4A4A66' };

/**
 * Shared header, markup and inline styles taken from the mockup
 * (`commonlink-demo-nouveau-design.html`, `<header>` of every page). The current page's link is
 * highlighted (`aria-current="page"`), as on the mockup's main pages.
 *
 * Out of mockup: the `.nav-toggle` burger and the `.nav-menu` wrapper, which only take effect
 * below 900px (see the « mobile menu » block of globals.css). `.nav-menu` is `display:contents`
 * on desktop, so the `<nav>` and the action buttons stay direct flex items of the row: the
 * desktop rendering is exactly the mockup's.
 */
export function Header() {
  const t = useTranslations('nav');
  const pathname = usePathname();
  const [menuOpen, setMenuOpen] = useState(false);

  // A navigation does not unmount the header: without this the panel would stay open
  // over the destination page.
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
    <header className={menuOpen ? 'site-header open' : 'site-header'} style={{ background: '#FFFFFF', borderBottom: '1px solid #E6E4F4' }}>
      <div className="site-header-row" style={{ maxWidth: '1240px', margin: '0 auto', padding: '12px 48px', boxSizing: 'border-box', display: 'flex', alignItems: 'center', justifyContent: 'space-between', gap: '16px', flexWrap: 'wrap' }}>
        <Link href="/" aria-label={t('home')} style={{ fontFamily: "'Nunito Sans', sans-serif", fontWeight: '900', fontSize: '22px', color: '#171744', textDecoration: 'none' }}>
          {t.rich('logo', { accent: (chunks) => <span style={{ color: '#2FB3AA' }}>{chunks}</span> })}
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
          <nav className="site-nav" aria-label={t('main')} style={{ display: 'flex', gap: '4px', flexWrap: 'wrap' }}>
            {navLinks.map((link) => {
              const active = pathname === link.href;
              return (
                <Link key={link.href} href={link.href} aria-current={active ? 'page' : undefined} style={active ? LINK_ACTIVE : LINK_IDLE}>
                  {link.label}
                </Link>
              );
            })}
          </nav>
          <div className="site-actions" style={{ display: 'flex', gap: '10px', flexWrap: 'wrap' }}>
            <a href={APP_URL} style={{ display: 'inline-flex', alignItems: 'center', minHeight: '44px', padding: '0 18px', borderRadius: '9999px', border: '1.5px solid #32327D', color: '#32327D', fontWeight: '600', fontSize: '14px', textDecoration: 'none' }}>
              {t('login')}
            </a>
            <a href={APP_URL} style={{ display: 'inline-flex', alignItems: 'center', minHeight: '44px', padding: '0 18px', borderRadius: '9999px', background: '#4ECDC4', color: '#171744', fontWeight: '700', fontSize: '14px', textDecoration: 'none' }}>
              {t('donate')}
            </a>
          </div>
        </div>
      </div>
    </header>
  );
}
