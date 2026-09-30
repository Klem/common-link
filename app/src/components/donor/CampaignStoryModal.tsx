'use client';

import { useEffect, useRef } from 'react';
import { useTranslations } from 'next-intl';
import { useCampaignStory } from '@/hooks/dashboard/useCampaignStory';

interface Props {
  /** Id of the campaign to show the full story for, or null when the modal is closed. */
  campaignId: string | null;
  onClose: () => void;
}

/**
 * Modal "Récit complet" opened from an impact gallery card — the donor's own campaign report
 * already carries the published story in full (`useCampaignStory`), so this only renders it.
 *
 * Same focus/Escape handling as `DonationTraceabilityModal`/`ShareImpactModal`: initial focus on
 * the close button, Escape closes it, no Tab-trapping.
 */
export function CampaignStoryModal({ campaignId, onClose }: Props) {
  const t = useTranslations('dashboard.donor.impact.story');
  const { story, isLoading, error } = useCampaignStory(campaignId);
  const closeButtonRef = useRef<HTMLButtonElement>(null);

  useEffect(() => {
    if (campaignId === null) return;
    closeButtonRef.current?.focus();

    function handleKeyDown(e: KeyboardEvent) {
      if (e.key === 'Escape') onClose();
    }
    document.addEventListener('keydown', handleKeyDown);
    return () => document.removeEventListener('keydown', handleKeyDown);
  }, [campaignId, onClose]);

  if (campaignId === null) return null;

  return (
    <div className="modal-backdrop" onClick={onClose}>
      <div
        className="modal modal-scroll modal-wide"
        role="dialog"
        aria-modal="true"
        aria-label={t('title')}
        onClick={(e) => e.stopPropagation()}
      >
        <div className="modal-header">
          <h2 className="font-display font-bold text-text">{t('title')}</h2>
          <button ref={closeButtonRef} className="modal-close" onClick={onClose} aria-label={t('close')}>
            ×
          </button>
        </div>
        <div className="modal-body">
          {isLoading && (
            <p className="text-sm text-text-2" aria-live="polite">
              {t('loading')}
            </p>
          )}
          {error && (
            <p className="text-sm text-coral" role="alert">
              {t('error')}
            </p>
          )}
          {story && (
            // Sanitized server-side (StoryHtmlSanitizer) before ever being persisted -- safe to
            // render as-is. `useCampaignStory` reuses the donor's own campaign report, gated on
            // having funded this campaign (DonorReadScope) -- never a public page.
            <div className="rte-content text-sm leading-relaxed" dangerouslySetInnerHTML={{ __html: story.storyText }} />
          )}
        </div>
      </div>
    </div>
  );
}
