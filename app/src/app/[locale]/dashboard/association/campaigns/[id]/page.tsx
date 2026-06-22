'use client';

import { useRef, useCallback, useState } from 'react';
import { useParams } from 'next/navigation';
import { useTranslations } from 'next-intl';
import { Topbar } from '@/components/dashboard';
import {
  CampaignHero,
  CampaignTabs,
  CampaignInfoTab,
  CampaignBudgetTab,
  CampaignMilestonesTab,
  CampaignPaymentsTab,
  CampaignDonorsTab,
  CampaignReportingTab,
  PrePublishModal,
} from '@/components/campaign';
import { useCampaign } from '@/hooks/campaign/useCampaign';
import { usePayments } from '@/hooks/campaign/usePayments';
import { useCampaignDonors } from '@/hooks/campaign/useCampaignDonors';
import { publishCampaign } from '@/lib/api/campaign';
import { useAccStatusStore } from '@/stores/accStatusStore';
import { useToastStore } from '@/stores/toastStore';
import { CampaignStatus } from '@/types/campaign';
import type { CampaignDto, UpdateCampaignRequest } from '@/types/campaign';

/**
 * Campaign editor page.
 *
 * Loads a single campaign by ID, then renders:
 * - A hero section (editable name, emoji, progress)
 * - A tab bar (Infos / Budget prév. / Milestones)
 * - The active tab content
 *
 * Name and emoji changes from the hero are applied optimistically
 * and debounced 800ms before being sent to the API.
 */
export default function CampaignEditorPage() {
  const t = useTranslations('dashboard.campaigns');
  const params = useParams();
  const campaignId = params.id as string;

  const { campaign, isLoading, error, isSaving, updateCampaignInfo, setCampaign, fetchCampaign } =
    useCampaign(campaignId);
  const { summary: paymentSummary } = usePayments(campaignId);
  const { donorsPage } = useCampaignDonors(campaignId);
  const { verified, bank: bankConnected } = useAccStatusStore();
  const { addToast } = useToastStore();

  const [activeTab, setActiveTab] = useState('info');
  const [showPublishModal, setShowPublishModal] = useState(false);

  /* Debounce ref for hero name/emoji saves */
  const debounceTimer = useRef<ReturnType<typeof setTimeout> | null>(null);

  /**
   * Applies a hero field change optimistically to local state,
   * then schedules a debounced API call (800 ms).
   */
  const scheduleHeroSave = useCallback(
    (patch: UpdateCampaignRequest) => {
      setCampaign((prev) => (prev ? { ...prev, ...patch } : prev));
      if (debounceTimer.current) clearTimeout(debounceTimer.current);
      debounceTimer.current = setTimeout(() => {
        updateCampaignInfo(patch);
      }, 800);
    },
    [setCampaign, updateCampaignInfo],
  );

  const handleNameChange = (name: string) => scheduleHeroSave({ name });
  const handleEmojiChange = (emoji: string) => scheduleHeroSave({ emoji });

  const handleSave = () => {
    if (debounceTimer.current) {
      clearTimeout(debounceTimer.current);
      debounceTimer.current = null;
    }
    if (campaign) updateCampaignInfo({ name: campaign.name, emoji: campaign.emoji });
  };

  const handleConfirmPublish = async () => {
    setShowPublishModal(false);
    try {
      const updated = await publishCampaign(campaignId);
      setCampaign(updated);
      addToast('success', 'campaigns.publish.toast.success');
    } catch {
      addToast('error', 'campaigns.publish.toast.error');
    }
  };

  /**
   * Called after the budget is saved successfully.
   * Updates the local campaign state with the returned DTO.
   */
  const handleBudgetSaved = useCallback((updated: CampaignDto) => {
    setCampaign(updated);
  }, [setCampaign]);

  /* Loading state */
  if (isLoading) {
    return (
      <div>
        <Topbar title={t('pageTitle')} />
        <div className="flex items-center justify-center p-[48px]">
          <div className="w-[32px] h-[32px] rounded-full border-2 border-[var(--color-green)]/30 border-t-[var(--color-green)] animate-spin" />
        </div>
      </div>
    );
  }

  /* Error / not found state */
  if (error || !campaign) {
    return (
      <div>
        <Topbar title={t('pageTitle')} />
        <div className="p-[48px] text-center text-[var(--color-text-2)]">
          {t('editor.notFound')}
        </div>
      </div>
    );
  }

  return (
    <div>
      <Topbar title={campaign.name} parent={t('campaigns.pageTitle')} />

      <div className="page">
        {/* — Barre d'actions — */}
        <div className="camp-actions-bar">
          <button className="cm-btn cm-btn-ghost cm-btn-sm" onClick={handleSave} disabled={isSaving}>
            💾 {t('editor.info.save')}
          </button>
          {campaign.status === CampaignStatus.DRAFT && (
            <button className="cm-btn cm-btn-primary cm-btn-sm" onClick={() => setShowPublishModal(true)}>
              🚀 {t('editor.publish')}
            </button>
          )}
        </div>

        <CampaignHero
          campaign={campaign}
          onNameChange={handleNameChange}
          onEmojiChange={handleEmojiChange}
          onTabChange={setActiveTab}
        />

        <CampaignTabs
          activeTab={activeTab}
          onTabChange={setActiveTab}
          milestoneCount={campaign.milestones.length}
          paymentCount={paymentSummary?.txTotal ? Number(paymentSummary.txTotal) : undefined}
          donorCount={donorsPage?.totalElements}
        />

        {/* Tab content */}
        {activeTab === 'info' && (
          <CampaignInfoTab
            campaign={campaign}
            onSave={updateCampaignInfo}
            isSaving={isSaving}
          />
        )}

        {activeTab === 'budget' && (
          <CampaignBudgetTab
            campaign={campaign}
            onBudgetSaved={handleBudgetSaved}
          />
        )}

        {activeTab === 'milestones' && (
          <CampaignMilestonesTab
            campaign={campaign}
            onMilestonesChanged={fetchCampaign}
          />
        )}

        {activeTab === 'payments' && (
          <CampaignPaymentsTab campaign={campaign} />
        )}

        {activeTab === 'donors' && (
          <CampaignDonorsTab campaign={campaign} />
        )}

        {activeTab === 'reporting' && (
          <CampaignReportingTab campaign={campaign} />
        )}
      </div>

      {showPublishModal && (
        <PrePublishModal
          campaign={campaign}
          verified={verified}
          bankConnected={bankConnected}
          onClose={() => setShowPublishModal(false)}
          onConfirm={handleConfirmPublish}
        />
      )}
    </div>
  );
}
