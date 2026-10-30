import type { IconInsets } from './buttons';
import type { Color, FontFamily, FontStyle, FontWeight, ImageResource } from './common';

/**
 * The shadow of the tab bar. Without the object, Android draws no shadow and iOS leaves the shadow of the bar as it is.
 * On iOS a field left out keeps the current value of the bar's layer.
 */
export interface ShadowOptions {
  /** The opacity of the shadow, from 0 to 1. On Android it is the alpha of the shadow color. */
  opacity?: number;
  /**
   * The color of the shadow. Without it, iOS keeps the current color and Android draws black. On Android the color
   * follows the light and dark appearance.
   */
  color?: Color;
  /** The blur radius of the shadow. On Android the shadow is 10 plus this value. */
  radius?: number;
}

/**
 * The dot at the top right corner of the icon of a tab. It shows only when `visible` is set: `color` and `size` alone
 * switch nothing on. A badge takes the place of the dot while the tab has one. On merging, the fields merge one by one
 * on both platforms.
 */
export interface DotIndicatorOptions {
  /**
   * The color of the dot. Without it, the dot is red on iOS and has the error color of the theme on Android. On iOS 26
   * it applies to the selected tab only; the dots of the other tabs stay system red. A change of the appearance
   * recolors it.
   */
  color?: Color;
  /**
   * The diameter of the dot. It applies on iOS before 26 only; on iOS 26 and on Android the system decides the size.
   * @default 6
   */
  size?: number;
  /**
   * Whether the dot shows. It stays hidden while the tab has a badge. On iOS 26 the dot is an empty badge of the tab,
   * and it does not show on the top bar of an iPad. A tab that sits in the "More" list of an iPhone has no dot.
   * @default false
   */
  visible?: boolean;
}

/**
 * A component drawn as the item of a tab. The native side creates it as a React root with the props `componentId`,
 * `tabIndex`, `selected`, `badge`, `dot` and, when the tab sets a dot color, `dotColor`; `passProps` stay in
 * JavaScript.
 */
export interface OptionsBottomTabComponent {
  /** The name of the registered screen that draws the item. */
  name: string;
  /** The props of the item at creation. The native updates of `selected`, `badge` and `dot` arrive on top of them. */
  passProps?: object;
}

/**
 * The component of the accessory above the tab bar. It is a React root created with an id; its `passProps` stay in
 * JavaScript and the native side never gets them.
 */
export interface OptionsBottomTabsAccessoryComponent {
  /** The name of the registered screen that draws the accessory. Without it, there is no accessory. */
  name?: string;
  /**
   * The id of the component. The accessory placement event carries it as `componentId`, to tell accessories apart.
   * Without it, an id is generated.
   */
  id?: string;
  /** The props of the component. */
  passProps?: object;
}

/**
 * (iOS 26+) The accessory above the tab bar, like the mini player of a music app. The system sizes it (48 points high)
 * and the component fills that size. It is placed above the bar, or beside the bar when the bar is minimized; on an
 * iPad with the bar at the top it sits at the bottom over the full width, always above the bar. A hidden accessory
 * (`visible: false`, or no name) keeps its React root and its state, and shows again with them. When another accessory
 * (another `componentId`) shows, the replaced one stops once it has left the window and its state is gone. The last
 * accessory stops together with the tabs. Android has no accessory.
 */
export interface OptionsBottomTabsAccessory {
  /**
   * The component of the accessory. A new `componentId` replaces the accessory; the one already shown changes nothing.
   * Merging `{ visible }` alone keeps the component.
   */
  component?: OptionsBottomTabsAccessoryComponent;
  /**
   * Whether the accessory shows. `false` hides it and keeps the component in the options; in `mergeOptions` the change
   * animates. At creation it never animates.
   * @default true
   */
  visible?: boolean;
  /** The test identifier of the container of the accessory, visible to end-to-end tests. */
  testID?: string;
}

/**
 * The custom row: a row of tab items drawn by components instead of the system bar. It applies only when every tab has
 * `bottomTab.component`. The shape is the same on both platforms; the row merges field by field, takes the default
 * options and the theme into account, and each set of tabs has its own. `mergeOptions` changes it at once.
 */
export interface BottomTabsCustomRowOptions {
  /**
   * The height of the content of the row, without the safe area and `bottomMargin`, in points or dp. By default: on iOS
   * 26 at the bottom, the height of the system capsule (62 points, 44 in a compact height); on a wide iPad with iOS 26,
   * the height of the bar plus 18; before iOS 26, the height of the bar; on Android, the height of the bar plus 18 dp.
   * A taller row grows upward from its bottom and the screens get the part it covers as a bottom inset.
   */
  height?: number;
  /**
   * The solid background of the row. It wins over `backgroundEffect`. `null` counts as no color. The color follows the
   * light and dark appearance.
   */
  backgroundColor?: Color;
  /**
   * The effect behind the cells when there is no `backgroundColor`: `'glass'`, `'blur'` or `'none'` (transparent). On
   * iOS 26 `'glass'` is the system glass and it is the default; before iOS 26 `'glass'` is a blur, which is the default
   * there too. On Android `'none'` is transparent and the others draw the translucent background color of the theme, as
   * Android has no real blur.
   */
  backgroundEffect?: 'glass' | 'blur' | 'none';
  /**
   * The corner radius of the row. By default: a capsule (half the height) on iOS 26 at the bottom, 28 on a wide iPad
   * with iOS 26, 0 before iOS 26, 28 dp on Android.
   */
  cornerRadius?: number;
  /**
   * The distance of the row from the sides. By default: 21 on iOS 26 at the bottom, 16 on a wide iPad with iOS 26, 0
   * before iOS 26, and 16 dp from the edges of the bar on Android.
   */
  horizontalMargin?: number;
  /**
   * The distance from the bottom of the row to the bottom safe area (to the bottom of the capsule on iOS 26). It lifts
   * the row once.
   * @default 0
   */
  bottomMargin?: number;
  /** The test identifier of the whole row, visible to end-to-end tests. */
  testID?: string;
}

/**
 * The options of the tab bar. They come from the options of the tabs over the options of the screen that the selected
 * tab shows, over the default options, and apply while that screen shows.
 */
export interface OptionsBottomTabs {
  /**
   * Whether the bar shows. A screen on top decides: one with `visible: false` hides the bar while it is on top, and
   * every screen pushed over it has to say `false` too, or the bar comes back. `mergeOptions` applies at once. A hidden
   * bar means the screens draw under it, and the custom row hides with it.
   * @default true
   */
  visible?: boolean;
  /**
   * Whether a change of `visible` animates. On iOS the first application never animates. On Android, `false` on the
   * screen that appears turns off the bar's animation during a push, pop or stack root change.
   * @default true
   */
  animate?: boolean;
  /**
   * How the content changes when another tab is selected on screen, by a press, by back or from code. `'platform'`
   * does what the platform does: on Android the tab that goes fades out and the new one fades in and grows (Material's
   * fade through, 300 ms), on iOS the content changes at once. `'none'` changes it at once on both platforms. The
   * appear events of a switch and `ANN.BottomTabSelected` come at its end, so with `'none'` right away. The tab that is
   * selected decides: the value of the screen it shows, over its own, the tabs' and the default options. A
   * `mergeOptions` applies from the next switch. Off screen the content always changes at once.
   * @default 'platform'
   */
  switchAnimation?: 'platform' | 'none';
  /**
   * The tab to select, as an index from 0. It selects once, when the options arrive, at creation (the first tab to
   * show) or in `mergeOptions`; it is not kept. An index out of range selects nothing (or the first tab at creation)
   * and logs a warning. It sends no press or select events. Without it, the first tab shows. `currentTabId` wins when
   * both are set.
   */
  currentTabIndex?: number;
  /**
   * The tab to select, by id. It matches a tab, or any screen or layout inside it. It works once, like
   * `currentTabIndex`, and wins over it. An unknown id selects the first tab at creation and nothing in `mergeOptions`.
   */
  currentTabId?: string;
  /**
   * The identifier of the bar for end-to-end tests: the accessibility identifier on iOS, the tag and the resource name
   * of the navigation view on Android.
   */
  testID?: string;
  /** (iOS) The accessibility label of the bar, which VoiceOver reads. It does nothing on Android. */
  accessibilityLabel?: string;
  /**
   * (iOS) The test identifier of the "More" button, which the system shows for more tabs than fit. The system draws the
   * list behind it.
   */
  moreTestID?: string;
  /**
   * Whether the screens of the tabs draw under the bar. On iOS 26 it is on by default (glass), on iOS before 26 and on
   * Android it is off. On iOS a translucent or hidden bar also counts as drawn under. On Android it sets the bottom
   * padding of the screens to 0, while their safe area still counts a shown bar; it fits well with `layoutStyle:
   * 'compact'`. It applies when a screen appears and in `mergeOptions`.
   */
  drawBehind?: boolean;
  /**
   * The background color of the bar; `null` gives the default one. On Android the default is the surface container color
   * (`colorSurfaceContainer`) of the Material 3 theme. Before iOS 26, and in the compatibility mode (`UIDesignRequiresCompatibility` in Info.plist: iOS 26, and iOS 27
   * only for an app built with an SDK before iOS 27), the bar is opaque with the color, or system opaque without it,
   * and a transparent color makes it transparent. On iOS 26 and later with glass the color is ignored
   * with one warning, since the system has no color for glass, while a transparent color gives a transparent bar. The
   * theme color `bar` is its default option.
   */
  backgroundColor?: Color;
  /**
   * When the screens of the tabs are created: `'together'` creates every tab at the start, and the tabs are ready when
   * all of them are; `'afterInitialTab'` creates the selected tab first and the others right after it;
   * `'onSwitchToTab'` creates a tab the first time it is selected. The screens of tabs not yet selected get no appear
   * events in any mode. On iOS a tab created before it shows has its screen's React root mounted, and the screen's
   * component renders the first time the tab shows; on Android it renders as the tab is created. It applies when the
   * tabs are created; `mergeOptions` does not change it.
   * @default 'together'
   */
  tabsAttachMode?: 'together' | 'afterInitialTab' | 'onSwitchToTab';
  /**
   * (iOS) The blur style of a translucent bar before iOS 26: `'default'` or `'black'`. It needs `translucent: true`,
   * and applies only when set. On iOS 26 the system decides.
   */
  barStyle?: 'default' | 'black';
  /**
   * (Android) The width of the bar. `'stretch'` fills the width; `'compact'` makes a floating bar of 80 dp for each
   * tab, centered above the navigation bar, and `'stretch'` after `'compact'` brings the full width back.
   * @default 'stretch'
   */
  layoutStyle?: 'stretch' | 'compact';
  /**
   * (Android) The corner radius of the bar's container, in dp. Without it, the container is not clipped and its outline
   * follows the background. It works best with `layoutStyle: 'compact'`.
   */
  cornerRadius?: number;
  /**
   * (Android) The distance from the bottom of the screen to the bar, in dp, for a floating bar. The screens' bottom
   * padding counts it. With `layoutStyle: 'compact'` the height of the navigation bar is added. It works best with
   * `layoutStyle: 'compact'` and `drawBehind`.
   */
  bottomMargin?: number;
  /**
   * (iOS) Whether the bar is translucent, with the system blur. It applies before iOS 26, where the screens then draw
   * under the bar; on iOS 26 (glass) it does nothing. Android has no blur, so it does nothing there.
   */
  translucent?: boolean;
  /**
   * (iOS) Whether the hairline at the top of the bar is removed, by clipping the bar's view to its bounds. The glass
   * bar of iOS 26 has no line, and the clip can cut the shadow of its capsule. Android has no such line; its border is
   * `borderColor` and `borderWidth`.
   */
  hideShadow?: boolean;
  /**
   * The labels under the icons: `'alwaysShow'` shows all, `'showWhenActive'` and `'showWhenActiveForce'` show the label
   * of the selected tab only, `'alwaysHide'` shows none. On iOS there are two states, show and hide: `'alwaysHide'`
   * leaves empty titles and centers the icons (and the "More" list and the iPad top bar show icons only), and the other
   * values show the titles. On Android, without it the Material automatic mode applies: all labels up to three tabs,
   * the selected one from four, and all labels when no tab has an icon. `mergeOptions` changes it on both platforms.
   */
  titleDisplayMode?: 'alwaysShow' | 'showWhenActive' | 'alwaysHide' | 'showWhenActiveForce';
  /** (Android) The elevation of the bar's container, in dp. Its shadow shows even without `cornerRadius`. */
  elevation?: number;
  /**
   * (Android) Whether the bar hides when the content of the screen scrolls down and comes back on a scroll up. The bar
   * slides down together with what lies under it, in 200 ms, and the screen then draws under it, so the screen's safe
   * area does not keep the bottom without `layout.safeAreaEdges`. It follows a new screen after a push, and turning it
   * off brings a hidden bar back. The scrolling view must be the root of the screen and have `nestedScrollEnabled`. iOS
   * ignores it; `minimizeBehavior` on iOS 26 is the nearest thing.
   * @default false
   */
  hideOnScroll?: boolean;
  /**
   * The color of the border at the top of the bar. Before iOS 26 it draws a line in the shadow image of every tab
   * (black if only `borderWidth` is set); on iOS 26 with glass it does not show. On Android it shows a line, dark gray
   * by default, and the theme color `border` is its default option.
   */
  borderColor?: Color;
  /**
   * The thickness of the border at the top of the bar: points on iOS (0.1 when only the color is set), dp on Android (a
   * thin line when only the color is set). It works with `borderColor`.
   */
  borderWidth?: number;
  /** The shadow of the bar. */
  shadow?: ShadowOptions;
  /**
   * The custom row, a row of component cells drawn in place of the bar. It needs `bottomTab.component` on every tab.
   */
  customRow?: BottomTabsCustomRowOptions;
  /**
   * (iOS 18+) How the tabs show on an iPad: `'automatic'` shows the bar, `'tabBar'` only the bar, `'sidebar'` adds a
   * sidebar button (the sidebar lists every tab, over the screen in a narrow window and beside it in a wide one). It
   * applies at creation and in `mergeOptions`, but not while the user customizes the tabs (`customizationId`), and with
   * a custom row `'sidebar'` falls back to the bar with a warning. On an iPhone the bar shows; on an unfolded iPhone
   * Duo (iOS 27+) `'sidebar'` shows the sidebar. Android ignores it.
   * @default 'automatic'
   */
  sidebar?: 'automatic' | 'tabBar' | 'sidebar';
  /**
   * (iOS 18+) Where the screen sits while the sidebar is beside it: `'tile'` to the right of the sidebar, `'overlap'`
   * under it (only the safe area moves, which React Native does not follow by itself). It applies at creation and in
   * `mergeOptions`.
   * @default 'tile'
   */
  sidebarLayout?: 'tile' | 'overlap';
  /**
   * (iOS 18+) Lets the user customize the tabs on an iPad with `sidebar: 'sidebar'`: Edit in the sidebar reorders,
   * hides and adds the tabs. The system saves the arrangement under this id and the tab ids, so the id must be unique
   * in the app and the tabs need stable ids. Without it, or empty, the tabs keep the layout order and are not
   * customizable. It reads at creation only.
   */
  customizationId?: string;
  /**
   * (iOS 26+) When the bar shrinks to the selected tab as content scrolls: `'automatic'` leaves it to the system (on
   * iPhone 26.5 it does not shrink), `'never'`, `'onScrollDown'`, `'onScrollUp'`. With a custom row it stays `'never'`
   * with a warning. It applies at creation and in `mergeOptions`.
   * @default 'automatic'
   */
  minimizeBehavior?: 'automatic' | 'never' | 'onScrollDown' | 'onScrollUp';
  /** (iOS 26+) The accessory above the tab bar. */
  bottomAccessory?: OptionsBottomTabsAccessory;
}

/**
 * The options of the item of a tab. They come from the layout of the tab over the options of its current screen, over
 * the default options. `mergeOptions` with any field on the id of a tab, or of a screen inside it, changes that tab; on
 * iOS the item is built again from the final options.
 */
export interface OptionsBottomTab {
  /**
   * The dot at the corner of the icon. It shows only with `visible`, and a badge takes its place. `mergeOptions` of the
   * dot or the badge applies at once.
   */
  dotIndicator?: DotIndicatorOptions;
  /**
   * A component drawn as the item of the tab, instead of the icon and the label. It works only when every tab has one;
   * if only some do, a warning is logged and all tabs draw natively. It is read at creation, so a change through
   * `mergeOptions` does nothing.
   */
  component?: OptionsBottomTabComponent;
  /**
   * The label of the tab. On iOS it is the title of the item (and of the tab on iOS 18+); with `titleDisplayMode:
   * 'alwaysHide'` it does not show, and a system role before iOS 18 keeps the label of the system.
   */
  text?: string;
  /**
   * The text of the badge on the icon. An empty string or `null` removes the badge, and `'0'` shows "0". The badge
   * keeps through tab switches and a change of the appearance. With a custom row it is passed to the cell as `badge`.
   */
  badge?: string | null;
  /**
   * The background color of the badge. On iOS before 26 it is the color in the normal state; on iOS 26 it applies to
   * the selected tab only, and the other tabs stay system red. On Android it is the badge color, the error color of the
   * theme by default. The theme color `badge` is its default option.
   */
  badgeColor?: Color;
  /**
   * (Android) Whether the badge shows with an animation. It does nothing: the Material 3 badges are not animated. The
   * option stays only for compatibility.
   */
  animateBadge?: boolean;
  /**
   * The identifier of the tab for end-to-end tests: on iOS the accessibility identifier of the item, of the tab and of
   * the button in the bar; on Android the tag and the resource name of the item. With a custom row it sits on the cell
   * of the row.
   */
  testID?: string;
  /**
   * The icon of the tab: an image, or `{ ios, android }` (an SF Symbol on iOS, a drawable of the app on Android). A
   * platform missing from the object has no icon. On iOS a bitmap is drawn in its own colors, or tinted when
   * `iconColor` is set, while an SF Symbol wins over a bitmap and is drawn by the system in the color of the state. On
   * Android the icon is tinted by the colors.
   */
  icon?: ImageResource;
  /**
   * The color of the icon. On iOS it is blended into a bitmap icon (an SF Symbol takes the color of the state, and iOS
   * 26 ignores it for an unselected tab). On Android it is the color of an unselected icon over the style of the bar,
   * and `null` keeps the color of the style. Without it, the icon keeps its own colors on iOS and takes the theme on
   * Android. Use `disableIconTint` for a multicolor icon.
   */
  iconColor?: Color;
  /** (Android) The width of the icon in dp. The icon is square and the width wins over `iconHeight`. */
  iconWidth?: number;
  /** (Android) The height of the icon in dp, used when no `iconWidth` is set. */
  iconHeight?: number;
  /**
   * The color of the label of an unselected tab. On iOS it is the label color by default, and iOS 26 does not apply it
   * to an unselected tab. On Android, without it the theme gives the color.
   */
  textColor?: Color;
  /**
   * The color of the icon of the selected tab; `iconColor` by default. The theme color `primary` is its default option.
   */
  selectedIconColor?: Color;
  /**
   * The color of the label of the selected tab; the label color on iOS by default. The theme color `primary` is its
   * default option.
   */
  selectedTextColor?: Color;
  /** The font family of the label. */
  fontFamily?: FontFamily;
  /** The slant of the label font. */
  fontStyle?: FontStyle;
  /** The weight of the label font; the nearest available weight is used. */
  fontWeight?: FontWeight;
  /**
   * The size of the label. On iOS it is 10 by default and applies to both states; on Android it is the size of an
   * unselected label, in sp.
   */
  fontSize?: number;
  /** (Android) The size of the label of the selected tab, in sp. */
  selectedFontSize?: number;
  /**
   * (iOS) The insets of the icon. Each side is independent. They apply before iOS 26 only; iOS 26 and the compatibility
   * mode (see `backgroundColor`) ignore them, and so does a system role before iOS 18.
   */
  iconInsets?: IconInsets;
  /**
   * The icon of the selected tab; without it, `icon` shows (the SF Symbol of it, if there is one). On iOS with the tab
   * API the selected image applies from iOS 26.1 and is tinted by the system. On Android it is the checked state of the
   * icon.
   */
  selectedIcon?: ImageResource;
  /**
   * Whether the icon keeps its own colors in the normal state: `iconColor` does not apply. On iOS 18+ the system still
   * draws an SF Symbol in the color of the state. On Android the icon of an unselected tab gets no tint, neither from
   * `iconColor` nor from the style of the bar.
   * @default false
   */
  disableIconTint?: boolean;
  /**
   * Whether the selected icon keeps its own colors: `selectedIconColor` does not apply (on iOS 18+ a selected SF Symbol
   * takes the tint of the bar). On Android the selected icon gets no tint, neither from `selectedIconColor` nor from
   * the style of the bar.
   * @default false
   */
  disableSelectedIconTint?: boolean;
  /**
   * Whether a tap selects the tab. `false` only sends the press event and keeps the current tab; this is how a button
   * tab works. It reads at the moment of the tap, with the default options.
   * @default true
   */
  selectTabOnPress?: boolean;
  /**
   * Whether a tap on the selected tab goes back to the first screen of its stack, or scrolls the first screen to the
   * top when it is already there. `false` does nothing beyond the press event. It reads at the moment of the tap.
   * @default true
   */
  popToRootOnPress?: boolean;
  /**
   * Whether leaving the tab, by a tap or from code, returns its stack to the first screen without animation or events.
   * It reads at the moment of leaving.
   * @default false
   */
  popToRootOnBlur?: boolean;
  /**
   * (iOS) The system role of the item. On iOS 18+ `'search'` gives the system search tab (a separate glass button on
   * iOS 26 that gives the bar to the search field when selected); it keeps its system title and image unless `text` and
   * `icon` are set, only one tab may have it (a second one becomes a normal tab with a warning), and it is pinned.
   * Other roles give a normal tab with its own `text` and icon. Before iOS 18 a role is the system item, with the
   * system icon and label: `icon` and `selectedIcon` replace the system image, and `text`, fonts and `iconInsets` do
   * not apply; an unknown role becomes a normal tab with a warning. With a custom row it is a normal tab.
   */
  role?:
    | 'search'
    | 'bookmarks'
    | 'contacts'
    | 'downloads'
    | 'favorites'
    | 'featured'
    | 'history'
    | 'more'
    | 'mostRecent'
    | 'mostViewed'
    | 'recents'
    | 'topRated';
  /**
   * (iOS 18+) Where the tab sits when the user customizes the tabs (`bottomTabs.customizationId`): `'fixed'` stays in
   * place, `'movable'` can be reordered but not removed, `'optional'` can be reordered, removed and brought back from
   * the sidebar, `'pinned'` stays at the end of the bar, `'sidebarOnly'` shows in the sidebar only. The search tab is
   * pinned by default. Without customization every tab is fixed. It reads at creation only.
   * @default 'movable'
   */
  customization?: 'fixed' | 'movable' | 'optional' | 'pinned' | 'sidebarOnly';
  /**
   * (iOS 18+) Whether an `'optional'` tab shows in the bar before the user customizes; `false` keeps it in the sidebar
   * until the user adds it. It reads at creation only.
   * @default true
   */
  shownByDefault?: boolean;
  /**
   * (iOS 18+) Whether the user may hide the tab in Edit of the sidebar (needs `bottomTabs.customizationId`). A hidden
   * selected tab stays open until another is selected, and code can still select it. It reads at creation only.
   * @default false
   */
  allowsHiding?: boolean;
}
