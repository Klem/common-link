'use client';

import { useState, useEffect } from 'react';
import { useTranslations } from 'next-intl';
import { Link, usePathname } from '@/i18n/navigation';
import { LanguageSwitcher } from '@/components/ui/LanguageSwitcher';

export function Header() {
  const t = useTranslations('nav');
  const pathname = usePathname();
  const [scrolled, setScrolled] = useState(false);
  const [mobileOpen, setMobileOpen] = useState(false);

  useEffect(() => {
    const onScroll = () => setScrolled(window.scrollY > 10);
    window.addEventListener('scroll', onScroll, { passive: true });
    return () => window.removeEventListener('scroll', onScroll);
  }, []);

  const navLinks = [
    { href: '/partenaires' as const, label: t('partners') },
    { href: '/associations' as const, label: t('associations') },
    { href: '/donateurs' as const, label: t('donors') },
  ];

  const isActive = (href: string) => pathname === href;

  return (
    <header
      className={`sticky top-0 z-[100] bg-background/[0.92] backdrop-blur-[16px] border-b border-border-light transition-shadow duration-300 ${
        scrolled ? 'shadow-sm' : ''
      }`}
    >
      <div className="max-w-container mx-auto px-8 h-16 flex items-center justify-between">
        {/* Logo */}
        <Link
          href="/"
          className="flex items-center gap-2 no-underline text-primary font-ui font-bold text-[1.15rem] hover:text-primary"
          aria-label={t('home')}
        >
          <svg
            className="flex-shrink-0"
            viewBox="0 0 40 40"
            width="36"
            height="36"
            aria-hidden="true"
          >
            <path
              d="M12 8C7.6 8 4 11.6 4 16s3.6 8 8 8h2v-4h-2c-2.2 0-4-1.8-4-4s1.8-4 4-4h6c2.2 0 4 1.8 4 4v1h4v-1c0-4.4-3.6-8-8-8h-6z"
              fill="var(--color-primary)"
            />
            <path
              d="M28 32c4.4 0 8-3.6 8-8s-3.6-8-8-8h-2v4h2c2.2 0 4 1.8 4 4s-1.8 4-4 4h-6c-2.2 0-4-1.8-4-4v-1h-4v1c0 4.4 3.6 8 8 8h6z"
              fill="var(--color-secondary)"
            />
          </svg>
          <span>Lien commun</span>
        </Link>

        {/* Desktop nav */}
        <nav
          className="hidden md:flex items-center gap-6"
          aria-label={t('main')}
        >
          {navLinks.map((link) => (
            <Link
              key={link.href}
              href={link.href}
              className={`font-ui text-[0.9rem] font-medium px-2 py-1 rounded-sm transition-colors duration-200 relative ${
                isActive(link.href)
                  ? 'text-primary font-semibold after:content-[""] after:absolute after:bottom-[-4px] after:left-1/2 after:-translate-x-1/2 after:w-5 after:h-0.5 after:bg-accent after:rounded-sm'
                  : 'text-foreground hover:text-primary hover:bg-secondary-pale'
              }`}
            >
              {link.label}
            </Link>
          ))}
          <LanguageSwitcher />
        </nav>

        {/* Mobile menu button */}
        <button
          className="flex md:hidden flex-col gap-[5px] bg-transparent border-none cursor-pointer p-2"
          onClick={() => setMobileOpen(!mobileOpen)}
          aria-label={mobileOpen ? t('menuClose') : t('menuOpen')}
          aria-expanded={mobileOpen}
        >
          <span
            className={`block w-[22px] h-0.5 bg-primary rounded-sm transition-all duration-300 ${
              mobileOpen ? 'rotate-45 translate-x-[5px] translate-y-[5px]' : ''
            }`}
          />
          <span
            className={`block w-[22px] h-0.5 bg-primary rounded-sm transition-all duration-300 ${
              mobileOpen ? 'opacity-0' : ''
            }`}
          />
          <span
            className={`block w-[22px] h-0.5 bg-primary rounded-sm transition-all duration-300 ${
              mobileOpen
                ? '-rotate-45 translate-x-[5px] -translate-y-[5px]'
                : ''
            }`}
          />
        </button>
      </div>

      {/* Mobile nav */}
      <nav
        className={`${
          mobileOpen ? 'flex' : 'hidden'
        } md:hidden flex-col px-8 py-4 pb-6 border-t border-border-light`}
        aria-label={t('mobile')}
      >
        {navLinks.map((link) => (
          <Link
            key={link.href}
            href={link.href}
            onClick={() => setMobileOpen(false)}
            className={`font-ui text-base font-medium py-2 transition-colors duration-200 ${
              isActive(link.href)
                ? 'text-primary font-semibold'
                : 'text-foreground hover:text-primary'
            }`}
          >
            {link.label}
          </Link>
        ))}
        <div className="mt-3">
          <LanguageSwitcher />
        </div>
      </nav>
    </header>
  );
}
