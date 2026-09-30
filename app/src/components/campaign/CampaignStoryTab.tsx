'use client';

import { useEffect, useState } from 'react';
import { useTranslations } from 'next-intl';
import { useDebouncedPatchSave } from '@/hooks/campaign/useDebouncedSave';
import { getCampaignStory, upsertCampaignStory } from '@/lib/api/campaign';
import { useToastStore } from '@/stores/toastStore';
import type { UpsertCampaignStoryRequest } from '@/types/campaign';

interface Props {
  campaignId: string;
}

/**
 * Association-side editor for a campaign's impact story (D5, option C).
 *
 * Autosave (debounced, like `CampaignInfoTab`) never publishes -- it always sends
 * `publish: false`, which the backend treats as one-directional (never unpublishes an
 * already-live story). Publishing is a separate, explicit action: a draft must never go public
 * from a debounce tick.
 */
export function CampaignStoryTab({ campaignId }: Props) {
  const t = useTranslations('dashboard.campaigns');
  const { addToast } = useToastStore();
  const [storyText, setStoryText] = useState('');
  const [publishedAt, setPublishedAt] = useState<string | null>(null);
  const [isLoading, setIsLoading] = useState(true);
  const [loadError, setLoadError] = useState(false);
  const [isPublishing, setIsPublishing] = useState(false);

  useEffect(() => {
    let cancelled = false;
    getCampaignStory(campaignId)
      .then((story) => {
        if (cancelled) return;
        setStoryText(story?.storyText ?? '');
        setPublishedAt(story?.publishedAt ?? null);
      })
      .catch(() => {
        // A failed load must never fall through to an empty, editable textarea: the first
        // autosave tick would overwrite a published story that simply failed to load, and there
        // is no version history to recover it from.
        if (!cancelled) setLoadError(true);
      })
      .finally(() => {
        if (!cancelled) setIsLoading(false);
      });
    return () => {
      cancelled = true;
    };
  }, [campaignId]);

  const { schedule, cancel } = useDebouncedPatchSave<UpsertCampaignStoryRequest>(async (patch) => {
    if (!patch.storyText) return;
    await upsertCampaignStory(campaignId, { storyText: patch.storyText, publish: false });
  });

  function handleChange(value: string): void {
    setStoryText(value);
    schedule({ storyText: value });
  }

  async function handlePublish(): Promise<void> {
    // The publish call already carries the current `storyText` -- cancel the pending autosave
    // instead of flushing it, so the two requests never race each other. On a first publish
    // (no row yet) a race would double-INSERT and hit the campaign's unique constraint.
    cancel();
    setIsPublishing(true);
    try {
      const saved = await upsertCampaignStory(campaignId, { storyText, publish: true });
      setPublishedAt(saved.publishedAt);
      addToast('success', 'campaignStoryPublished');
    } finally {
      setIsPublishing(false);
    }
  }

  if (isLoading) {
    return <p className="text-sm text-text-2">{t('editor.story.loading')}</p>;
  }

  if (loadError) {
    return <p className="text-sm text-coral">{t('editor.story.loadError')}</p>;
  }

  return (
    <div className="set-tab-content active">
      <div className="card no-hover">
        <div className="card-h">
          <h3>{t('editor.story.title')}</h3>
          <span className={`badge ${publishedAt ? 'badge-active' : 'badge-draft'}`}>
            {publishedAt ? t('editor.story.published') : t('editor.story.draft')}
          </span>
        </div>
        <div className="card-b">
          <div className="fg">
            <label htmlFor="storyText" className="fl">
              {t('editor.story.label')}
            </label>
            <textarea
              id="storyText"
              className="fi"
              rows={8}
              placeholder={t('editor.story.placeholder')}
              value={storyText}
              onChange={(e) => handleChange(e.target.value)}
            />
          </div>
          <div className="frow-actions">
            <button
              type="button"
              className="cm-btn cm-btn-primary cm-btn-sm"
              onClick={handlePublish}
              disabled={isPublishing || !storyText.trim()}
            >
              {publishedAt ? t('editor.story.republish') : t('editor.story.publish')}
            </button>
          </div>
        </div>
      </div>
    </div>
  );
}
