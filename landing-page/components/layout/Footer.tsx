import { useTranslations } from 'next-intl';
import { Link } from '@/i18n/navigation';

export function Footer() {
  const t = useTranslations('footer');
  const currentYear = new Date().getFullYear();

  return (
    <footer className="bg-primary-dark text-white/70 pt-16 pb-8">
      <div className="max-w-container mx-auto px-8">
        {/* Grid */}
        <div className="grid grid-cols-1 md:grid-cols-[2fr_1fr_1.5fr] gap-8 md:gap-12 pb-12 border-b border-white/10">
          {/* Brand */}
          <div>
            <strong className="block text-white font-ui text-[0.95rem] mb-4">
              {t('brand.name')}
            </strong>
            <p className="text-[0.9rem]">{t('brand.description')}</p>
          </div>

          {/* Navigation */}
          <div className="flex flex-col gap-2">
            <strong className="block text-white font-ui text-[0.95rem] mb-4">
              {t('nav.title')}
            </strong>
            <Link
              href="/partenaires"
              className="text-white/70 text-[0.9rem] hover:text-white transition-colors duration-200"
            >
              {t('nav.partners')}
            </Link>
            <Link
              href="/associations"
              className="text-white/70 text-[0.9rem] hover:text-white transition-colors duration-200"
            >
              {t('nav.associations')}
            </Link>
            <Link
              href="/donateurs"
              className="text-white/70 text-[0.9rem] hover:text-white transition-colors duration-200"
            >
              {t('nav.donors')}
            </Link>
          </div>

          {/* Legal */}
          <div>
            <strong className="block text-white font-ui text-[0.95rem] mb-4">
              {t('legal.title')}
            </strong>
            <p className="text-[0.85rem] leading-relaxed">{t('legal.text')}</p>
          </div>
        </div>

        {/* Bottom */}
        <div className="pt-6 text-[0.8rem] opacity-50 space-y-1">
          <p>{t('copyright', { year: currentYear })}</p>
          <p>
            {t.rich('luciole', {
              link: (chunks) => (
                <a
                  href="https://www.luciole-vision.com"
                  target="_blank"
                  rel="noopener noreferrer"
                  className="text-white/50 underline hover:text-white/70 transition-colors"
                >
                  {chunks}
                </a>
              ),
            })}
          </p>
        </div>
      </div>
    </footer>
  );
}
