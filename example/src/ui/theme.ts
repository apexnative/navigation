import { useColorScheme } from 'react-native';

/** The colors of the ApexNative brand (retro-futurism): the same in both themes. */
export const brand = {
  coral: '#E76F51',
  /** The flat shadow under a coral button. */
  coralDeep: '#B4472C',
  mustard: '#E9B949',
  teal: '#2A9D8F',
  red: '#D1495B',
  sky: '#5FA8D3',
  lavender: '#9C89B8',
  orange: '#F4A261',
  pink: '#E5989B',
  cream: '#F3E9D2',
  navy: '#1E2A44',
  deep: '#101828',
};

/** Colors of things that look the same in both themes: demo views, overlays. */
export const palette = {
  primary: brand.coral,
  accent: brand.teal,
  danger: brand.red,
  warning: brand.mustard,
  info: brand.sky,
};

export interface Theme {
  dark: boolean;
  /** Behind every screen. */
  background: string;
  /** Cards and lists. */
  card: string;
  text: string;
  /** Text that explains: intros, what to check. */
  body: string;
  textMuted: string;
  /** Labels in capitals above groups, paths of options. */
  label: string;
  border: string;
  /** Lines between the rows of a card. */
  divider: string;
  /** Inline code, segmented controls. */
  code: string;
  /** Buttons of the bars, the selected tab: the tint of the navigation (`app/theme.ts`). */
  primary: string;
  primarySoft: string;
  /** Numbers of scenarios. */
  pill: string;
  pillText: string;
  /** A selected value of a segmented control. */
  selected: string;
  onSelected: string;
  /** Outlines of badges and of chips that are not selected. */
  outline: string;
  chevron: string;
  /** Blocks of code and the space card: dark in both themes. */
  space: string;
  /** Times of the journal. */
  time: string;
  /** The card of an error in the journal: its border and its text. */
  errorBorder: string;
  errorText: string;
  accent: string;
  danger: string;
  warning: string;
  info: string;
}

const light: Theme = {
  dark: false,
  background: '#F4ECDC',
  card: '#FFFAF0',
  text: brand.navy,
  body: '#4E4A3F',
  textMuted: '#6B5F49',
  label: '#7A6A4F',
  border: '#E3D6BC',
  divider: '#EFE4CC',
  code: '#EFE4CC',
  primary: '#C4532F',
  primarySoft: 'rgba(196, 83, 47, 0.13)',
  pill: '#FBE4D9',
  pillText: '#9E3F22',
  selected: brand.navy,
  onSelected: brand.cream,
  outline: '#D9CBAE',
  chevron: '#B4A684',
  space: brand.deep,
  time: '#7A6A4F',
  errorBorder: '#EBC9CC',
  errorText: '#B23A4B',
  accent: brand.teal,
  danger: '#B23A4B',
  warning: brand.mustard,
  info: brand.sky,
};

const dark: Theme = {
  dark: true,
  background: brand.deep,
  card: brand.navy,
  text: brand.cream,
  body: '#D9CFB8',
  textMuted: '#B5A88C',
  label: '#B5A88C',
  border: '#2B3754',
  divider: '#2B3754',
  code: '#2B3754',
  primary: brand.orange,
  primarySoft: 'rgba(244, 162, 97, 0.16)',
  pill: 'rgba(244, 162, 97, 0.16)',
  pillText: brand.orange,
  selected: brand.cream,
  onSelected: brand.deep,
  outline: '#3A4766',
  chevron: '#6B7A99',
  space: '#0B111D',
  time: '#8C9AB5',
  errorBorder: '#3A2A3A',
  errorText: '#F2A7B0',
  accent: brand.teal,
  danger: '#E26D7B',
  warning: brand.mustard,
  info: brand.sky,
};

export function useTheme(): Theme {
  return useColorScheme() === 'dark' ? dark : light;
}

/** ApexNavigation understands `{ light, dark }` colors natively on both platforms. */
export const dynamic = {
  background: { light: light.background, dark: dark.background },
  card: { light: light.card, dark: dark.card },
  text: { light: light.text, dark: dark.text },
  textMuted: { light: light.textMuted, dark: dark.textMuted },
  primary: { light: light.primary, dark: dark.primary },
  danger: { light: light.danger, dark: dark.danger },
};

/** The kinds of entries of the journal: the same in both themes. */
export const kindColors = {
  event: brand.teal,
  state: brand.mustard,
  command: brand.sky,
  error: '#E26D7B',
};

/** The rims of the sections' portholes, in turn. */
const rims = [
  brand.coral,
  brand.mustard,
  brand.teal,
  brand.red,
  brand.sky,
  brand.lavender,
  brand.orange,
  brand.pink,
];

/** The rim of a section's porthole, by its number. */
export function rimColor(section: number): string {
  return rims[(section - 1) % rims.length];
}

/** Colors to pick for options. */
export const swatches = [
  brand.coral,
  brand.mustard,
  brand.teal,
  brand.sky,
  brand.lavender,
  brand.red,
];
