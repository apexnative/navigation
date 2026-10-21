import type { ComponentType } from 'react';
import type { Options } from '../../interfaces/Options';
import { getAppContext } from '../context/appContext';
import { readScreenOptions, ScreenOptions } from '../options/ScreenOptions';
import { getTheme, type NavigationTheme } from '../theme/theme';
import type { RegisteredContext } from '../types';
import type { ScreenArrangement } from './arrangement';

/** What the options of a screen can read besides its props, as they are when a command shows the screen. */
export interface OptionsEnvironment {
  theme: NavigationTheme;
  context: RegisteredContext;
}

/** The options of a screen: options as they are, or a chain built from its props, the theme and the context. */
export type ScreenOptionsInput<Props> =
  Options | ((options: ScreenOptions, props: Props, app: OptionsEnvironment) => ScreenOptions);

/** A screen with what goes with it. */
export interface ScreenDefinition<Props = any> {
  component: ComponentType<Props>;
  /** Applied when a command shows the screen, before it appears. */
  options?: ScreenOptionsInput<Props>;
  /**
   * One screen per item, such as the chat with one person: a push of this screen goes back to the screen of the same
   * stack with the same key, closing the screens over it, instead of showing a second one. The name of the screen goes
   * in front of the key, so two screens never share one. In another stack it shows as usual.
   */
  key?: (props: Props) => string | number;
  /**
   * A second screen beside or under this one in a large window (iPad, iPhone Duo open, Android tablets and open
   * foldables); anywhere else this screen shows alone, as it does without it. Shown by its own name, the screen is the
   * primary of the pair: its bar is the pair's. For a pair with every setting, `defineArrangement`.
   *
   * @example
   * defineScreen({ component: ProfileScreen, arrangement: { secondary: 'Details' } });
   */
  arrangement?: ScreenArrangement<Props & object>;
}

/**
 * Describes a screen next to its component, so its registration stays one line. The same object can go right into
 * `screens` of createNavigation.
 */
export function defineScreen<Props>(definition: ScreenDefinition<Props>): ScreenDefinition<Props> {
  return definition;
}

export function buildScreenOptions<Props>(
  input: ScreenOptionsInput<Props> | undefined,
  props: Props
): Options {
  if (!input) return {};
  if (typeof input !== 'function') return input;
  const app: OptionsEnvironment = { theme: getTheme(), context: getAppContext() };
  return readScreenOptions(input(new ScreenOptions(), props, app));
}
