import { getTranslations } from 'next-intl/server';

interface PreviewBannerProps {
  locale: string;
  /**
   * When false, the destination campaign is not published: the donation endpoint would refuse the
   * payment, so the banner says so and the form is rendered disabled.
   */
  donationsEnabled: boolean;
}

/**
 * Banner shown whenever the page is rendered behind a preview token.
 *
 * Displayed even when the campaign is published: an association must never mistake its preview for
 * the page a donor actually sees.
 */
export async function PreviewBanner({ locale, donationsEnabled }: PreviewBannerProps) {
  const t = await getTranslations({ locale, namespace: 'landing.preview' });

  return (
    <div className="lp-preview-banner" role="status">
      <strong>{t('label')}</strong> {t('hint')}
      {!donationsEnabled && (
        <span className="lp-preview-banner-warning">{t('donationsDisabled')}</span>
      )}
    </div>
  );
}
