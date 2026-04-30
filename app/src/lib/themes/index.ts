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

export { dark } from './dark';
export { light } from './light';

import { dark } from './dark';
import { light } from './light';

export const themes: Theme[] = [light, dark];

export const defaultTheme = light;
