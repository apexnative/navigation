/**
 * The settings of one side of a side menu, the same type for `sideMenu.left` and `sideMenu.right`. The left menu opens
 * from the left edge and the right menu from the right edge, also in right-to-left layouts. Only one menu is open at a
 * time: opening one closes the other. The settings apply when the side menu is created, each time a screen of the side
 * menu appears (then all of them anew, with the defaults named below), and at once when they arrive in `mergeOptions`
 * (then only the fields that arrived). A value from a screen of the center holds while that screen is on top of the
 * center, and afterwards the value of the side menu's own options or the default holds.
 */
export interface SideMenuSide {
  /**
   * Whether the menu opens (`true`) or closes (`false`), with an animation unless `animate` is `false`. It is a
   * one-time command, not a state: it is carried out when it arrives and then forgotten, so a screen that appears later
   * and applies its options again doesn't open or close the menu once more. Opening it while the other menu is open
   * closes the other. It opens the menu also when `enabled` is `false`. It does nothing for a menu that isn't on screen
   * to close, or for a side that has no menu, and is ignored in the default options. Set in the layout options, it
   * opens the menu when the side menu first shows. Without it, nothing happens.
   */
  visible?: boolean;
  /**
   * Whether the opening or closing that `visible` asks for is animated. With `false` it happens at once and the
   * screens of the menu get the same appearance events in the same order, except that on Android a menu that opens at
   * once sends its screens DidAppear without WillAppear. It acts only together with `visible` in the same merge.
   * @default true
   */
  animate?: boolean;
  /**
   * Whether the user can open the menu with a gesture. With `false` the gesture doesn't open it, while `visible` and
   * `openMenu` still do; a menu that is already open stays open and can be closed by a gesture. It holds while the
   * screen that set it is on top of the center, and a value of the default options counts.
   * @default true
   */
  enabled?: boolean;
  /**
   * The width of the menu, in points on iOS and dp on Android. It is capped at the width of the side menu (iOS) and at
   * that width less 64 dp (Android). A change in `mergeOptions` applies at once, also to an open menu. A width set by
   * a screen of the center acts while that screen shows.
   * Without it, 280 points on iOS and the width of the side menu less 64 dp on Android (the least gap the Material
   * drawer leaves).
   */
  width?: number;
  /**
   * (Android) The height of the menu in dp; a menu lower than the container is pinned to the top. A change in
   * `mergeOptions` applies at once, also to an open menu. Without it, the menu takes the full height under the status
   * bar. iOS doesn't read it.
   */
  height?: number;
  /**
   * (iOS) Whether a finger can pull the menu out past its width. With `true` the menu follows the finger at a quarter
   * of the speed, up to 1.1 times its width, from its outer edge in both open modes; with `false` it stops at its
   * width. Pulling past the width goes before `animationType`.
   * @default true
   */
  shouldStretchDrawer?: boolean;
  /**
   * (iOS) The speed in points per second with which the menu moves to its open or closed place. A menu released
   * faster than this moves at the speed of the finger, and the move takes 0.1 seconds at the least. A value of `0` or
   * less counts as the default.
   * @default 840
   */
  animationVelocity?: number;
  /**
   * (iOS) Where the menu is relative to the center. `'aboveContent'` slides the menu over the center, which darkens
   * under it. `'pushContent'` keeps the menu under the center, which moves aside by the width of the menu and casts a
   * shadow (see `sideMenu.animationType` for how the menu itself appears). An unknown string means `'aboveContent'`. A
   * change applies at once, to an open menu too. Android always draws the menu over the center.
   * @default 'aboveContent'
   */
  openMode?: 'aboveContent' | 'pushContent';
  /**
   * (iOS 26+) Whether a Liquid Glass layer fills the menu's container, under its screen. The glass shows through only
   * where the screen is transparent, so the screen needs `layout.backgroundColor` and `layout.componentBackgroundColor`
   * both `'transparent'` and a root view with no background. With `'aboveContent'` the center shows through the glass. Below iOS 26 and on Android it
   * does nothing. A change applies at once.
   * @default false
   */
  glass?: boolean;
  /**
   * The test identifier of this side's container; while the menu is open, the dimming over the center is
   * `<testID>.scrim`. When it is not set, the identifiers derive from `sideMenu.testID`.
   */
  testID?: string;
}

/**
 * How a swipe opens a menu (iOS): `'bezel'` from a strip of 20 points along the edge of the side, `'entireScreen'`
 * from anywhere on the center, where the direction of the move picks the side. An unknown string means `'bezel'`.
 */
export type SideMenuOpenGestureMode = 'bezel' | 'entireScreen';

/**
 * (iOS) How the menu itself moves while the center slides aside in `'pushContent'` mode. `'parallax'` moves it from a
 * point half its width outside to its place; `'slide'` moves it along with the center; `'slide-and-scale'` fades it in
 * while it grows from 0.9 to 1 and shifts by 50 points; `'door'` swings it open like a door around the edge next to
 * the center.
 */
export type SideMenuAnimationType = 'parallax' | 'slide' | 'slide-and-scale' | 'door';

/**
 * The options of a side menu: a center with a menu on the left, on the right or on both sides. They are set in the
 * options of the side menu, in the options of the screens inside it (through `mergeOptions` or the screen definition)
 * and in the default options; a screen outside a side menu is not affected. A `null` in place of a nested object counts
 * as an empty object, and a value of the wrong type is skipped without a crash. While a menu is open, the `sideMenu`
 * options of the center's visible screen still count.
 */
export interface OptionsSideMenu {
  /** The left menu. */
  left?: SideMenuSide;
  /** The right menu. */
  right?: SideMenuSide;
  /**
   * (iOS) Where a swipe has to start to open a menu. Closing by a gesture works from anywhere in both modes, and the
   * swipe back of a stack goes before the menu: on a screen that can go back, the leading edge goes back and doesn't
   * open a menu. In `'entireScreen'` mode a horizontal scroll in the center goes first while it can still scroll in the
   * direction of the move; a horizontal pan that isn't a native scroll view, such as a carousel of a gesture library,
   * doesn't. A change applies at once. Android's edge is the one of the Material drawer, and it doesn't
   * read this.
   * @default 'bezel'
   */
  openGestureMode?: SideMenuOpenGestureMode;
  /**
   * (iOS) How the menu moves in `'pushContent'` mode while the center goes aside. Without it, the menu stands still and
   * the center moves. It does nothing in `'aboveContent'` mode. A change applies at once. Android doesn't read it.
   */
  animationType?: SideMenuAnimationType;
  /**
   * The test identifier of the side menu's container (on iOS its view, on Android the drawer). The parts derive from
   * it: `<testID>.center`, `<testID>.left`, `<testID>.right`, and on iOS `<testID>.scrim` for the dimming over the
   * center that a tap on closes the menu. Android draws the dimming inside the drawer without a view of its own, and a
   * tap on `<testID>.center` closes the menu in the same way. The button that opens the menu is an ordinary button of
   * the top bar with a `testID` of its own.
   */
  testID?: string;
}
