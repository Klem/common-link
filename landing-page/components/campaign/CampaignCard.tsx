import { useTranslations } from 'next-intl';
import { API_URL } from '@/lib/constants';
import { coverImageUrl, type PublicCampaign } from '@/lib/api/campaigns';
import { CAUSE_EMOJI, isDisplayedCause } from '@/lib/causes';

interface CampaignCardProps {
  campaign: PublicCampaign;
}

/** Formats an amount in euros without cents, like the mockup ("4 200 €"). */
function formatEur(amount: number): string {
  return new Intl.NumberFormat('fr-FR', {
    style: 'currency',
    currency: 'EUR',
    maximumFractionDigits: 0,
  }).format(amount);
}

/**
 * Card of a live campaign.
 *
 * Classes taken from the mockup vocabulary already ported to `globals.css`
 * (`.campaign-card`, `.campaign-img`, `.category-badge`, `.campaign-asso`, `.campaign-title`,
 * `.campaign-progress`, `.campaign-amounts`, `.campaign-footer`, `.progress-bar`). No Tailwind.
 *
 * Cause badge: only a known cause other than `AUTRE` is shown (`isDisplayedCause`). The place of
 * action, when set, is shown in the footer meta (`📍 Vallauris (06)`), as in the mockup.
 *
 * Images: `coverImage` and `associationLogo` are serving paths that answer 404 when nothing was
 * uploaded — the `<img>` is only rendered for a non-null path, otherwise `.photo-placeholder`
 * (campaign emoji, association initial).
 */
export function CampaignCard({ campaign }: CampaignCardProps) {
  const t = useTranslations('landing.campaigns');
  const tCause = useTranslations('landing.causes');
  const cause = isDisplayedCause(campaign.campaignCategory) ? campaign.campaignCategory : null;

  const pct = campaign.goal > 0 ? Math.round((campaign.raised / campaign.goal) * 100) : 0;

  return (
    <article className="campaign-card">
      <div className="campaign-img">
        {campaign.coverImage ? (
          <img src={coverImageUrl(campaign.coverImage, campaign.campaignUpdatedAt)} alt="" />
        ) : (
          <div className="photo-placeholder">{campaign.campaignEmoji}</div>
        )}
        {cause && (
          <span className="badge badge-active category-badge">
            {CAUSE_EMOJI[cause]} {tCause(cause)}
          </span>
        )}
      </div>

      <div className="campaign-body">
        <div className="campaign-asso">
          {campaign.associationLogo ? (
            <img
              className="campaign-asso-logo"
              src={`${API_URL}${campaign.associationLogo}`}
              alt=""
            />
          ) : (
            <div className="photo-placeholder campaign-asso-logo" aria-hidden="true">
              {campaign.associationName.charAt(0).toUpperCase()}
            </div>
          )}
          <span>{campaign.associationName}</span>
        </div>

        <div className="campaign-title">{campaign.campaignName}</div>

        <div className="campaign-progress">
          <div className="campaign-amounts">
            <span className="campaign-amount-raised">{formatEur(campaign.raised)}</span>
            <span className="campaign-amount-goal">
              {campaign.goal > 0 ? `/ ${formatEur(campaign.goal)}` : ''}
            </span>
          </div>
          <div
            className="progress-bar"
            role="progressbar"
            aria-valuenow={Math.min(pct, 100)}
            aria-valuemin={0}
            aria-valuemax={100}
          >
            {/* Computed width: the only case where an inline style is allowed. */}
            <div className="progress-fill progress-teal" style={{ width: `${Math.min(pct, 100)}%` }} />
          </div>
        </div>

        <div className="campaign-footer">
          <span className="campaign-meta">
            {campaign.actionPlace && <>📍 {campaign.actionPlace.label} · </>}
            🎯 {t('milestones', { count: campaign.milestoneCount })}
          </span>
          <a
            className="btn btn-sm btn-primary"
            href={campaign.donationUrl}
            target="_blank"
            rel="noopener noreferrer"
          >
            {t('ctaDonate')}
          </a>
        </div>
      </div>
    </article>
  );
}
