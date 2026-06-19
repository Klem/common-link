'use client';

import { useTranslations, useLocale } from 'next-intl';
import { useRouter } from 'next/navigation';
import { Topbar } from '@/components/dashboard';
import { ROUTES } from '@/lib/routes';

/**
 * Reporting landing page — instructs the user to open a campaign and navigate
 * to its "Suivi budgétaire" tab to see the budget variance report.
 */
export default function ReportingPage() {
  const t = useTranslations('dashboard');
  const locale = useLocale();
  const router = useRouter();

  return (
    <div>
      <Topbar title={t('nav.reporting')} />
      <div className="p-[24px]">
        <div className="card card-no-hover">
          <div className="card-b">
            <p className="text-[var(--color-text-2)] text-[14px]">
              {t('reporting.page.selectCampaign')}
            </p>
            <button
              type="button"
              className="btn btn-primary mt-[16px]"
              onClick={() => router.push(`/${locale}${ROUTES.ASSOCIATION_CAMPAIGNS}`)}
            >
              {t('reporting.page.openCampaign')}
            </button>
          </div>
        </div>
      </div>
    </div>
  );
}
