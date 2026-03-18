export interface ThemeColors {
  bg: string;
  bgTwo: string;
  bgThree: string;
  border: string;
  green: string;
  greenDim: string;
  greenGlow: string;
  greenGlowDim: string;
  yellow: string;
  cyan: string;
  red: string;
  text: string;
  textTwo: string;
  muted: string;
}

export interface Theme {
  name: string;
  label: string;
  colors: ThemeColors;
}

export { dark } from './dark';

import { dark } from './dark';

export const themes: Theme[] = [dark];

export const defaultTheme = dark;
