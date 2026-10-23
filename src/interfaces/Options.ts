// The options of screens and containers, as the native side reads them: a JSON tree with only the keys that are set.
// Each area keeps its types in a file of its own under `options/`; this file puts them together and lists the public
// ones (each is in a section of `docs/sections.json`).
import type { AnimationOptions } from './options/animations';
import type { OptionsBottomTab, OptionsBottomTabs } from './options/bottomTabs';
import type { Color } from './options/common';
import type {
  ModalOptions,
  OptionsModalPresentationStyle,
  OptionsModalTransitionStyle,
  OverlayOptions,
} from './options/modals';
import type { PreviewOptions } from './options/preview';
import type { OptionsSideMenu } from './options/sideMenu';
import type { OptionsSplitView } from './options/splitView';
import type { HardwareBackButtonOptions, OptionsFab } from './options/stack';
import type {
  NavigationBarOptions,
  OptionsKeyCommand,
  OptionsLayout,
  OptionsStatusBar,
} from './options/system';
import type { OptionsToolbar } from './options/toolbar';
import type { OptionsScrollEdgeEffect, OptionsTopBar } from './options/topBar';

/**
 * The options of a screen or a container. A screen shows its own options over those of its containers (its stack, its
 * tabs, its side menu), and all of them over the default options of the app. Objects merge key by key, arrays and
 * values replace what was there, and a key left out keeps what the layers below give.
 */
export interface Options {
  /** The bar at the top of a screen in a stack: its title, buttons, back button, search and look. */
  topBar?: OptionsTopBar;
  /** (iOS) The toolbar at the bottom of a screen in a stack. */
  toolbar?: OptionsToolbar;
  /** (iOS 26+) How the content fades or blurs where it scrolls under a bar. */
  scrollEdgeEffect?: OptionsScrollEdgeEffect;
  /** The tab bar of the tabs the screen is in, while the screen shows. */
  bottomTabs?: OptionsBottomTabs;
  /** The tab of a screen that is a tab of a tab bar (or the first screen of a stack that is one). */
  bottomTab?: OptionsBottomTab;
  /** A screen shown as a modal: how it is presented, how it can be dismissed, its sheet. */
  modal?: ModalOptions;
  /** How a modal is presented: full screen, as a page sheet, a form sheet, a popover… */
  modalPresentationStyle?: OptionsModalPresentationStyle;
  /** (iOS) The system transition of a modal: cover vertical, flip horizontal, cross dissolve, partial curl. */
  modalTransitionStyle?: OptionsModalTransitionStyle;
  /** A screen shown as an overlay: whether touches outside its content go through to what is below. */
  overlay?: OverlayOptions;
  /** The side menus of the side menu the screen is in: their visibility, width, gestures. */
  sideMenu?: OptionsSideMenu;
  /** The split view the screen is in: its columns, their widths, how they show. */
  splitView?: OptionsSplitView;
  /** The screen's layout: its background, safe area, orientation. */
  layout?: OptionsLayout;
  /** The status bar while the screen shows. */
  statusBar?: OptionsStatusBar;
  /** (Android) The navigation bar while the screen shows. */
  navigationBar?: NavigationBarOptions;
  /** The transitions of the commands: push, pop, modals, the root, and elements that move between screens. */
  animations?: AnimationOptions;
  /**
   * (iOS) The swipe back in the stack: `true` from the edge and, on iOS 26, from anywhere in the content; `'edge'`
   * only from the edge, so horizontal gestures of the content (sliders, carousels) keep theirs; `false` no swipe, the
   * back button still works. The top screen's value applies each time it shows, and at once through `mergeOptions`.
   * Before iOS 26 `'edge'` is the same as `true`. Android has the system back instead and doesn't read it.
   * @default true
   */
  popGesture?: boolean | 'edge';
  /** (Android) What the system back button and the back gesture do on the screen. */
  hardwareBackButton?: HardwareBackButtonOptions;
  /** (Android) The floating action button of the screen. */
  fab?: OptionsFab;
  /** (iOS) The keyboard shortcuts of the screen, shown by the system when a hardware keyboard is held. */
  keyCommands?: OptionsKeyCommand[];
  /**
   * Whether a text field of the screen loses the focus, and the keyboard hides, as the screen leaves: when the
   * transition that takes it away starts (a pop, a modal's dismiss, a new stack root or app root), so the keyboard
   * doesn't stay up during the transition.
   * @default false
   */
  blurOnUnmount?: boolean;
  /**
   * The preview of a pushed screen, for a view of the screen it is pushed from (iOS). Set by `usePreview`; apps
   * don't set it.
   * @internal
   */
  preview?: PreviewOptions;
}

/** (iOS) The app's window, behind every screen: seen around a modal while it comes in. */
export interface WindowOptions {
  /** The color of the window; without it, the system background. */
  backgroundColor?: Color;
}

export type { AnimationOptions } from './options/animations';
export type {
  AppearingElementAnimation,
  DisappearingElementAnimation,
  ElementAnimation,
  ElementTransition,
  EnterExitAnimationOptions,
  Interpolation,
  ModalAnimationOptions,
  OptionsAnimationPropertyConfig,
  ScreenAnimationOptions,
  SharedElementTransition,
  StackAnimationOptions,
  StackPopAnimationOptions,
  StackPushAnimationOptions,
  TopBarAnimationOptions,
  ViewAnimationOptions,
  ZoomTransitionOptions,
} from './options/animations';
export type {
  BottomTabsCustomRowOptions,
  DotIndicatorOptions,
  OptionsBottomTab,
  OptionsBottomTabs,
  OptionsBottomTabsAccessory,
  ShadowOptions,
} from './options/bottomTabs';
export type {
  IconBackgroundOptions,
  IconInsets,
  OptionsTopBarButton,
  OptionsTopBarButtonMenu,
  OptionsTopBarButtonMenuElement,
  OptionsTopBarButtonMenuItem,
  OptionsTopBarButtonSubmenu,
  SystemItemIcon,
} from './options/buttons';
export type { Color, ImageResource, PlatformIcon } from './options/common';
export type {
  ModalOptions,
  OptionsModalPopover,
  OptionsModalPresentationStyle,
  OptionsModalSheet,
  OptionsModalSheetDetent,
  OptionsModalTransitionStyle,
  OverlayOptions,
} from './options/modals';
export type { OptionsSideMenu, SideMenuSide } from './options/sideMenu';
export type { OptionsSplitView, OptionsSplitViewColumn } from './options/splitView';
export type { HardwareBackButtonOptions, OptionsFab } from './options/stack';
export type {
  LayoutOrientation,
  NavigationBarOptions,
  OptionsKeyCommand,
  OptionsLayout,
  OptionsStatusBar,
  SafeAreaEdge,
} from './options/system';
export type {
  OptionsToolbar,
  OptionsToolbarSearchField,
  OptionsToolbarSpace,
} from './options/toolbar';
export type {
  OptionsScrollEdge,
  OptionsScrollEdgeEffect,
  OptionsSearchBar,
  OptionsSearchBarScope,
  OptionsSearchBarSuggestion,
  OptionsSearchBarToken,
  OptionsTopBar,
  OptionsTopBarBackButton,
  OptionsTopBarBackground,
  OptionsTopBarLargeTitle,
  OptionsTopBarScrollEdgeAppearance,
  OptionsTopBarScrollEdgeAppearanceBackground,
  OptionsTopBarScrollEdgeAppearanceTitle,
  OptionsTopBarSubtitle,
  OptionsTopBarTitle,
} from './options/topBar';
