import { useContext, useEffect, useLayoutEffect, useRef, useSyncExternalStore } from 'react';
import { takeButtonHandlers } from '../../engine/buttons';
import { mergeOptions } from '../../engine/commands';
import type { Options } from '../../interfaces/Options';
import { holdLeave } from '../commands/commands';
import { placeOf } from '../../engine/places';
import { onScreenTabPress, type TabPressHandler } from '../events/tabPresses';
import {
  type ButtonPressedEvent,
  type NavigationEventName,
  type NavigationEvents,
  onNavigationEvent,
  type ScreenDidAppearEvent,
  type ScreenDidDisappearEvent,
  type SearchCancelledEvent,
  type SearchChangedEvent,
  type SearchSubmittedEvent,
} from '../events/navigationEvents';

/** The events a screen hears about itself: those that carry the screen, and the move of the accessory it shows. */
export type ScreenEventName =
  | {
      [Name in NavigationEventName]: NavigationEvents[Name] extends { screen: unknown }
        ? Name
        : never;
    }[NavigationEventName]
  | 'accessoryPlacementChanged';
import { readScreenOptions, ScreenOptions } from '../options/ScreenOptions';
import {
  getNavigationState,
  hasAppeared,
  type NavigationState,
  onNavigationStateChange,
} from '../state/navigationState';
import { ScreenContext, type ScreenInfo } from '../screens/registerScreens';

// The ids the native side sends a held back with: the back button of the bar, the system back of Android.
const BACK_BUTTON = 'ApexNavigation.back';
const HARDWARE_BACK = 'ApexNavigation.hardwareBackButton';

// What holds every way the user leaves a screen for another screen of the app, as the native side reads it: back
// pops no more, a swipe down leaves the modal in place; each try comes as an event instead. The first screen of a
// stack has no back button: held, iOS would draw one.
function holdOptions(hold: boolean, first: boolean): Options {
  return {
    ...(!first && { topBar: { backButton: { popStackOnPress: !hold } } }),
    hardwareBackButton: { popStackOnPress: !hold, dismissModalOnPress: !hold },
    modal: { swipeToDismiss: !hold },
  } as Options;
}

/**
 * While `prevent` is true, keeps the user on this screen: the back button, the swipe back and the history menu (iOS),
 * the system back (Android), the swipe down of the modal the screen is in and its close button call `onAttempt`
 * instead, so the screen can ask. The commands of the app are not held: `goBack()` after the user agreed leaves.
 * Leaving the app is not held either: on the first screen of the root the system back of Android leaves it, as the
 * home gesture does on iOS; to ask before that, React Native's `BackHandler`.
 */
export function usePreventLeave(prevent: boolean, onAttempt: () => void): void {
  const { id } = useScreen();
  const latest = useRef(onAttempt);
  useLayoutEffect(() => {
    latest.current = onAttempt;
  });
  useLayoutEffect(() => {
    mergeOptions(id, holdOptions(prevent, placeOf(id)?.index === 0));
    return prevent ? holdLeave(id, () => latest.current()) : undefined;
  }, [id, prevent]);
  useScreenEvent('buttonPressed', ({ buttonId }) => {
    if (prevent && (buttonId === BACK_BUTTON || buttonId === HARDWARE_BACK)) latest.current();
  });
  useScreenEvent('modalDismissAttempted', () => {
    if (prevent) latest.current();
  });
}

/** The screen the component is in. */
export function useScreen(): ScreenInfo {
  const screen = useContext(ScreenContext);
  if (!screen) {
    throw new Error(
      '[navigation] This hook is for the screens of createNavigation, and the component is not in one'
    );
  }
  return screen;
}

/**
 * An event of the screen the component is in, by its name in `listeners` and `navigation.on`: only this screen's. The
 * handler can change on every render.
 *
 * @example
 * useScreenEvent('sheetDetentChanged', ({ detent }) => {
 *   setExpanded(detent === 'large');
 * });
 */
export function useScreenEvent<Name extends ScreenEventName>(
  name: Name,
  handler: (event: NavigationEvents[Name]) => void
): void {
  const { id } = useScreen();
  const latest = useRef(handler);
  useLayoutEffect(() => {
    latest.current = handler;
  });
  useEffect(
    () =>
      onNavigationEvent(name, (event) => {
        // The accessory's event names its component, which is this screen when the accessory renders the hook.
        const about = event as { screen?: { id: string }; componentId?: string };
        if ((about.screen?.id ?? about.componentId) === id) latest.current(event);
      }),
    [name, id]
  );
}

/** Each time the screen appears. */
export function useScreenAppear(handler: (event: ScreenDidAppearEvent) => void): void {
  useScreenEvent('screenDidAppear', handler);
}

/** Each time the screen disappears: something covers it, or it leaves. */
export function useScreenDisappear(handler: (event: ScreenDidDisappearEvent) => void): void {
  useScreenEvent('screenDidDisappear', handler);
}

/**
 * Whether a shown screen is in the layer the user sees: the state's screen is in its layer (the root, or its modal). An
 * overlay is over every layer. A sheet over the screen is a layer over it, though the screen doesn't disappear then.
 */
function inTopLayer(screenId: string): boolean {
  const place = placeOf(screenId);
  const shown = getNavigationState().screen;
  const top = shown && placeOf(shown.id);
  return !place || place.container === 'overlay' || !top || top.layer === place.layer;
}

/**
 * Runs the effect each time the screen gets the focus, and its cleanup when it loses it or unmounts: a refresh, a
 * subscription, a video that plays only while the screen shows. The screen has the focus while it shows and nothing
 * covers it: a modal or a sheet over it takes the focus away, and its closing gives it back. A component mounted while
 * its screen has the focus runs it at once; mounted under a sheet, once the sheet goes.
 */
export function useFocusEffect(effect: () => void | (() => void)): void {
  const { id: componentId } = useScreen();
  const latest = useRef(effect);
  useLayoutEffect(() => {
    latest.current = effect;
  });
  useEffect(() => {
    let appeared = hasAppeared(componentId);
    let running = false;
    let cleanup: void | (() => void);
    const update = () => {
      const focused = appeared && inTopLayer(componentId);
      if (focused === running) return;
      running = focused;
      if (focused) {
        cleanup = latest.current();
      } else {
        cleanup?.();
        cleanup = undefined;
      }
    };
    const stops = [
      onNavigationEvent('screenDidAppear', ({ screen }) => {
        if (screen.id !== componentId) return;
        appeared = true;
        update();
      }),
      onNavigationEvent('screenDidDisappear', ({ screen }) => {
        if (screen.id !== componentId) return;
        appeared = false;
        update();
      }),
      onNavigationStateChange(update),
    ];
    update();
    return () => {
      stops.forEach((stop) => stop());
      if (running) cleanup?.();
    };
  }, [componentId]);
}

/**
 * What the user sees: the screen and the selected tab. The component renders again when the selected part changes, so
 * select only what it needs.
 */
export function useNavigationState(): NavigationState;
export function useNavigationState<Selected>(
  select: (state: NavigationState) => Selected
): Selected;
export function useNavigationState<Selected>(
  select?: (state: NavigationState) => Selected
): Selected | NavigationState {
  return useSyncExternalStore(onNavigationStateChange, () =>
    select ? select(getNavigationState()) : getNavigationState()
  );
}

/** The screen the component is in is the one the user sees. */
export function useIsFocused(): boolean {
  const { id: componentId } = useScreen();
  return useNavigationState((state) => state.screen?.id === componentId);
}

/** A press on a button of the screen's top bar or toolbar. */
export function useNavigationButton(
  buttonId: string,
  handler: (event: ButtonPressedEvent) => void
): void {
  useScreenEvent('buttonPressed', (event) => {
    if (event.buttonId === buttonId) handler(event);
  });
}

/** The search bar of the screen. */
export function useSearchBar(handlers: {
  onChange?: (event: SearchChangedEvent) => void;
  onSubmit?: (event: SearchSubmittedEvent) => void;
  onCancel?: (event: SearchCancelledEvent) => void;
  /** (iOS 16+, Android) A suggestion of `searchBar.suggestions` is picked, by its `id`; the text stays. */
  onSuggestion?: (suggestionId: string) => void;
}): void {
  useScreenEvent('searchChanged', (event) => handlers.onChange?.(event));
  useScreenEvent('searchSubmitted', (event) => handlers.onSubmit?.(event));
  useScreenEvent('searchCancelled', (event) => handlers.onCancel?.(event));
  useScreenEvent('searchSuggestionPicked', (event) => handlers.onSuggestion?.(event.suggestionId));
}

/**
 * Options of the screen from its state, such as a title from loaded data or a button that turns on. They are built on
 * each render and applied before the screen paints when they changed; buttons take `onPress`, and a new handler takes
 * the press at once.
 */
export function useScreenOptions(build: (options: ScreenOptions) => ScreenOptions): void {
  const { id: componentId } = useScreen();
  const sent = useRef<string | undefined>(undefined);
  useLayoutEffect(() => {
    const options = takeButtonHandlers(componentId, readScreenOptions(build(new ScreenOptions())));
    const key = JSON.stringify(options);
    if (key === sent.current) return;
    sent.current = key;
    mergeOptions(componentId, options);
  });
}

/** Listens to an event of the whole navigation while the component is mounted. */
export function useNavigationEvent<Name extends NavigationEventName>(
  name: Name,
  handler: (event: NavigationEvents[Name]) => void
): void {
  const latest = useRef(handler);
  useLayoutEffect(() => {
    latest.current = handler;
  });
  useEffect(() => onNavigationEvent(name, (event) => latest.current(event)), [name]);
}

/**
 * Called when the tab of this screen is pressed while the tab shows it; `wasSelected` tells a second press, on which
 * apps scroll to the top. The handler can change on every render.
 */
export function useTabPress(handler: TabPressHandler): void {
  const { id: componentId } = useScreen();
  const latest = useRef(handler);
  useLayoutEffect(() => {
    latest.current = handler;
  });
  useEffect(() => onScreenTabPress(componentId, (event) => latest.current(event)), [componentId]);
}
