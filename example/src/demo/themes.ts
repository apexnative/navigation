import type { NavigationTheme } from '@apexnative/navigation';

/** Themes of the theme demo: Ocean colors the bars too, Forest keeps the glass bars of iOS 26. */
export const themes = {
  ocean: {
    colors: {
      primary: '#0984E3',
      background: { light: '#EEF6FD', dark: '#06131F' },
      bar: { light: '#D6EBFB', dark: '#0D2538' },
      text: { light: '#0B2A44', dark: '#E3F2FD' },
      border: '#0984E3',
      badge: '#E17055',
    },
  },
  forest: {
    colors: {
      primary: '#00A86B',
      background: { light: '#F1F8F2', dark: '#07130B' },
      text: { light: '#123B22', dark: '#DDF5E4' },
      badge: '#F39C12',
    },
  },
} satisfies Record<string, NavigationTheme>;
