'use client';

import { useEffect, useRef } from 'react';
import { useTranslations } from 'next-intl';
import { impactWording, withTerminalPunctuation } from '@/lib/impactWording';
import type { ShareableImpact } from '@/lib/impactWording';

interface Props {
  impact: ShareableImpact | null;
  onClose: () => void;
}

const CARD_WIDTH = 600;
const CARD_HEIGHT = 315;
// storySummary can be up to 220 chars (the form's own cap) -- at the card's font size that can
// overflow the fixed-height <foreignObject> and clip silently in the downloaded file, with no
// scrollbar to reveal it. Truncated at a word boundary so a long summary degrades to a shorter
// but still complete-looking sentence, never a mid-word cut.
const MAX_CARD_WORDING_LENGTH = 130;

function truncateForCard(text: string): string {
  if (text.length <= MAX_CARD_WORDING_LENGTH) return text;
  const cut = text.slice(0, MAX_CARD_WORDING_LENGTH);
  const lastSpace = cut.lastIndexOf(' ');
  return `${(lastSpace > 0 ? cut.slice(0, lastSpace) : cut).trim()}…`;
}

/**
 * Builds the shareable card as an inline SVG string -- same design tokens as the rest of the
 * dashboard, hardcoded here (not `var(--token)`) so the downloaded file stays self-contained
 * once it leaves the page. No amount, no donor share: project-level wording only (D6).
 *
 * Renders the same collective wording as the gallery card (prefix + wording + suffix), not the
 * bare impact text -- once this card leaves the page (downloaded, posted on another platform) the
 * "Vous y avez contribué" framing is the only thing left carrying the point of D6.
 */
function buildCardSvg(
  impact: ShareableImpact,
  wording: string | null,
  wordingPrefix: string,
  wordingSuffix: string,
  notYetPublishedText: string,
): string {
  const escape = (s: string) => s.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;');
  const bodyText = wording
    ? escape(`${wordingPrefix} ${withTerminalPunctuation(truncateForCard(wording))} ${wordingSuffix}`)
    : escape(notYetPublishedText);
  return `<svg xmlns="http://www.w3.org/2000/svg" width="${CARD_WIDTH}" height="${CARD_HEIGHT}" viewBox="0 0 ${CARD_WIDTH} ${CARD_HEIGHT}">
  <rect width="${CARD_WIDTH}" height="${CARD_HEIGHT}" rx="20" fill="#FDF8F0" />
  <rect width="${CARD_WIDTH}" height="8" fill="#4ECDC4" />
  <text x="40" y="80" font-family="Arial, sans-serif" font-size="40">${escape(impact.campaignEmoji)}</text>
  <text x="100" y="72" font-family="Arial, sans-serif" font-size="26" font-weight="700" fill="#171744">${escape(impact.campaignName)}</text>
  <text x="100" y="98" font-family="Arial, sans-serif" font-size="15" fill="#62627D">${escape(impact.associationName)}</text>
  <foreignObject x="40" y="140" width="${CARD_WIDTH - 80}" height="140">
    <p xmlns="http://www.w3.org/1999/xhtml" style="margin:0;font-family:Arial, sans-serif;font-size:17px;line-height:1.5;color:#171744;">${bodyText}</p>
  </foreignObject>
  <text x="40" y="${CARD_HEIGHT - 24}" font-family="Arial, sans-serif" font-size="13" fill="#62627D">CommonLink</text>
</svg>`;
}

/**
 * Share modal: card preview (no amount, no donor share), LinkedIn/X/WhatsApp share links built
 * client-side, and SVG download. Same focus/Escape handling as `DonationTraceabilityModal` --
 * initial focus on the close button and Escape closes it, but neither traps Tab-cycling.
 */
export function ShareImpactModal({ impact, onClose }: Props) {
  const t = useTranslations('dashboard.donor.share');
  const tImpact = useTranslations('dashboard.donor.impact');
  const closeButtonRef = useRef<HTMLButtonElement>(null);

  useEffect(() => {
    if (!impact) return;
    closeButtonRef.current?.focus();

    function handleKeyDown(e: KeyboardEvent) {
      if (e.key === 'Escape') onClose();
    }
    document.addEventListener('keydown', handleKeyDown);
    return () => document.removeEventListener('keydown', handleKeyDown);
  }, [impact, onClose]);

  if (!impact) return null;

  const wording = impactWording(impact);
  const svg = buildCardSvg(
    impact,
    wording,
    tImpact('wordingPrefix'),
    tImpact('wordingSuffix'),
    t('notYetPublished'),
  );
  const shareText = t('shareText', { campaignName: impact.campaignName, associationName: impact.associationName });
  const shareUrl = typeof window !== 'undefined' ? window.location.origin : '';

  function handleDownload(): void {
    const blob = new Blob([svg], { type: 'image/svg+xml' });
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = `impact-${impact!.campaignId}.svg`;
    a.click();
    URL.revokeObjectURL(url);
  }

  return (
    <div className="modal-backdrop" onClick={onClose}>
      <div
        className="modal"
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
        <div className="modal-body flex flex-col gap-4">
          <div
            className="rounded-lg overflow-hidden border border-mist-lavender"
            dangerouslySetInnerHTML={{ __html: svg }}
          />

          <div className="flex flex-wrap gap-2">
            <a
              className="btn btn-secondary btn-sm"
              href={`https://www.linkedin.com/sharing/share-offsite/?url=${encodeURIComponent(shareUrl)}`}
              target="_blank"
              rel="noopener noreferrer"
            >
              {t('linkedin')}
            </a>
            <a
              className="btn btn-secondary btn-sm"
              href={`https://twitter.com/intent/tweet?text=${encodeURIComponent(shareText)}`}
              target="_blank"
              rel="noopener noreferrer"
            >
              {t('x')}
            </a>
            <a
              className="btn btn-secondary btn-sm"
              href={`https://wa.me/?text=${encodeURIComponent(shareText)}`}
              target="_blank"
              rel="noopener noreferrer"
            >
              {t('whatsapp')}
            </a>
          </div>

          <button type="button" className="btn btn-primary btn-sm self-start" onClick={handleDownload}>
            {t('download')}
          </button>
        </div>
      </div>
    </div>
  );
}
