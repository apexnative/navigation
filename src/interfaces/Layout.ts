// The layout tree as the public builders (`Root`, `Stack`, `Tabs`, `Screen`, `SideMenu`, `SplitView`) produce it and
// as the app can also write it by hand. The engine turns it into the JSON nodes the native side reads
// (`src/engine/layout.ts`); these types are not that JSON.
import type { Options } from './Options';

/**
 * A screen of the app, a React Native one: the name it has in `screens`, the props it gets, and the options of this
 * show.
 */
export interface LayoutComponent {
  /**
   * The id of the screen, to address it in commands and events. Without it, the library gives the screen one. An id
   * names one screen in the whole app at a time: a layout that gives it twice, or a command that shows a screen
   * under the id of one that is already shown elsewhere, is refused.
   */
  id?: string;
  /** The name of the screen in `screens`. A name that is not there is refused before anything is sent. */
  name: string;
  /**
   * The props the screen gets. They stay in JavaScript, under the id of the screen; the native side never sees them.
   */
  passProps?: object;
  /** The options of this show of the screen, over its own options and under those of its containers. */
  options?: Options;
}

/**
 * A screen the app creates in native code and registered under a name. The native side makes it through the creator
 * registered for the name.
 */
export interface ExternalComponent {
  /** The id of the screen. Without it, the library gives the screen one. */
  id?: string;
  /** The name the screen is registered under on the native side; it need not be a name in `screens`. */
  name: string;
  /**
   * The props the native screen gets, as JSON: functions are dropped from the data (the native screen calls them by
   * name), `undefined` values are left out and dates become strings.
   */
  passProps?: object;
  /** The options of the screen: its bar, its buttons, as for a React screen. */
  options?: Options;
}

/**
 * A stack of screens. The last child shows; going back reveals the one before it. A stack takes screens only, a
 * container inside a stack is refused.
 */
export interface LayoutStack {
  /** The id of the stack. Without it, the library gives the stack one. */
  id?: string;
  /**
   * The screens from the bottom up. A stack without screens is refused by the native side. The screens under the
   * last one are created at once, but they are shown and drawn only when they are reached.
   */
  children?: LayoutStackChildren[];
  /** The options of the stack: defaults for its screens, such as their bar or their animations. */
  options?: Options;
}

/**
 * What a stack holds: a screen, React Native or native. It is a layout that sets one of the two screen keys.
 */
export type LayoutStackChildren = Pick<Layout, 'component' | 'externalComponent'>;

/**
 * A set of bottom tabs. Each child is a tab. The tab bar button is built from the `bottomTab` options of the child
 * and of its screens.
 */
export interface LayoutBottomTabs {
  /**
   * The id of the tabs, named by `bottomTabsId` in the tab events. Without it, the library gives the tabs one. The
   * commands that act on a tab take the id of the tab itself, not this one.
   */
  id?: string;
  /**
   * The tabs in order: the index of a child is the index of its tab in events and options. A stack, a screen, or a
   * side menu fits a tab. Android shows at most 5 tabs and refuses a layout with more; iOS shows the tabs after the
   * fourth under "More" on an iPhone. Tabs without children are refused by the native side.
   */
  children?: Layout[];
  /** The options of the tabs, such as the tab to show first (`bottomTabs.currentTabIndex`, `currentTabId`). */
  options?: Options;
}

/**
 * A side menu: a center and, at its sides, up to two menus.
 */
export interface LayoutSideMenu {
  /** The id of the side menu. Without it, the library gives the menu one. */
  id?: string;
  /** The content of the menu at the left (leading) edge. Without it, there is no menu on that side. */
  left?: Layout;
  /** The content that the menus belong to. A side menu cannot be built without it. */
  center: Layout;
  /** The content of the menu at the right (trailing) edge. Without it, there is no menu on that side. */
  right?: Layout;
  /** The options of the side menu: its visibility, widths and gestures. */
  options?: Options;
}

/**
 * A split view: columns side by side, or one over the other when the window is narrow. `master` and `detail` are
 * required; a layout without them is refused.
 */
export interface LayoutSplitView {
  /** The id of the split view. Without it, the library gives the split view one. */
  id?: string;
  /** The primary column. */
  master: Layout;
  /** The secondary column, the one that shows the content of the item chosen in the primary. */
  detail: Layout;
  /** (iOS) The middle column. A split view with it has three columns. */
  supplementary?: Layout;
  /**
   * What an iPhone, a narrow window and a narrow Android screen show in place of the columns. Without it, the
   * columns collapse by the rules of the platform.
   */
  compact?: Layout;
  /** (iPadOS 26+) The column at the trailing edge. Android has no such column and ignores it. */
  inspector?: Layout;
  /** The options of the split view: its display mode, column widths and buttons. */
  options?: Options;
}

/**
 * One node of a layout: exactly one of its keys is set, and it names the kind of the node. A layout with none of them
 * is refused before it is sent.
 */
export interface Layout {
  /** A React Native screen. */
  component?: LayoutComponent;
  /** A screen created in native code. */
  externalComponent?: ExternalComponent;
  /** A stack of screens. */
  stack?: LayoutStack;
  /** Bottom tabs. */
  bottomTabs?: LayoutBottomTabs;
  /** A side menu. */
  sideMenu?: LayoutSideMenu;
  /** A split view. */
  splitView?: LayoutSplitView;
}

/**
 * The layout of the whole app: what `setRoot` takes. A modal or an overlay is not a kind of node: any node becomes
 * one when it is listed under `modals` or `overlays`.
 */
export interface LayoutRoot {
  /** The root of the app. It replaces the previous one. */
  root: Layout;
  /**
   * The modals to show over the root once it is in place, in order, each over the one before. Only the last one is
   * animated; the ones under it come at once. Without it, none.
   */
  modals?: Layout[];
  /**
   * The overlays to show after the modals, one by one. Overlays shown before the new root stay. Without it, none.
   */
  overlays?: Layout[];
}
