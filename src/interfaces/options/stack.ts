import type { Color, ImageResource } from './common';

/**
 * (Android) What the system back button and the back gesture do on a screen. The options are read together with
 * those of the screen's stack, tabs and the default options. iOS has no hardware back button.
 */
export interface HardwareBackButtonOptions {
  /**
   * What back does on the first screen of a tab, when the tab's stack has nothing to pop. `'exit'` leaves the choice
   * to what comes next in line, usually leaving the app. `'first'` selects the first tab unless it is already
   * selected, and then leaves. `'previous'` selects the tab that was selected before this one (the tabs keep the
   * history of selections made by the user or by code; going back does not add to it), and leaves when the history is
   * empty. Set it for tabs with the tabs builder's `backBehavior`. The value is read when back is pressed.
   * @default 'exit'
   */
  bottomTabsOnPress?: 'exit' | 'first' | 'previous';
  /**
   * Whether back pops the top screen of the stack. With `false` the press goes to the app as an event instead. Set by
   * the screen hooks that hold back (`usePreventLeave`); apps don't set it.
   * @internal
   */
  popStackOnPress?: boolean;
  /**
   * Whether back on the first screen of a modal dismisses the modal. With `false` the press goes to the app as an
   * event instead. Set by the screen hooks that hold back (`usePreventLeave`); apps don't set it.
   * @internal
   */
  dismissModalOnPress?: boolean;
  /**
   * (Android) Whether back on the first screen of the app, with nothing left to go back to, plays the system's
   * animation of going home. The system decides it as the gesture starts, so with `false` the navigation keeps the
   * back on that screen too: the gesture shows no animation, and on release the press goes to `BackHandler` listeners,
   * then to the activity's own back (`invokeDefaultOnBackPressed` of the app's activity, such as `moveTaskToBack`).
   * Read from the screen back acts on; set it in the default options for the whole app. iOS ignores it.
   * @default true
   */
  systemAnimation?: boolean;
}

/**
 * (Android) A floating action button over the screen, or an entry of its menu. The button shows while its screen is
 * on top of the stack; without the option there is none. `mergeOptions` and the default options of the app apply, and
 * the colors of the theme are read again when the theme changes.
 */
export interface OptionsFab {
  /**
   * The identifier of the button. A press reports it, and the app finds the `onPress` of the button by it. A button
   * without an `id` doesn't show. The same `id` on a later show or merge updates the button; another `id` replaces it.
   * For an entry of `actions` it is the id of that entry's own press.
   */
  id: string;
  /**
   * An identifier for tests and accessibility tools, set on the button (and on the main button of a menu). The
   * scrim of a menu gets `<testID>.scrim`. Without it, the element has no id. It changes with `mergeOptions`.
   */
  testID?: string;
  /**
   * The color of the button's container. Without it, the `primaryContainer` of the app's Material 3 theme (the
   * neutral Material 3 theme of the library if the app theme is not Material 3). A `{ light, dark }` pair follows the
   * theme. It does not tint the icon: without `iconColor` the icon keeps its own colors. A merge with only this key
   * keeps the icon color.
   */
  backgroundColor?: Color;
  /**
   * The color of the container while the button is pressed. Without it, the color doesn't change and only the ripple
   * shows. It is independent of `backgroundColor`: setting one keeps the other.
   */
  clickColor?: Color;
  /** The color of the press ripple. Without it, the one from the theme's style. It stays on a light/dark switch. */
  rippleColor?: Color;
  /**
   * Whether the button shows. `false` hides it with the Material animation, and closes the actions of a menu; `true`
   * in a merge shows it again.
   * @default true
   */
  visible?: boolean;
  /**
   * The icon: the name of a drawable resource of the app (for example a Material Symbols icon) or an image source
   * with a `uri`. It loads in the background and takes the color of `iconColor` if there is one. A menu without an icon
   * shows a plus; with one, the menu's main button shows it.
   */
  icon?: ImageResource;
  /**
   * The tint of the icon. Without it, the `onPrimaryContainer` of the theme. A merge may set it alone, without a new
   * icon.
   */
  iconColor?: Color;
  /**
   * The side of the row where the button stands. `'right'` is the end of the row (the left side in a right-to-left
   * layout), `'left'` the start (the right side in a right-to-left layout). A merge applies it.
   * @default 'right'
   */
  alignHorizontally?: 'left' | 'right';
  /**
   * Whether the button hides after a drag that scrolls the content down and shows after a drag up. Only the vertical
   * lists of the top screen count. A merge applies it; `false` stops listening.
   * @default false
   */
  hideOnScroll?: boolean;
  /**
   * The size of the button: `'mini'` is 40 dp, `'regular'` is 56 dp. It also holds with a Material 3 theme.
   * @default 'regular'
   */
  size?: 'mini' | 'regular';
  /**
   * The entries of a menu: the button becomes a menu whose main button opens and closes the entries, each a small
   * button above it. An entry takes `id`, `testID`, `icon`, `iconColor`, `backgroundColor`, `clickColor`,
   * `rippleColor` and `size`; its other fields do nothing. Pressing an entry reports its `id` and closes the menu. A
   * merge with a non-empty list replaces the entries; an empty list takes them away, and the button is a plain button
   * again.
   */
  actions?: OptionsFab[];
  /**
   * The color that dims the screen while the menu's entries are open, one with transparency such as
   * `'rgba(0, 0, 0, 0.32)'`. A press on it closes the menu. Without it, nothing is dimmed. A merge applies it from the
   * next opening.
   */
  scrimColor?: Color;
}
