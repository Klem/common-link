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
  indigo: string;
  indigoLight: string;
  coral: string;
  amber: string;
  success: string;
  error: string;
}

export interface Theme {
  name: string;
  label: string;
  colors: ThemeColors;
}

export { defaultTheme } from './default';
export { light } from './light';
export { darkTheme } from './dark';

import { defaultTheme } from './default';
import { light } from './light';
import { darkTheme } from './dark';

export const themes: Theme[] = [defaultTheme, light, darkTheme];
