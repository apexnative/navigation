// The events the native side sends that are not tied to one screen's content, or that name a container, as the
// JavaScript side receives them (`ANN.<Name>`). The events of a screen are in `ComponentEvents.ts`.

/**
 * The native side is ready for JavaScript to start the app. It carries no data. JavaScript answers by setting the
 * default options and the root. It is sent after each bundle load, after a reload, and (Android) for each new
 * activity.
 */
export type AppLaunchedEvent = Record<string, never>;

/** The commands that report their completion with `CommandCompletedEvent`. */
export type CompletedCommandName =
  | 'setRoot'
  | 'push'
  | 'pop'
  | 'popTo'
  | 'popToRoot'
  | 'setStackRoot'
  | 'showInColumn'
  | 'showModal'
  | 'dismissModal'
  | 'dismissAllModals'
  | 'showOverlay'
  | 'dismissOverlay'
  | 'dismissAllOverlays';

/**
 * A command finished successfully. It is sent after the promise of the command resolved, never for a command that was
 * refused. The library itself does not listen to it: it is for external tools, such as end-to-end tests and latency
 * measurements.
 */
export interface CommandCompletedEvent {
  /** The name of the command. */
  commandName: CompletedCommandName;
  /** The id of this call of the command. */
  commandId: string;
  /** When the command finished, in milliseconds since the Unix epoch. */
  completionTime: number;
}

/**
 * What the user sees changed. It is sent only when the state differs from the last one sent, and before the promise of
 * a command that changed it resolves.
 */
export interface NavigationStateChangedEvent {
  /**
   * The visible screen with its name in `screens`, or `null` when there is none. In a container the visible screen is
   * the one it shows.
   */
  screen: { id: string; name: string } | null;
  /** The index of the selected tab of the nearest tabs around the visible screen, or `null` outside tabs. */
  tab: number | null;
}

/**
 * A tab was selected by the user: a press on the tab, a pick from the "More" list or a press on "More" that showed a
 * tab (iOS). It is sent after the selection, and only when the tab changed; a press on the current tab is reported by
 * `BottomTabPressedEvent` alone. Selecting a tab from code sends nothing.
 */
export interface BottomTabSelectedEvent {
  /** The index of the tab that is now selected. */
  selectedTabIndex: number;
  /** The index of the tab that was selected before. */
  unselectedTabIndex: number;
  /** The id of the tabs. */
  bottomTabsId: string;
}

/**
 * A tab was pressed, the selected one too. It is sent before the tab is selected; with `selectTabOnPress: false` it is
 * the only thing that happens. A long press sends `BottomTabLongPressedEvent` instead.
 */
export interface BottomTabPressedEvent {
  /** The index of the tab that was pressed. */
  tabIndex: number;
  /** The id of the tabs. */
  bottomTabsId: string;
  /** The id of the screen the tab shows: the top of its stack. */
  screenId?: string;
  /** Whether the tab was the selected one already; a press on the selected tab scrolls its content to the top. */
  wasSelected: boolean;
}

/**
 * A tab was pressed and held. The tab is not selected and gets no press event. On iOS it is not sent for the tabs
 * that are components (`bottomTab.component`, `bottomTabs.customRow`), for "More", or for the tab strip at the top of an
 * iPad. On Android it replaces the tooltip of the tab.
 */
export interface BottomTabLongPressedEvent {
  /** The index of the tab that was pressed. */
  selectedTabIndex: number;
  /** The id of the tabs. */
  bottomTabsId: string;
}

/**
 * Whether the columns of a split view are shown as one stack, one over the other: on iOS a split view collapsed
 * without a compact layout, on Android the sliding pane. It is sent when the split view enters the window or is first
 * laid out, and on each change.
 */
export interface SplitViewColumnsStackedEvent {
  /** The id of the split view. */
  componentId: string;
  /** Whether the columns are shown as one stack. */
  stacked: boolean;
}

/**
 * (iOS) A link the app dispatched (`dispatchDeepLinkURL`) that React Native's `Linking` doesn't take from the native
 * side: on React Native 0.88 and newer, in an app whose Info.plist names a scene delegate, it takes only the links a
 * scene opened. JavaScript hands it to `Linking` as a `url` event.
 */
export interface DeepLinkDispatchedEvent {
  /** The link. */
  url: string;
}

/**
 * A screen of the single React tree was destroyed and the slot still belongs to it: JavaScript removes the slot from
 * its tree. It is not sent for a prepared screen that was never pushed, nor when JavaScript reloads.
 */
export interface SlotDestroyedEvent {
  /** The id of the screen that owned the slot. */
  componentId: string;
}

/**
 * The distances by which the bars and the system cover the content of a screen on each side, in points on iOS and dp
 * on Android: what the safe area view of the screen did not already take off. It is sent after the screen is laid out
 * and on each change, never twice with the same values. The same values are read synchronously with
 * `getScreenInsets`.
 */
export interface ScreenInsetsChangedEvent {
  /** The id of the screen. */
  componentId: string;
  /** The distance at the top. */
  top: number;
  /** The distance at the right. */
  right: number;
  /** The distance at the bottom. */
  bottom: number;
  /** The distance at the left. */
  left: number;
}

/** A rectangle in the coordinates of a screen. */
export interface ScreenRegionRect {
  /** The distance of its left edge from the left edge of the screen. */
  x: number;
  /** The distance of its top edge from the top edge of the screen. */
  y: number;
  /** Its width. */
  width: number;
  /** Its height. */
  height: number;
}

/**
 * The parts of a screen that the device divides or covers, only those inside the screen. It is sent when they change.
 * The same values are read synchronously with `getScreenRegions`. On iOS it needs iOS 27.1; Android takes the folds
 * from the window.
 */
export interface ScreenRegionsChangedEvent {
  /** The id of the screen. */
  componentId: string;
  /** The folds, without margins. */
  division: ScreenRegionRect[];
  /** The areas covered by a camera or anything else that hides part of the screen. */
  occlusion: ScreenRegionRect[];
}

/**
 * How the screens of a pair are shown. It is sent for a screen of a pair when this changes. The same state is read
 * synchronously with `getArrangementState`.
 */
export interface ArrangementChangedEvent {
  /** The id of the screen of the pair. */
  componentId: string;
  /** Which part of the pair the screen is. */
  placement: 'primary' | 'secondary';
  /** How the pair lies: side by side, one over the other, one over the other as an overlay, or only one of them. */
  layout: 'sideBySide' | 'stacked' | 'overlay' | 'single';
  /** Whether the screen is in front. */
  front: boolean;
  /** Whether the screen is hidden. */
  hidden: boolean;
}

/** The state of the hinge of a foldable device. */
export type HingeState = 'closed' | 'partiallyOpen' | 'fullyOpen' | 'unknown';

/**
 * The hinge of the device changed. It is sent only while JavaScript has turned hinge updates on, each value once. An
 * empty event means the device has no hinge, or it is no longer known.
 */
export interface HingeChangedEvent {
  /**
   * The state of the hinge. On Android `closed` means the device has no fold, or its angle is below 90 degrees.
   */
  state?: HingeState;
  /**
   * The angle of the hinge in degrees (on iOS to tenths). Missing when the state is `unknown` and where the system
   * gives no angle.
   */
  angle?: number;
}

/**
 * A native screen calls into JavaScript: a function among its props, or a navigation command made from the screen.
 * JavaScript answers with `resolveNativeScreenCall`, with a value or an error. A call made while the navigation is not
 * running fails at once; one pending when JavaScript reloads fails with `JavaScript reloaded`.
 */
export interface NativeScreenCallEvent {
  /** The id of the native screen that makes the call. */
  componentId: string;
  /** The id of the call, to answer it. */
  callId: string;
  /** What is called: a function of the props, or a navigation command. */
  kind: 'function' | 'command';
  /**
   * The name of the function, or of the command: `push`, `pop`, `popTo`, `popToRoot`, `showInColumn`, `showModal`,
   * `dismiss`, `showOverlay`, `setOptions`, `selectTab` or `setTabBadge`.
   */
  name: string;
  /** The arguments of the call. */
  args: unknown[];
}

/**
 * A native screen left the layout, however it left: back, a swipe, a closed modal, a new root, or (Android) the
 * activity going away. It is sent once; JavaScript lets go of the hook and the functions of the screen. It is not sent
 * when JavaScript reloads.
 */
export interface NativeScreenReleasedEvent {
  /** The id of the native screen. */
  componentId: string;
}
