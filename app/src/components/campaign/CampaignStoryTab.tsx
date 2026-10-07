'use client';

import { useEffect, useRef, useState } from 'react';
import { useTranslations } from 'next-intl';
import { RichTextEditor } from '@/components/ui/RichTextEditor';
import { useDebouncedCallback } from '@/hooks/campaign/useDebouncedSave';
import { apiUrl } from '@/lib/api';
import { getCampaignStory, upsertCampaignStory, uploadStoryImage } from '@/lib/api/campaign';
import { useToastStore } from '@/stores/toastStore';

interface Props {
  campaignId: string;
}

const SUMMARY_MAX_LENGTH = 220;

/**
 * Association-side editor for a campaign's impact story (D5, option C): a rich-text narrative
 * (`storyText`, sanitized server-side) plus a short plain-text summary (`storySummary`) -- the
 * only text the donor-facing gallery and share card ever render, never the full HTML.
 *
 * The endpoint is a full upsert (both fields required on every call), unlike `CampaignInfoTab`'s
 * true partial PATCH -- autosave therefore always sends the current value of both fields from
 * refs, not just the field that just changed.
 *
 * Autosave never publishes -- it always sends `publish: false`, which the backend treats as
 * one-directional (never unpublishes an already-live story). Publishing is a separate, explicit
 * action: a draft must never go public from a debounce tick.
 */
export function CampaignStoryTab({ campaignId }: Props) {
  const t = useTranslations('dashboard.campaigns');
  const { addToast } = useToastStore();
  const [storyText, setStoryText] = useState('');
  const [storySummary, setStorySummary] = useState('');
  const [publishedAt, setPublishedAt] = useState<string | null>(null);
  const [isLoading, setIsLoading] = useState(true);
  const [loadError, setLoadError] = useState(false);
  const [isPublishing, setIsPublishing] = useState(false);

  const storyTextRef = useRef(storyText);
  storyTextRef.current = storyText;
  const storySummaryRef = useRef(storySummary);
  storySummaryRef.current = storySummary;

  useEffect(() => {
    let cancelled = false;
    getCampaignStory(campaignId)
      .then((story) => {
        if (cancelled) return;
        setStoryText(story?.storyText ?? '');
        setStorySummary(story?.storySummary ?? '');
        setPublishedAt(story?.publishedAt ?? null);
      })
      .catch(() => {
        // A failed load must never fall through to an empty, editable form: the first autosave
        // tick would overwrite a published story that simply failed to load, and there is no
        // version history to recover it from.
        if (!cancelled) setLoadError(true);
      })
      .finally(() => {
        if (!cancelled) setIsLoading(false);
      });
    return () => {
      cancelled = true;
    };
  }, [campaignId]);

  const { schedule, cancel } = useDebouncedCallback(() => {
    if (!storyTextRef.current.trim() || !storySummaryRef.current.trim()) return;
    upsertCampaignStory(campaignId, {
      storyText: storyTextRef.current,
      storySummary: storySummaryRef.current,
      publish: false,
    });
  });

  function handleStoryTextChange(html: string): void {
    setStoryText(html);
    schedule();
  }

  function handleSummaryChange(value: string): void {
    setStorySummary(value.slice(0, SUMMARY_MAX_LENGTH));
    schedule();
  }

  async function handleImageUpload(file: File): Promise<string> {
    try {
      const { url } = await uploadStoryImage(campaignId, file);
      // `url` is server-relative (`/api/public/campaigns/...`) -- it gets embedded verbatim in
      // storyText's HTML, which is later rendered as-is on the donor's bilan page. A relative
      // <img src> there resolves against the frontend origin, not the API, and 404s (same
      // resolution `campaignCoverUrl` already guards against for the campaign cover image).
      return apiUrl(url);
    } catch {
      addToast('error', 'campaignStoryImageUploadFailed');
      throw new Error('Story image upload failed');
    }
  }

  async function handlePublish(): Promise<void> {
    // The publish call already carries the current fields -- cancel the pending autosave instead
    // of flushing it, so the two requests never race each other. On a first publish (no row yet)
    // a race would double-INSERT and hit the campaign's unique constraint.
    cancel();
    setIsPublishing(true);
    try {
      const saved = await upsertCampaignStory(campaignId, {
        storyText,
        storySummary,
        publish: true,
      });
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

  const canPublish = storyText.trim().length > 0 && storySummary.trim().length > 0;

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
            <label htmlFor="storySummary" className="fl">
              {t('editor.story.summaryLabel')}
            </label>
            <input
              id="storySummary"
              type="text"
              className="fi"
              maxLength={SUMMARY_MAX_LENGTH}
              placeholder={t('editor.story.summaryPlaceholder')}
              value={storySummary}
              onChange={(e) => handleSummaryChange(e.target.value)}
            />
            <p className="fhint">{t('editor.story.summaryHint', { count: storySummary.length, max: SUMMARY_MAX_LENGTH })}</p>
          </div>

          <div className="fg">
            <label className="fl">{t('editor.story.label')}</label>
            <RichTextEditor
              value={storyText}
              onChange={handleStoryTextChange}
              onImageUpload={handleImageUpload}
              placeholder={t('editor.story.placeholder')}
            />
          </div>

          <div className="frow-actions">
            <button
              type="button"
              className="cm-btn cm-btn-primary cm-btn-sm"
              onClick={handlePublish}
              disabled={isPublishing || !canPublish}
            >
              {publishedAt ? t('editor.story.republish') : t('editor.story.publish')}
            </button>
          </div>
        </div>
      </div>
    </div>
  );
}
