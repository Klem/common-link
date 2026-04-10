'use client';

import { useState, useEffect, useRef } from 'react';
import { useTranslations } from 'next-intl';
import type { CampaignDto, CampaignStatus } from '@/types/campaign';

const EMOJIS = [
  '🌱','🌿','🌳','🏠','🏫','🏥','🌊','💧','🍎','📚','🎒','🩺','🤝','🌟','⭐','💡',
  '🎨','🌈','🕊️','🌻','🏆','🚀','❤️','💚','💙','☀️','🌍','🎓','🍲','🔥','🎁','❄️','🧣',
];

/** Maps a campaign status to its display label key and colour class. */
function statusMeta(status: CampaignStatus): { labelKey: string; cls: string } {
  switch (status) {
    case 'LIVE':
      return { labelKey: 'status.live', cls: 'text-[var(--color-green)] bg-[var(--color-green)]/10 border-[var(--color-green)]/20' };
    case 'ENDED':
      return { labelKey: 'status.ended', cls: 'text-[var(--color-red)] bg-[var(--color-red)]/10 border-[var(--color-red)]/20' };
    default:
      return { labelKey: 'status.draft', cls: 'text-[var(--color-text-2)] bg-[var(--color-bg-3)] border-[var(--color-border)]' };
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
  const { labelKey, cls } = statusMeta(campaign.status);
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
    <div
      className="relative overflow-hidden rounded-[18px] border border-[var(--color-border)] p-[28px_32px] mb-[24px]"
      style={{ background: 'var(--color-bg-2)' }}
    >
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
              {campaign.emoji || '🌱'}
            </button>

            {pickerOpen && (
              <div className="absolute top-[calc(100%+8px)] left-0 z-50 flex flex-wrap gap-[4px] w-[240px] rounded-xl border border-[var(--color-border)] p-[8px] shadow-lg"
                style={{ background: 'var(--color-bg-2)' }}
              >
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
              className="w-full bg-transparent border-none outline-none text-[22px] font-extrabold text-[var(--color-text)] focus:border-b-2 focus:border-[var(--color-green)]/30"
              style={{ fontFamily: 'var(--font-display)' }}
              placeholder="Nom de votre campagne…"
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
        <span className={`text-[12px] font-semibold px-[10px] py-[4px] rounded-full border whitespace-nowrap ${cls}`}>
          {t(labelKey)}
        </span>
      </div>

      {/* Progress */}
      <div className="mt-[20px]">
        <div className="flex justify-between text-[12px] mb-[8px]">
          <span className="text-[var(--color-text-2)]">{t('editor.hero.collected')}</span>
          <span>
            <strong className="text-[var(--color-green)]">
              {campaign.raised.toLocaleString('fr-FR')} €
            </strong>
            {' '}
            <span className="text-[var(--color-muted)]">
              / {campaign.goal.toLocaleString('fr-FR')} €
            </span>
          </span>
        </div>
        <div className="h-[6px] rounded-[3px] overflow-hidden" style={{ background: 'var(--color-bg-3)' }}>
          <div
            className="h-full bg-gradient-to-r from-[var(--color-green)] to-[var(--color-cyan)] transition-all duration-500"
            style={{ width: `${percentage}%` }}
          />
        </div>
      </div>
    </div>
  );
}
