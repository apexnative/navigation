import { type ComponentType, createElement } from 'react';
import type { Options } from '../../interfaces/Options';
import { newestType } from '../../engine/refresh';
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

const SCREEN_DEFINITION = Symbol('apexnative.navigation.screenDefinition');

/**
 * A screen of `defineScreen`: its component, which carries the screen's definition. It goes right into `screens` of
 * createNavigation, and rendered on its own (a test) it renders the screen.
 */
export type DefinedScreen<Props> = ComponentType<Props> & {
  readonly [SCREEN_DEFINITION]: ScreenDefinition<Props>;
};

/**
 * Describes a screen next to its component, so its registration stays one line. It gives the screen's component,
 * which goes right into `screens` of createNavigation: a file that exports only its screens stays a boundary of Fast
 * Refresh, so an edit of the screen, its options too, updates it in place instead of reloading the app.
 */
export function defineScreen<Props>(definition: ScreenDefinition<Props>): DefinedScreen<Props> {
  const { component } = definition;
  // The component itself, so that React Refresh, which knows it by the module's export, keeps its state. A component
  // in a second definition gets a component of its own, as one component can't carry two.
  const screen: object = SCREEN_DEFINITION in component ? definedAgain(definition) : component;
  Object.defineProperty(screen, SCREEN_DEFINITION, { value: definition, configurable: true });
  return screen as DefinedScreen<Props>;
}

function definedAgain<Props>({ component }: ScreenDefinition<Props>): ComponentType<Props> {
  const Component = component as ComponentType<object>;
  function Screen(props: Props) {
    return createElement(Component, props as object);
  }
  Screen.displayName = component.displayName ?? component.name;
  return Screen;
}

/**
 * The definition a screen of `defineScreen` carries, the newest one after Fast Refresh ran the screen's module again;
 * undefined for anything else.
 */
export function definitionOf(value: unknown): ScreenDefinition | undefined {
  if ((typeof value !== 'function' && typeof value !== 'object') || value === null)
    return undefined;
  if (!(SCREEN_DEFINITION in value)) return undefined;
  return (newestType(value) as { [SCREEN_DEFINITION]: ScreenDefinition })[SCREEN_DEFINITION];
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
