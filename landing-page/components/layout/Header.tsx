'use client';

import { useState, useEffect } from 'react';
import { useTranslations } from 'next-intl';
import { Link, usePathname } from '@/i18n/navigation';
import { LanguageSwitcher } from '@/components/ui/LanguageSwitcher';
import { Button } from '@/components/ui/Button';
import { APP_URL } from '@/lib/constants';

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
    { href: '/donateurs' as const, label: t('donors') },
    { href: '/associations' as const, label: t('associations') },
    { href: '/tarifs' as const, label: t('tarifs') },
  ];

  const isActive = (href: string) => pathname === href;

  return (
    <header
      className={`sticky top-0 z-[100] bg-background/[0.92] backdrop-blur-[16px] border-b border-border-light transition-shadow duration-300 ${
        scrolled ? 'shadow-sm' : ''
      }`}
    >
      <div className="max-w-container mx-auto px-8 h-16 flex items-center justify-between gap-6">
        {/* Logo */}
        <Link
          href="/"
          className="flex items-center gap-1 no-underline text-primary font-ui font-extrabold text-[1.15rem] hover:text-primary flex-shrink-0"
          aria-label={t('home')}
        >
          <span>Common</span>
          <span className="italic-accent text-secondary">Link</span>
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
          <a
            href={APP_URL}
            className="font-ui text-[0.9rem] font-medium px-2 py-1 rounded-sm text-foreground hover:text-primary hover:bg-secondary-pale transition-colors duration-200"
          >
            {t('projects')}
          </a>
        </nav>

        <div className="hidden md:flex items-center gap-4 flex-shrink-0">
          <a
            href={APP_URL}
            className="font-ui text-[0.9rem] font-semibold text-primary hover:text-secondary transition-colors duration-200"
          >
            {t('login')}
          </a>
          <Button href={APP_URL} external size="sm">
            {t('donate')}
          </Button>
          <LanguageSwitcher />
        </div>

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
        <a href={APP_URL} className="font-ui text-base font-medium py-2 text-foreground hover:text-primary">
          {t('projects')}
        </a>
        <a href={APP_URL} className="font-ui text-base font-medium py-2 text-primary">
          {t('login')}
        </a>
        <div className="mt-3 flex items-center gap-4">
          <Button href={APP_URL} external size="sm">
            {t('donate')}
          </Button>
          <LanguageSwitcher />
        </div>
      </nav>
    </header>
  );
}
