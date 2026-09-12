import { useTranslations } from 'next-intl';
import { API_URL } from '@/lib/constants';
import type { PublicCampaign } from '@/lib/api/campaigns';

interface CampaignCardProps {
  campaign: PublicCampaign;
}

/** Formate un montant en euros sans centimes, comme la maquette (« 4 200 € »). */
function formatEur(amount: number): string {
  return new Intl.NumberFormat('fr-FR', {
    style: 'currency',
    currency: 'EUR',
    maximumFractionDigits: 0,
  }).format(amount);
}

/**
 * Carte d'une campagne en cours.
 *
 * Classes reprises du vocabulaire maquette déjà porté dans `globals.css`
 * (`.campaign-card`, `.campaign-img`, `.category-badge`, `.campaign-asso`, `.campaign-title`,
 * `.campaign-progress`, `.campaign-amounts`, `.campaign-footer`, `.progress-bar`). Zéro Tailwind.
 *
 * Images : `coverImage` et `associationLogo` sont des chemins de service qui répondent 404 quand
 * rien n'a été téléversé — on ne rend l'`<img>` que si le chemin est non nul, sinon on retombe sur
 * `.photo-placeholder` (emoji de campagne, initiale de l'association).
 */
export function CampaignCard({ campaign }: CampaignCardProps) {
  const t = useTranslations('landing.campaigns');

  const pct = campaign.goal > 0 ? Math.round((campaign.raised / campaign.goal) * 100) : 0;

  return (
    <article className="campaign-card">
      <div className="campaign-img">
        {campaign.coverImage ? (
          <img src={`${API_URL}${campaign.coverImage}`} alt="" />
        ) : (
          <div className="photo-placeholder">{campaign.campaignEmoji}</div>
        )}
        {campaign.campaignCategory && (
          <span className="badge badge-active category-badge">{campaign.campaignCategory}</span>
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
            {/* Largeur calculée : seul cas où un style inline est admis. */}
            <div className="progress-fill progress-teal" style={{ width: `${Math.min(pct, 100)}%` }} />
          </div>
        </div>

        <div className="campaign-footer">
          <span className="campaign-meta">
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
