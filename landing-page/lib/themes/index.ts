export interface Theme {
  name: string;
  label: string;
  colors: {
    primary: string;
    'primary-dark': string;
    'primary-light': string;
    secondary: string;
    'secondary-light': string;
    'secondary-pale': string;
    accent: string;
    'accent-light': string;
    background: string;
    'background-alt': string;
    'text-dark': string;
    'text-body': string;
    'text-muted': string;
    border: string;
    'border-light': string;
    success: string;
    error: string;
  };
  fonts: {
    ui: string;
    body: string;
  };
  radius: {
    sm: string;
    md: string;
    lg: string;
    xl: string;
  };
}

import { lienCommunTheme } from './lien-commun';
import { oceanTheme } from './ocean';
import { forestTheme } from './forest';

export const themes: Record<string, Theme> = {
  'lien-commun': lienCommunTheme,
  ocean: oceanTheme,
  forest: forestTheme,
};

export const defaultThemeName = 'lien-commun';
