import { type ScreenEventOf, toScreenEvent } from '../../engine/events';
import { onNativeEvent } from '../../engine/native';
import type {
  BottomAccessoryPlacementChangedEvent,
  ComponentDidAppearEvent,
  ComponentDidDisappearEvent,
  ComponentWillAppearEvent,
  KeyCommandPressedEvent as NativeKeyCommandPressedEvent,
  ModalAttemptedToDismissEvent,
  ModalDismissedEvent as NativeModalDismissedEvent,
  NavigationButtonPressedEvent,
  PreviewCompletedEvent as NativePreviewCompletedEvent,
  ScreenPoppedEvent as NativeScreenPoppedEvent,
  SearchBarCancelPressedEvent,
  SearchSuggestionPickedEvent as NativeSearchSuggestionPickedEvent,
  SearchBarSubmittedEvent,
  SearchBarUpdatedEvent,
  SheetDetentChangedEvent as NativeSheetDetentChangedEvent,
  SplitViewDisplayModeChangedEvent as NativeSplitViewDisplayModeChangedEvent,
  TitleRenamedEvent as NativeTitleRenamedEvent,
} from '../../interfaces/ComponentEvents';
import type {
  BottomTabLongPressedEvent,
  BottomTabPressedEvent,
  BottomTabSelectedEvent,
} from '../../interfaces/Events';
import { type NavigationState, onNavigationStateChange } from '../state/navigationState';

/** The events of the navigation by name, with what each one gets; the events of a screen carry it as `screen`. */
export interface NavigationEvents {
  /** A screen is about to appear; the components of the bars (title, buttons, background) are not screens. */
  screenWillAppear: ScreenEventOf<ComponentWillAppearEvent>;
  /** A screen appeared. */
  screenDidAppear: ScreenEventOf<ComponentDidAppearEvent>;
  /** A screen disappeared: something covers it, or it left. */
  screenDidDisappear: ScreenEventOf<ComponentDidDisappearEvent>;
  /** A screen left its stack by back, a swipe or `pop`. */
  screenPopped: ScreenEventOf<NativeScreenPoppedEvent>;
  buttonPressed: ScreenEventOf<NavigationButtonPressedEvent>;
  tabSelected: BottomTabSelectedEvent;
  tabPressed: BottomTabPressedEvent;
  tabLongPressed: BottomTabLongPressedEvent;
  modalDismissed: ScreenEventOf<NativeModalDismissedEvent>;
  /** A swipe tried to dismiss a modal whose screen keeps the user (`usePreventLeave`). */
  modalDismissAttempted: ScreenEventOf<ModalAttemptedToDismissEvent>;
  searchChanged: ScreenEventOf<SearchBarUpdatedEvent>;
  searchSubmitted: ScreenEventOf<SearchBarSubmittedEvent>;
  searchCancelled: ScreenEventOf<SearchBarCancelPressedEvent>;
  /** #### (iOS 16+, Android) The user picked a suggestion of `searchBar.suggestions`; the text stays. */
  searchSuggestionPicked: ScreenEventOf<NativeSearchSuggestionPickedEvent>;
  /** #### (iOS specific) */
  sheetDetentChanged: ScreenEventOf<NativeSheetDetentChangedEvent>;
  /** #### (iOS specific) */
  splitViewDisplayModeChanged: NativeSplitViewDisplayModeChangedEvent;
  keyCommandPressed: ScreenEventOf<NativeKeyCommandPressedEvent>;
  /** #### (iOS specific) */
  titleRenamed: ScreenEventOf<NativeTitleRenamedEvent>;
  /** #### (iOS specific, iOS 26+) */
  accessoryPlacementChanged: BottomAccessoryPlacementChangedEvent;
  /** #### (iOS specific) */
  previewCompleted: ScreenEventOf<NativePreviewCompletedEvent>;
  /** What the user sees changed: the screen or the selected tab. */
  stateChanged: NavigationState;
}

export type NavigationEventName = keyof NavigationEvents;

// The events by the names of their types, as the app imports them.
export type ScreenWillAppearEvent = NavigationEvents['screenWillAppear'];
export type ScreenDidAppearEvent = NavigationEvents['screenDidAppear'];
export type ScreenDidDisappearEvent = NavigationEvents['screenDidDisappear'];
export type ScreenPoppedEvent = NavigationEvents['screenPopped'];
export type ButtonPressedEvent = NavigationEvents['buttonPressed'];
export type TabSelectedEvent = NavigationEvents['tabSelected'];
export type TabPressedEvent = NavigationEvents['tabPressed'];
export type TabLongPressedEvent = NavigationEvents['tabLongPressed'];
export type ModalDismissedEvent = NavigationEvents['modalDismissed'];
export type ModalDismissAttemptedEvent = NavigationEvents['modalDismissAttempted'];
export type SearchChangedEvent = NavigationEvents['searchChanged'];
export type SearchSubmittedEvent = NavigationEvents['searchSubmitted'];
export type SearchCancelledEvent = NavigationEvents['searchCancelled'];
export type SearchSuggestionPickedEvent = NavigationEvents['searchSuggestionPicked'];
export type SheetDetentChangedEvent = NavigationEvents['sheetDetentChanged'];
export type SplitViewDisplayModeChangedEvent = NavigationEvents['splitViewDisplayModeChanged'];
export type KeyCommandPressedEvent = NavigationEvents['keyCommandPressed'];
export type TitleRenamedEvent = NavigationEvents['titleRenamed'];
export type AccessoryPlacementChangedEvent = NavigationEvents['accessoryPlacementChanged'];
export type PreviewCompletedEvent = NavigationEvents['previewCompleted'];

// The events of a screen: the native side names it by its id.
const SCREEN_EVENTS = new Set<NavigationEventName>([
  'screenWillAppear',
  'screenDidAppear',
  'screenDidDisappear',
  'screenPopped',
  'buttonPressed',
  'modalDismissed',
  'modalDismissAttempted',
  'searchChanged',
  'searchSubmitted',
  'searchCancelled',
  'searchSuggestionPicked',
  'sheetDetentChanged',
  'keyCommandPressed',
  'titleRenamed',
  'previewCompleted',
]);

/** Handlers of the events of the navigation, by event name. */
export type NavigationListeners = {
  [Name in NavigationEventName]?: (event: NavigationEvents[Name]) => void;
};

// The native event behind each event of the navigation; `stateChanged` comes from the kept state.
const NATIVE_EVENTS: { [Name in Exclude<NavigationEventName, 'stateChanged'>]: string } = {
  screenWillAppear: 'ComponentWillAppear',
  screenDidAppear: 'ComponentDidAppear',
  screenDidDisappear: 'ComponentDidDisappear',
  screenPopped: 'ScreenPopped',
  buttonPressed: 'NavigationButtonPressed',
  tabSelected: 'BottomTabSelected',
  tabPressed: 'BottomTabPressed',
  tabLongPressed: 'BottomTabLongPressed',
  modalDismissed: 'ModalDismissed',
  modalDismissAttempted: 'ModalAttemptedToDismiss',
  searchChanged: 'SearchBarUpdated',
  searchSubmitted: 'SearchBarSubmitted',
  searchCancelled: 'SearchBarCancelPressed',
  searchSuggestionPicked: 'SearchSuggestionPicked',
  sheetDetentChanged: 'SheetDetentChanged',
  splitViewDisplayModeChanged: 'SplitViewDisplayModeChanged',
  keyCommandPressed: 'KeyCommandPressed',
  titleRenamed: 'TitleRenamed',
  accessoryPlacementChanged: 'BottomAccessoryPlacementChanged',
  previewCompleted: 'PreviewCompleted',
};

// The appearance of screens; the native side tells the components of the bars appearing too, with a type of their own.
const SCREEN_APPEARANCE = new Set<NavigationEventName>([
  'screenWillAppear',
  'screenDidAppear',
  'screenDidDisappear',
]);

/** Listens to an event of the navigation; returns the function that stops listening. */
export function onNavigationEvent<Name extends NavigationEventName>(
  name: Name,
  handler: (event: NavigationEvents[Name]) => void
): () => void {
  // From the kept state, so a handler reads the same state as getState().
  if (name === 'stateChanged') {
    return onNavigationStateChange(handler as (state: NavigationState) => void);
  }
  const deliver = handler as (event: unknown) => void;
  const nativeName = NATIVE_EVENTS[name as keyof typeof NATIVE_EVENTS];
  if (SCREEN_APPEARANCE.has(name)) {
    return onNativeEvent<{ componentId: string; componentType: string }>(nativeName, (event) => {
      if (event.componentType === 'Component') deliver(toScreenEvent(event));
    });
  }
  if (SCREEN_EVENTS.has(name)) {
    return onNativeEvent<{ componentId: string }>(nativeName, (event) =>
      deliver(toScreenEvent(event))
    );
  }
  return onNativeEvent(nativeName, deliver);
}

/** Listens to the events of `listeners`, for as long as the app runs. */
export function addNavigationListeners(listeners: NavigationListeners) {
  for (const name of Object.keys(listeners) as NavigationEventName[]) {
    const handler = listeners[name];
    if (handler) onNavigationEvent(name, handler as (event: NavigationEvents[typeof name]) => void);
  }
}
