import { useTranslations } from 'next-intl';

export function DashboardMockup() {
  const t = useTranslations('donors.dashboard');

  return (
    <div className="max-w-[640px] mx-auto">
      <div className="font-ui text-[0.8rem] text-foreground-muted text-center mb-4 uppercase tracking-wider">
        {t('label')}
      </div>
      <div className="bg-white border border-border rounded-xl shadow-lg overflow-hidden">
        {/* Dashboard header */}
        <div className="p-6 flex items-center gap-4 border-b border-border-light">
          <div className="w-11 h-11 flex-shrink-0 bg-gradient-to-br from-primary to-secondary rounded-full flex items-center justify-center text-white font-ui font-bold text-[0.9rem]">
            CL
          </div>
          <div>
            <div className="font-ui font-bold text-foreground-dark text-[1rem]">
              {t('greeting')}
            </div>
            <div className="font-ui text-[0.8rem] text-foreground-muted">
              {t('subtitle')}
            </div>
          </div>
        </div>

        {/* Stats */}
        <div className="grid grid-cols-3 border-b border-border-light">
          {[0, 1, 2].map((i) => (
            <div
              key={i}
              className={`p-5 text-center ${i < 2 ? 'border-r border-border-light' : ''}`}
            >
              <div className="font-ui font-bold text-[1.25rem] text-primary">
                {t(`stats.${i}.value`)}
              </div>
              <div className="font-ui text-[0.75rem] text-foreground-muted mt-1">
                {t(`stats.${i}.label`)}
              </div>
            </div>
          ))}
        </div>

        {/* Activity */}
        <div className="p-6">
          <div className="font-ui font-semibold text-[0.85rem] text-foreground-dark mb-4">
            {t('activityTitle')}
          </div>
          <div className="space-y-3">
            {[0, 1, 2].map((i) => {
              const type = t(`activities.${i}.type`);
              return (
                <div
                  key={i}
                  className="flex items-center gap-3 py-2 px-3 rounded-md bg-background-alt"
                >
                  <span
                    className={`font-ui text-[0.7rem] font-semibold px-2 py-0.5 rounded-[10px] flex-shrink-0 ${
                      type === 'verified'
                        ? 'text-success bg-success/10'
                        : 'text-accent bg-accent/15'
                    }`}
                  >
                    {t(`activities.${i}.badge`)}
                  </span>
                  <span className="font-ui text-[0.85rem] text-foreground flex-1 truncate">
                    {t(`activities.${i}.text`)}
                  </span>
                  <span className="font-ui text-[0.85rem] font-semibold text-foreground-dark tabular-nums flex-shrink-0">
                    {t(`activities.${i}.amount`)}
                  </span>
                </div>
              );
            })}
          </div>
        </div>
      </div>
    </div>
  );
}
