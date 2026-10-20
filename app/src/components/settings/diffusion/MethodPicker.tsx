'use client';

import { useTranslations } from 'next-intl';
import type { EmbedType } from '@/lib/gtm';
import { DIFFUSION_METHODS, MethodGroup } from './diffusionMethods';
import type { MethodFacts } from './diffusionMethods';

const GROUPS: readonly MethodGroup[] = [MethodGroup.COMMONLINK, MethodGroup.SITE];
const FACT_KEYS: readonly (keyof MethodFacts)[] = ['returnTo', 'gtm', 'update', 'consent'];

interface Props {
  value: EmbedType;
  onChange: (method: EmbedType) => void;
  disabled: boolean;
}

/**
 * The diffusion methods as one radio group of cards — hosted by CommonLink, then embedded in the
 * association's site — each listing where donors return, who loads GTM, how it updates and who owns
 * cookie consent. Native radios, so the group works with the keyboard as is.
 */
export function MethodPicker({ value, onChange, disabled }: Props) {
  const t = useTranslations('settings.diffusion');

  return (
    <fieldset className="dif-picker">
      <legend className="sr-only">{t('methods.title')}</legend>
      {GROUPS.map((group) => (
        <div key={group} className={`dif-group dif-group-${group}`}>
          <p className="dif-group-title">{t(`methods.groups.${group}`)}</p>
          <div className="dif-group-cards">
            {DIFFUSION_METHODS.filter((m) => m.group === group).map((m) => {
              const checked = m.id === value;
              return (
                <label key={m.id} className={`dif-method${checked ? ' active' : ''}`}>
                  <span className="dif-method-head">
                    <input
                      type="radio"
                      name="dif-method"
                      className="dif-method-input"
                      value={m.id}
                      checked={checked}
                      onChange={() => onChange(m.id)}
                      disabled={disabled}
                    />
                    <span className="dif-method-title">{t(`methods.${m.id}.title`)}</span>
                  </span>
                  {m.hasTag && <span className="badge badge-active">{t(`methods.${m.id}.tag`)}</span>}
                  <span className="dif-method-desc">{t(`methods.${m.id}.desc`)}</span>
                  <span className="dif-facts">
                    {FACT_KEYS.map((key) => (
                      <span key={key} className="dif-fact">
                        <span className="dif-fact-label">{t(`methods.facts.${key}`)}</span>
                        <span className={`dif-fact-value${m.facts[key] === 'consentYours' ? ' warn' : ''}`}>
                          {t(`methods.factValues.${m.facts[key]}`)}
                        </span>
                      </span>
                    ))}
                  </span>
                </label>
              );
            })}
          </div>
        </div>
      ))}
    </fieldset>
  );
}
