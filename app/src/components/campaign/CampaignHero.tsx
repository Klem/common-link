'use client';

import { useState, useEffect, useRef } from 'react';
import { useTranslations } from 'next-intl';
import type { CampaignDto, CampaignStatus } from '@/types/campaign';

const EMOJIS = [
  '🏕','🏙','🌍','🎗','🎀','🎁','🎓','🏥','🌱','🌊','🏡','🎭','🎪','🏛','⚡','🏃',
  '🌺','🏄','🌿🍃','🏔','🎯','🦋','🦄🌈','🎵','🎤','🌸🌼','🏇','🌞','🎠','🌻','🌈','🎡🎢','🦅',
];

/** Maps a campaign status to its display label key and badge class. */
function statusBadge(status: CampaignStatus): { labelKey: string; cls: string } {
  switch (status) {
    case 'LIVE':
      return { labelKey: 'status.live', cls: 'badge badge-active' };
    case 'ENDED':
      return { labelKey: 'status.ended', cls: 'badge badge-error' };
    default:
      return { labelKey: 'status.draft', cls: 'badge badge-neutral' };
  }
}

/**
 * Formats a pair of ISO date strings into a human-readable range, e.g. "15 jan. → 31 mars 2025".
 */
function formatDateRange(startDate: string | null, endDate: string | null): string {
  const fmt = (d: string) =>
    new Date(d).toLocaleDateString('fr-FR', { day: 'numeric', month: 'short', year: 'numeric' });
  if (startDate && endDate) return `${fmt(startDate)} → ${fmt(endDate)}`;
  if (startDate) return `À partir du ${fmt(startDate)}`;
  if (endDate) return `Jusqu'au ${fmt(endDate)}`;
  return '';
}

interface CampaignHeroProps {
  /** Full campaign data to display. */
  campaign: CampaignDto;
  /** Called when the user edits the name input. */
  onNameChange: (name: string) => void;
  /** Called when the user picks a new emoji. */
  onEmojiChange: (emoji: string) => void;
}

/**
 * Hero section of the campaign editor.
 *
 * Displays the campaign emoji (with picker), editable name, date range, goal,
 * status pill, and a progress bar for raised / goal amounts.
 */
export function CampaignHero({ campaign, onNameChange, onEmojiChange }: CampaignHeroProps) {
  const t = useTranslations('dashboard.campaigns');
  const [pickerOpen, setPickerOpen] = useState(false);
  const pickerRef = useRef<HTMLDivElement>(null);

  const percentage = campaign.goal > 0 ? Math.min((campaign.raised / campaign.goal) * 100, 100) : 0;
  const { labelKey, cls } = statusBadge(campaign.status);
  const dateRange = formatDateRange(campaign.startDate, campaign.endDate);

  /* Close picker when clicking outside */
  useEffect(() => {
    function handleClickOutside(e: MouseEvent) {
      if (pickerRef.current && !pickerRef.current.contains(e.target as Node)) {
        setPickerOpen(false);
      }
    }
    if (pickerOpen) document.addEventListener('mousedown', handleClickOutside);
    return () => document.removeEventListener('mousedown', handleClickOutside);
  }, [pickerOpen]);

  return (
    <div className="relative overflow-hidden rounded-[18px] border border-[var(--color-border)] p-[28px_32px] mb-[24px] bg-bg-2">
      {/* Top gradient bar */}
      <div className="absolute top-0 left-0 right-0 h-[3px] bg-gradient-to-r from-[var(--color-green)] to-[var(--color-cyan)]" />

      {/* Top row */}
      <div className="flex justify-between items-start gap-[16px]">
        {/* Left — emoji + name + dates */}
        <div className="flex items-start gap-[16px] flex-1 min-w-0">
          {/* Emoji picker trigger */}
          <div className="relative" ref={pickerRef}>
            <button
              type="button"
              onClick={() => setPickerOpen((o) => !o)}
              className="text-[36px] leading-none cursor-pointer hover:opacity-80 transition-opacity select-none"
              aria-label="Changer l'emoji"
            >
              {campaign.emoji || '🏕'}
            </button>

            {pickerOpen && (
              <div className="absolute top-[calc(100%+8px)] left-0 z-50 flex flex-wrap gap-[4px] w-[240px] rounded-xl border border-[var(--color-border)] p-[8px] shadow-lg bg-bg-2">
                {EMOJIS.map((em) => (
                  <button
                    key={em}
                    type="button"
                    onClick={() => {
                      onEmojiChange(em);
                      setPickerOpen(false);
                    }}
                    className="p-[4px] rounded-[6px] text-[17px] hover:bg-[var(--color-bg-3)] cursor-pointer transition-colors"
                  >
                    {em}
                  </button>
                ))}
              </div>
            )}
          </div>

          {/* Name + info line */}
          <div className="flex-1 min-w-0">
            <input
              type="text"
              value={campaign.name}
              onChange={(e) => onNameChange(e.target.value)}
              className="font-display w-full bg-transparent border-none outline-none text-[22px] font-extrabold text-[var(--color-text)] focus:border-b-2 focus:border-[var(--color-green)]/30"
              placeholder="Nom de votre campagne✨"
            />
            <div className="flex items-center gap-[10px] mt-[6px] text-[12px] text-[var(--color-text-2)]">
              {dateRange && <span>{dateRange}</span>}
              {dateRange && <span>·</span>}
              <span>
                {t('editor.hero.goal')} :{' '}
                <strong className="text-[var(--color-green)]">
                  {campaign.goal.toLocaleString('fr-FR')} €
                </strong>
              </span>
            </div>
          </div>
        </div>

        {/* Right — status pill */}
        <span className={cls}>
          {t(labelKey)}
        </span>
      </div>

      {/* Stat cards */}
      <div className="grid grid-cols-3 gap-[12px] mt-[20px]">
        <div className="bg-bg-3 rounded-lg p-[12px_14px]">
          <div className="text-[11px] text-[var(--color-text-2)] mb-[2px]">{t('editor.hero.collected')}</div>
          <div className="font-display font-bold text-[15px] text-[var(--color-green)]">
            {campaign.raised.toLocaleString('fr-FR')} €
          </div>
        </div>
        <div className="bg-bg-3 rounded-lg p-[12px_14px]">
          <div className="text-[11px] text-[var(--color-text-2)] mb-[2px]">{t('editor.hero.goal')}</div>
          <div className="font-display font-bold text-[15px] text-[var(--color-text)]">
            {campaign.goal.toLocaleString('fr-FR')} €
          </div>
        </div>
        <div className="bg-bg-3 rounded-lg p-[12px_14px]">
          <div className="text-[11px] text-[var(--color-text-2)] mb-[2px]">{t('editor.hero.progress')}</div>
          <div className="font-display font-bold text-[15px] text-[var(--color-text)]">
            {Math.round(percentage)} %
          </div>
        </div>
      </div>

      {/* Progress bar */}
      <div className="progress-bar mt-[16px]">
        <div
          className="progress-fill teal"
          style={{ width: `${percentage}%` }}
        />
      </div>
    </div>
  );
}
