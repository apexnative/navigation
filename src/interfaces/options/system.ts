import type { Color } from './common';

/**
 * An edge of the screen: `'top'`, `'right'`, `'bottom'` or `'left'`. The edges are physical: in a right-to-left
 * layout `'left'` stays the left side. A value outside these four is ignored.
 */
export type SafeAreaEdge = 'top' | 'right' | 'bottom' | 'left';

/**
 * An orientation a screen allows. `'all'` and `'sensor'` allow all four sides; `'portrait'` the upright portrait;
 * `'upsideDown'` the upside-down portrait; `'landscape'` and `'sensorLandscape'` both landscape sides;
 * `'sensorPortrait'` both portrait sides; `'default'` the orientations the app declares (`Info.plist` on iOS, the
 * manifest on Android). The screen allows the union of the sides of all its values. An unknown value is dropped.
 */
export type LayoutOrientation =
  | 'all'
  | 'default'
  | 'portrait'
  | 'landscape'
  | 'upsideDown'
  | 'sensor'
  | 'sensorLandscape'
  | 'sensorPortrait';

/**
 * The place of a screen in the system: the edges where it keeps its content out of the bars, its background, the
 * orientations it allows, and how it treats the home indicator, system gestures and the keyboard. The system follows
 * the screen that shows on top, and moves to the options of the next one when another screen comes on top. Options
 * given to a stack, tabs or a side menu apply to their screens.
 */
export interface OptionsLayout {
  /**
   * The edges on which the screen keeps its content out of what covers it: the bars, the status bar, the home
   * indicator or navigation bar, the camera cutout. `[]` lets the content reach every edge. The array replaces the
   * one from a lower layer. The screen adds the padding itself; `useSafeAreaInsets()` returns what is still covered,
   * which is 0 on an edge the screen keeps and the whole covered size on an edge where the content goes under a bar.
   * Edges listed here always win, also for a screen with a large title.
   *
   * Without it, on iOS: every edge except those where the content goes under a bar that shows (the top bar, a tab
   * bar or toolbar at the bottom, the side of a vertical bar); a screen with a large title keeps the top free so its
   * list can scroll under the bar. On Android: all four edges, except the top under a top bar that hides on scroll
   * or under a large title, and the bottom under tabs that hide on scroll.
   */
  safeAreaEdges?: SafeAreaEdge[];
  /**
   * The color of the screen, also under its React content. On iOS it is the background of the screen's view (the
   * system background without it); stacks, tabs and side menus paint their view with the color of the screen that
   * shows, seen behind transparent bars and around screens during transitions. On Android it fills the screen and
   * its containers but leaves room at the top for the status bar while it shows. `null` makes the background
   * transparent. Without it, Android shows the window's background (the app theme).
   */
  backgroundColor?: Color;
  /**
   * The color under the React content only, drawn over `backgroundColor`, for content that should differ from the
   * screen around it. Without it, `backgroundColor` shows under the content. `null` removes the color.
   */
  componentBackgroundColor?: Color;
  /**
   * The orientations the screen allows. The screen on top decides. An empty array, or no value in any layer, leaves
   * the orientations of the app. The array replaces the one from a lower layer. The change applies when the screen
   * appears and at once through `mergeOptions`; the interface turns by itself if the current orientation is no longer
   * allowed. On iOS 15 the interface only turns if the device is held in an allowed orientation. On iOS a modal decides
   * only when it covers the whole screen (`fullScreen`, `overFullScreen`); under a sheet or a popover the screen below
   * keeps deciding. On Android a sheet decides like any modal. An overlay decides only when its own options set it. The
   * system does not apply the request on large screens: an iPad that runs in multitasking, and on Android 16 an app
   * that targets API 36 on a screen at least 600 dp wide.
   */
  orientation?: LayoutOrientation[];
  /**
   * (iOS 27.1+) Whether the bars and the status bar of the screen may stand in a vertical strip at the side, as on
   * iPhone Duo. `false` keeps them horizontal; without it, the system decides. Set it once for a screen rather than
   * switching it by state. With `false` the screen has no vertical strip, so `drawBehindVerticalBar` has no side to
   * act on. It does nothing before iOS 27.1 or in an app built with an older SDK.
   * @default true
   */
  verticalBars?: boolean;
  /**
   * (iOS 27.1+) Whether the screen goes under the vertical bar of iPhone Duo, as it goes under a top bar: a list
   * draws its blur up to the edge and keeps its rows clear of the bar itself with `useSafeAreaInsets()`. `false` keeps
   * the content outside the bar like any other safe area edge. Explicit `safeAreaEdges` decide in its place. The side
   * is the physical side where the bar stands. It updates with every layout and with `mergeOptions`.
   * @default true
   */
  drawBehindVerticalBar?: boolean;
  /**
   * (iOS) Whether the system hides the home indicator after the user has not touched the screen for a while and shows
   * it again on touch, for video, games or photos. The value comes from the screen on top with its containers, read
   * when the system asks, and `mergeOptions` applies it at once.
   * @default false
   */
  autoHideHomeIndicator?: boolean;
  /**
   * (iOS) The edges that give the app the first swipe the system would take (home from the bottom, the notification
   * and control centers from the top). The system shows a hint, and a second swipe runs its gesture. The edges are
   * physical and do not swap in a right-to-left layout. Without it, no edge is deferred. It applies after the screen
   * appears and at once through `mergeOptions`.
   */
  deferSystemGestures?: SafeAreaEdge[];
  /**
   * (Android) Whether the screen makes room for the keyboard. `true` ends the React content above the keyboard (the
   * bottom inset is the larger of the keyboard and the navigation bar); `false` keeps the size of the screen and lets
   * the keyboard cover its bottom, as on iOS, for a screen that handles the keyboard itself. It needs edge-to-edge:
   * without it the system resizes the window by the manifest's `windowSoftInputMode`, and the option changes nothing.
   * `mergeOptions` applies it at once, also with the keyboard open. On iOS the keyboard always covers the screen.
   * @default true
   */
  adjustResize?: boolean;
}

/**
 * The status bar while the screen shows. The screen on top decides, and each key merges on its own. An overlay
 * changes the status bar only with values of its own. On iOS a modal takes the status bar only when it shows full
 * screen; under a sheet, a popover or an `overFullScreen` modal the screen below keeps it. iOS needs
 * `UIViewControllerBasedStatusBarAppearance` set to `true` in `Info.plist` (the CLI's `setup` does that), so the app
 * cannot use React Native's own `StatusBar` and sets the status bar through screen options instead.
 */
export interface OptionsStatusBar {
  /**
   * Whether the status bar shows. It applies when the screen appears and at once through `mergeOptions`. On iOS an
   * explicit value beats `hideWithTopBar`, the change has no animation, and on iPhone in landscape the system hides
   * the bar by itself. On Android a hidden bar comes back for a moment on a swipe from the edge, a screen without a top
   * bar starts at the top of the window, the top bar strip gets the height of the camera cutout, and the default color
   * is transparent. A screen without the option shows the bar again on Android. A status bar hidden or recolored by
   * app code (React Native's `StatusBar`) is not undone by the next screen on Android unless that screen's values
   * differ from the ones the navigation applied last.
   * @default true
   */
  visible?: boolean;
  /**
   * The color of the text and icons: `'light'` for a dark background, `'dark'` for a light one. Without it, or with
   * another value, the color follows the background and the interface style: dark content in the light style and
   * light content in the dark style on iOS; on Android the contrast with what is under the bar (its own opaque color,
   * the top bar that reaches under it, the window background). It applies when the screen appears, at once through
   * `mergeOptions`, and on Android also when the theme or the orientation changes. On iOS a change of the style alone,
   * through `mergeOptions` or as another screen comes on top, fades over 0.35 s unless `animate` is `false`; with a
   * change of `visible` it changes at once; along a push, a pop, a tab switch or a modal it changes with the
   * transition.
   */
  style?: 'light' | 'dark';
  /**
   * (Android) The color of the status bar. Without it, the color the app theme gave the window when the activity was
   * created (transparent with edge-to-edge; transparent while the bar is hidden); it doesn't follow a light/dark switch
   * made without recreating the activity. A screen without a color after one with a color goes back to
   * the theme's color. The color changes at once, without animation, also between screens and tabs. It lies over the
   * screens, so an opaque color covers the strip of the top bar under the status bar and a transparent one shows it.
   * `null` takes the theme's color. iOS draws the status bar transparent over the screen and does not read it.
   */
  backgroundColor?: Color;
  /**
   * (Android) Whether the screen and its top bar reach under the status bar. Without it, a shown top bar reaches under
   * the bar as one surface (its color, background component and shadow reach the top of the window, its row of title
   * and buttons sits below the bar), and a screen without a top bar starts below the bar. `true` lets a screen without
   * a top bar start at the top of the window, with its safe area keeping content below. `false` starts the top bar
   * below the status bar, which keeps its own color. A sheet modal has no status bar above it, so this does not apply
   * there. On iOS the screen is always under the status bar and the safe area keeps the content below.
   */
  drawBehind?: boolean;
  /**
   * (Android) Whether a status bar color that is opaque is drawn at 65% opacity, so the screen shows through. A color
   * that is already partly transparent stays as it is, and with edge-to-edge the default color is transparent, so
   * there is nothing to change. It does not blur. It applies when the screen appears and through `mergeOptions`.
   * @default false
   */
  translucent?: boolean;
  /**
   * (iOS) Whether a change of `style` animates, over 0.35 s. `false` changes it at once. The visibility never
   * animates.
   * @default true
   */
  animate?: boolean;
  /**
   * (iOS) Whether the status bar hides while the top bar of the screen's stack is hidden. An explicit `visible` wins.
   * The value is read when the system asks about the status bar, and the system is asked again whenever the stack's
   * top bar shows or hides, by an option or by scrolling, a tap or the keyboard (not for the bar of a tab opened from
   * More).
   * @default false
   */
  hideWithTopBar?: boolean;
  /**
   * (iOS) Whether a blur shows under the status bar, drawn over the screen in the frame the status bar has when the
   * option applies. It applies when the screen appears and through `mergeOptions`; `false` removes it. The blur does
   * not follow a rotation or a change of window size.
   * @default false
   */
  blur?: boolean;
}

/**
 * (Android) The navigation bar of Android (three buttons or gestures) while the screen shows. The screen on top
 * decides. A `mergeOptions` call without `navigationBar` leaves the bar as it is. iOS has no such bar; the home
 * indicator is `layout.autoHideHomeIndicator`.
 */
export interface NavigationBarOptions {
  /**
   * The color of the navigation bar, one for each appearance when it is `{ light, dark }`. Without it, and with
   * `null`, and when the screens go under the bar (see `drawBehind`): nothing over the gesture bar, and over the
   * three buttons a translucent plate, white at 90% with dark buttons in the light theme and `#1B1B1B` at 50% with
   * light buttons in the dark theme. When the screens stay above the bar (`drawBehind: false`, or without
   * edge-to-edge): the bottom tabs' color while the screen is in bottom tabs, else the `android:navigationBarColor` of
   * the app theme if the theme sets its own, otherwise black (black at 80% over three buttons). An opaque color gives
   * a bar of that color with the screens above it. A color with transparency lets the screens go under the bar and
   * draws the color over them. The buttons are dark on a light color and light on a dark one. It applies when the
   * screen appears or comes back on top, at once through `mergeOptions` that contains `navigationBar`, on a theme or
   * configuration change, and when the user switches between gestures and three buttons. On Android 7 (API 24–25) the
   * buttons are always light, so a light color there gives light buttons on it.
   */
  backgroundColor?: Color;
  /**
   * Whether the navigation bar shows. `false` hides it; it comes back for a moment on a swipe from the edge, and the
   * screen gets the room of the bar (its inset is 0). It applies when the screen appears or comes back on top and, in
   * `mergeOptions`, at once if the call sets it.
   * @default true
   */
  visible?: boolean;
  /**
   * Whether the screens go under a navigation bar that shows. Without it, they go under it when edge-to-edge is on
   * and the bar has no opaque color of its own; the safe area then keeps the content above the bar (the bottom of
   * `useSafeAreaInsets()`), the background of the tab row goes under the bar and the tabs themselves stay above it.
   * Without edge-to-edge the window ends above the bar. `true` always goes under the bar (without edge-to-edge it
   * changes nothing). `false` keeps the screens above the bar, which then has its own color. It applies when the
   * screen appears and through `mergeOptions`, which asks for the window insets again.
   */
  drawBehind?: boolean;
}

/** A key held with the key of a shortcut: `'command'`, `'shift'`, `'option'` (also `'alternate'`) or `'control'`. */
export type OptionsKeyCommandModifier = 'command' | 'shift' | 'option' | 'alternate' | 'control';

/**
 * (iOS) A keyboard shortcut of a screen, for a hardware keyboard on iPad or iPhone. A press reports the `id` to the
 * app as a key command event. The shortcuts belong to the screen while it shows, and work as long as no one else holds
 * the keyboard and also while a text field of the screen has focus. The array of `keyCommands` replaces the one from a
 * lower layer. The screen's own shortcuts count first and the shortcuts of its stack or tabs apply to its screens that
 * have none; the default options of the app do not take part. The system lists the shortcuts that have a `title`
 * when the user holds the Command key on iPad.
 */
export interface OptionsKeyCommand {
  /** The identifier the key command event reports. An item without a string `id` is skipped. */
  id: string;
  /**
   * The key: a character (`'n'`, `'['`) or a name: `'escape'`, `'enter'`, `'tab'`, `'delete'` (backspace), `'up'`,
   * `'down'`, `'left'` or `'right'` (the arrow keys). An item without a string `input` is skipped.
   */
  input: string;
  /** The keys held together with `input`; unknown names are ignored. Without them, the key alone triggers it. */
  modifiers?: OptionsKeyCommandModifier[];
  /**
   * The name in the list of shortcuts the system shows. Without it, the shortcut works but does not show in the
   * list.
   */
  title?: string;
}
