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


import { commonLink } from './commonLink';
import { darkCoral } from './darkCoral';
import { invertedIndigo } from './invertedIndigo';
import { light } from './light';
// import { midContrast } from './midContrast';
import { softLightIndigo } from './softLightIndigo';
import { warmLight } from './warmLight';

export { commonLink } from './commonLink';
export { darkCoral } from './darkCoral';
export { invertedIndigo } from './invertedIndigo';
export { light } from './light';
// export { midContrast } from './midContrast';
export { softLightIndigo } from './softLightIndigo';
export { warmLight } from './warmLight';

export const themes: Theme[] = [
  commonLink,
  darkCoral,
  invertedIndigo,
  light,
  // midContrast,
  softLightIndigo,
  warmLight
];

export const defaultTheme = commonLink;
