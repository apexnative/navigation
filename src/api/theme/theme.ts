import type { ColorValue } from 'react-native';
import { setDefaultOptions } from '../../engine/commands';
import type { Color, Options, WindowOptions } from '../../interfaces/Options';
import { launchState } from '../../engine/launchState';
import { mergeOptions } from '../../engine/mergeOptions';
import { getAppContext } from '../context/appContext';
import { readScreenOptions, ScreenOptions } from '../options/ScreenOptions';
import type { OptionsEnvironment } from '../screens/defineScreen';

/**
 * The look of the navigation: its bars, tabs and screen backgrounds. A color can be `{ light, dark }`: the native side
 * picks the one of the system's appearance. A color left out keeps the platform's look: on iOS 26 the glass bars, on
 * Android the colors of the app's Material 3 theme (`Theme.ApexNavigation`: neutral grays).
 */
export interface NavigationTheme {
  colors?: {
    /** Buttons of the top bar, the back button, the selected tab, the cursor of the search. */
    primary?: Color;
    /** Behind every screen. */
    background?: Color;
    /** The top bar and the tab bar. */
    bar?: Color;
    /** Titles. */
    text?: Color;
    /** The lines under the top bar and over the tab bar. */
    border?: Color;
    /** The badges of the tabs. */
    badge?: Color;
  };
}

/**
 * The theme as React content takes it: each color for the current appearance, ready for a style. A color the theme
 * leaves out is undefined.
 */
export interface ResolvedNavigationTheme {
  colors: { [Key in keyof NonNullable<NavigationTheme['colors']>]?: ColorValue };
}

/**
 * The default options of every screen: options as they are, or a chain built from the theme and the app's context. A
 * chain is built again when the theme changes.
 */
export type AppOptionsInput =
  Options | ((options: ScreenOptions, app: OptionsEnvironment) => ScreenOptions);

/** The direction of the app: right to left, left to right, or as the language of the app. */
export type NavigationDirection = 'ltr' | 'rtl' | 'locale';

/**
 * The default options as the native side takes them: the color of the app's window goes with them (iOS), and the
 * direction of the app.
 */
export type DefaultOptions = Options & {
  window?: WindowOptions;
  layout?: { direction?: NavigationDirection };
};

/** The app's own default options, over those of the theme: `options` between what goes under and over them. */
export interface AppDefaults {
  under?: DefaultOptions;
  options?: AppOptionsInput;
  over?: DefaultOptions;
}

let theme: NavigationTheme = {};
let appDefaults: AppDefaults = {};
let direction: NavigationDirection | undefined;
const listeners = new Set<() => void>();

export function getTheme(): NavigationTheme {
  return theme;
}

/** The theme's colors for the light or the dark appearance: of a `{ light, dark }` pair the one, others as they are. */
export function resolveTheme(
  { colors = {} }: NavigationTheme,
  scheme: 'light' | 'dark'
): ResolvedNavigationTheme {
  const resolved: ResolvedNavigationTheme['colors'] = {};
  for (const [key, color] of Object.entries(colors) as [keyof typeof resolved, Color][]) {
    const value = resolveColor(color, scheme);
    if (value !== undefined) resolved[key] = value;
  }
  return { colors: resolved };
}

function resolveColor(color: Color | undefined, scheme: 'light' | 'dark'): ColorValue | undefined {
  if (typeof color === 'string') return color;
  if (color === null || typeof color !== 'object') return undefined;
  if ('light' in color || 'dark' in color) {
    const picked = (color as { light?: unknown; dark?: unknown })[scheme];
    return typeof picked === 'string' ? picked : undefined;
  }
  // A color of the platform (PlatformColor, DynamicColorIOS): a style takes it as it is.
  return color as ColorValue;
}

export function subscribeTheme(listener: () => void): () => void {
  listeners.add(listener);
  return () => {
    listeners.delete(listener);
  };
}

/** The theme, the default options and the direction of the app, before its first launch. */
export function initTheme(
  initial: NavigationTheme | undefined,
  defaults: AppDefaults,
  initialDirection?: NavigationDirection
) {
  theme = initial ?? {};
  appDefaults = defaults;
  direction = initialDirection;
}

/** The default options of the theme with the app's own over them; undefined when there are none. */
export function themedDefaultOptions(): DefaultOptions | undefined {
  const { under, options, over } = appDefaults;
  const own =
    typeof options === 'function'
      ? readScreenOptions(options(new ScreenOptions(), { theme, context: getAppContext() }))
      : options;
  const app: DefaultOptions | undefined = direction && { layout: { direction } };
  const layers = [themeOptions(theme), under, own, over, app].filter(
    (layer): layer is DefaultOptions => layer !== undefined && Object.keys(layer).length > 0
  );
  return layers.length > 0 ? layers.reduce<DefaultOptions>(mergeOptions, {}) : undefined;
}

/** Changes the theme: the screens shown take it too. */
export function setTheme(next: NavigationTheme) {
  theme = next;
  applyDefaults();
  listeners.forEach((listener) => listener());
}

/** The direction of the app now: the config's, or the one `setDirection` gave since. */
export function getDirection(): NavigationDirection | undefined {
  return direction;
}

/** Changes the direction of the app: the bars, the transitions and the swipe back follow at once. */
export function setDirection(next: NavigationDirection) {
  direction = next;
  applyDefaults();
}

// Before the first launch, the launch sets them.
function applyDefaults() {
  if (launchState.launch === 0) return;
  const defaults = themedDefaultOptions();
  if (defaults) setDefaultOptions(defaults);
}

function themeOptions({ colors = {} }: NavigationTheme): Options {
  const { primary, background, bar, text, border, badge } = colors;
  return withoutEmpty({
    layout: { backgroundColor: background },
    topBar: {
      background: { color: bar },
      title: { color: text },
      largeTitle: { color: text },
      backButton: { color: primary },
      leftButtonColor: primary,
      rightButtonColor: primary,
      centerButtonColor: primary,
      borderColor: border,
      searchBar: { tintColor: primary },
    },
    bottomTabs: { backgroundColor: bar, borderColor: border },
    bottomTab: { selectedIconColor: primary, selectedTextColor: primary, badgeColor: badge },
  }) as Options;
}

/** The object without its undefined values and the objects left empty. */
function withoutEmpty(value: Record<string, unknown>): Record<string, unknown> {
  const result: Record<string, unknown> = {};
  for (const [key, item] of Object.entries(value)) {
    if (item === undefined) continue;
    if (typeof item === 'object' && item !== null && !Array.isArray(item)) {
      const inner = withoutEmpty(item as Record<string, unknown>);
      if (Object.keys(inner).length > 0) result[key] = inner;
    } else {
      result[key] = item;
    }
  }
  return result;
}
