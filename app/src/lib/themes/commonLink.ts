import type { Theme } from './index';

export const commonLink: Theme = {
  name: 'common-link',
  label: 'CommonLink',
  colors: {
    bg:           '#FDF8F0',           // Soft Cream → Fond de page
    bgTwo:        '#32327D',           // Deep Indigo → Navigation, sidebar, éléments institutionnels
    bgThree:      '#3d3d8f',           // Deep Indigo Light → Variante plus claire
    border:       '#E6E4F4',           // Mist Lavender → Bordures, séparateurs, fonds légers
    green:        '#4ECDC4',           // Bright Teal → CTA principaux, progression
    greenDim:     '#2FB3AA',           // Teal Dark → Hover CTA, liens actifs
    greenGlow:    'rgba(78,205,196,0.45)',
    greenGlowDim: 'rgba(78,205,196,0.20)',

    yellow:       '#FFB347',           // Soft Amber
    cyan:         '#4ECDC4',           // Bright Teal

    red:          '#E8453C',           // Error Red
    text:         '#171744',           // Ink Navy → Texte principal
    textTwo:      '#62627D',           // Slate Lavender → Texte secondaire, labels, méta
    muted:        '#62627D',           // Slate Lavender

    indigo:       '#32327D',           // Deep Indigo
    indigoLight:  '#3d3d8f',           // Deep Indigo Light

    coral:        '#FF6B5B',           // Warm Coral → Accents émotionnels, highlights
    amber:        '#FFB347',           // Soft Amber → Badges, succès, warnings

    success:      '#34C759',           // Success Green
    error:        '#E8453C',           // Error Red
  },
};
