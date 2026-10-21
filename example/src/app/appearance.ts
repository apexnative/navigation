import { Appearance } from 'react-native';

/** How the Showcase picks light or dark: as the system does, or always one of them. */
export type AppearanceMode = 'system' | 'light' | 'dark';

let mode: AppearanceMode = 'system';

// A reload of the JavaScript keeps the native override: start from the system's, as the picker does. `index.js` imports
// this file at the start: Metro inlines imports where they are first used, so Settings alone would run it when it opens.
Appearance.setColorScheme('auto');

export function appearanceMode(): AppearanceMode {
  return mode;
}

/** Light or dark for the whole app at once, its native bars too, until it starts again. */
export function setAppearanceMode(next: AppearanceMode) {
  mode = next;
  Appearance.setColorScheme(next === 'system' ? 'auto' : next);
}
