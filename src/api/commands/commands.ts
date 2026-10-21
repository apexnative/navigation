import { takeButtonHandlers } from '../../engine/buttons';
import type { NavigationButton } from '../options/buttons';
import * as engine from '../../engine/commands';
import { nextId } from '../../engine/ids';
import { propsOf } from '../../engine/props';
import { afterLoading, screenOptions } from '../../engine/screens';
import { withSlots } from '../../engine/slots';
import type { Layout } from '../../interfaces/Layout';
import type {
  AnimationOptions,
  ModalOptions,
  Options,
  OptionsModalPresentationStyle,
} from '../../interfaces/Options';
import { getActiveApp } from '../app/activeApp';
import { whenScreenUnmounts } from '../events/screenLifecycle';
import { mergeLayoutOptions } from '../layout/element';
import { stackChild, type StackEntry } from '../layout/Stack';
import { badgeText } from '../layout/Tabs';
import { mergeOptions } from '../../engine/mergeOptions';
import { buildScreenOptions, type ScreenOptionsInput } from '../screens/defineScreen';
import { coversColumn, placeOf } from '../../engine/places';
import { command, placeFor } from './errors';
import type { ColumnConfig, ModalConfig, OverlayConfig, PushConfig } from './presentation';
import { defined, modalAnimations, pushAnimations } from './transitions';

// The id and testID of the close button of a modal.
const CLOSE_BUTTON = 'CloseButton';

// The modals shown by `showModal`, for the ids of their first screens.
let modals = 0;

// The settings of a command with the props of the screen it shows.
type WithProps<Config> = Config & { props?: object };

// `replace` comes in with the push's animation, not that of `setStackRoot` (Android's fade through).
const AS_PUSH = { animations: { setStackRoot: { asPush: true } } } as Options;

// An overlay shows the screens below it where its content is not.
const OVERLAY_OPTIONS: Options = {
  layout: { backgroundColor: 'transparent', componentBackgroundColor: 'transparent' },
};

// A command that shows a lazy screen waits for its module: the screen's own options come from it.

export function push(
  fromId: string,
  name: string,
  config: WithProps<PushConfig> = {}
): Promise<string> {
  return command('push', () => {
    placeFor('push', fromId, { stack: true });
    return afterLoading([name], () => pushNow(fromId, name, config));
  });
}

function pushNow(fromId: string, name: string, config: WithProps<PushConfig>): Promise<string> {
  const { props } = config;
  const base = getActiveApp()?.presentation?.push;
  const options = showOptions(name, props, {}, base?.options, config.options, {
    app: pushAnimations(base?.transition),
    call: pushAnimations(config.transition),
  });
  return withSlots(() => engine.push(fromId, screenLayout(name, props, options)));
}

/**
 * A push that waits as the preview of a view of the screen (`usePreview`): a tap on the preview pushes it. The screen
 * has the given id, so the presses of its menu reach the hook. A preview that is no longer `wanted` when its push is
 * about to go out (the component unmounted while the screen loaded) isn't sent: its `dismissPreview` came before it.
 */
export function pushPreview(
  fromId: string,
  name: string,
  props: object | undefined,
  preview: object,
  id: string,
  wanted: () => boolean
): Promise<string> {
  return command('push', () => {
    placeFor('push', fromId, { stack: true });
    return afterLoading([name], () => {
      // Unmounted while the module of the screen loaded: nothing is built for it.
      if (!wanted()) throw new Error('[navigation] push: the preview went before it was shown');
      const options = mergeOptions(showOptions(name, props, {}, undefined, undefined), {
        preview,
      } as Options);
      return withSlots(() => engine.push(fromId, screenLayout(name, props, options, id), wanted));
    });
  });
}

export function pop(fromId: string): Promise<string> {
  return command('pop', () => {
    // The first screen of a column over the column before it: the native side goes back to that column.
    if (!coversColumn(fromId)) placeFor('pop', fromId, { below: true });
    return engine.pop(fromId);
  });
}

export function popTo(screenId: string): Promise<string> {
  return command('popTo', () => {
    placeFor('popTo', screenId, { stack: true });
    return engine.popTo(screenId);
  });
}

export function popToRoot(fromId: string): Promise<string> {
  return command('popToRoot', () => {
    placeFor('popToRoot', fromId, { stack: true });
    return engine.popToRoot(fromId);
  });
}

export function setStack(fromId: string, entries: StackEntry[]): Promise<string> {
  return command('setStack', () => {
    placeFor('setStack', fromId, { stack: true });
    return withSlots(() => engine.setStackRoot(fromId, entries.map(stackChild)));
  });
}

/**
 * The screen alone in the stack of the screen it is made from: the history of the stack goes, so there is no back. It
 * comes in as a push does; resolves with its id.
 */
export function replace(
  fromId: string,
  name: string,
  config: WithProps<PushConfig> = {}
): Promise<string> {
  return command('replace', () => {
    placeFor('replace', fromId, { stack: true });
    return afterLoading([name], () => {
      const { props } = config;
      const base = getActiveApp()?.presentation?.push;
      const options = showOptions(name, props, AS_PUSH, base?.options, config.options, {
        app: pushAnimations(base?.transition),
        call: pushAnimations(config.transition),
      });
      const id = nextId('Component');
      return withSlots(() =>
        engine.setStackRoot(fromId, [screenLayout(name, props, options, id)])
      ).then(() => id);
    });
  });
}

export function showInColumn(
  fromId: string,
  name: string,
  config: WithProps<ColumnConfig> = {}
): Promise<string> {
  return command('showInColumn', () => {
    placeFor('showInColumn', fromId);
    return afterLoading([name], () => {
      const { props, column = 'secondary' } = config;
      const options = showOptions(name, props, {}, undefined, config.options);
      return withSlots(() =>
        engine.showInColumn(fromId, column, screenLayout(name, props, options))
      );
    });
  });
}

/** A column of a split view that the app shows and hides. */
export type SplitColumn = 'primary' | 'supplementary' | 'inspector';

/** Shows or hides a column of the split view the screen is in; resolves once the columns have moved. */
export function setColumnShown(fromId: string, column: SplitColumn, shown: boolean): Promise<void> {
  const name = shown ? 'showColumn' : 'hideColumn';
  return command(name, () => {
    placeFor(name, fromId);
    return engine.setColumnShown(fromId, column, shown);
  });
}

export function showModal(name: string, config: WithProps<ModalConfig> = {}): Promise<string> {
  return command('showModal', () => afterLoading([name], () => showModalNow(name, config)));
}

function showModalNow(name: string, config: WithProps<ModalConfig>): Promise<string> {
  const { props, ...call } = config;
  const base = getActiveApp()?.presentation?.modal ?? {};
  // The screen's options come in layers; the rest of the settings merge, the command's over the app's.
  const settings = mergeOptions(
    { ...base, options: undefined, transition: undefined },
    { ...call, options: undefined, onDismiss: undefined, transition: undefined }
  );
  // The first screen gets an id of its own: the close button and `onDismiss` go by it.
  const screenId = `ModalScreen${++modals}`;
  let options = showOptions(name, props, {}, base.options, call.options, {
    app: modalAnimations(base.transition),
    call: modalAnimations(call.transition),
  });
  if (settings.closeButton && settings.stack !== false)
    options = withCloseButton(options, screenId);
  if (call.onDismiss) whenScreenUnmounts(screenId, call.onDismiss);
  const screen = screenLayout(name, props, options, screenId);
  const presentation = presentationOptions(settings);
  const layout: Layout =
    settings.stack === false
      ? mergeLayoutOptions(screen, presentation)
      : { stack: { children: [screen], options: presentation } };
  return withSlots(() => engine.showModal(layout));
}

export function dismissModal(modalId: string): Promise<string> {
  return command('dismissModal', () => engine.dismissModal(modalId));
}

export function dismissAllModals(): Promise<string> {
  return command('dismissAllModals', () => engine.dismissAllModals());
}

export function showOverlay(name: string, config: WithProps<OverlayConfig> = {}): Promise<string> {
  return command('showOverlay', () => afterLoading([name], () => showOverlayNow(name, config)));
}

function showOverlayNow(name: string, config: WithProps<OverlayConfig>): Promise<string> {
  const { props, ...call } = config;
  const base = getActiveApp()?.presentation?.overlay ?? {};
  const settings = { ...base, ...call };
  const options = showOptions(name, props, OVERLAY_OPTIONS, base.options, call.options);
  const overlay = defined({ interceptTouchOutside: settings.blockTouches });
  const layout = screenLayout(name, props, mergeOptions(options, { overlay }));
  return withSlots(() => engine.showOverlay(layout));
}

export function dismissOverlay(overlayId: string): Promise<string> {
  return command('dismissOverlay', () => engine.dismissOverlay(overlayId));
}

/**
 * There is a screen under this one in its stack, or it is the first of a split view column shown over the column
 * before it.
 */
export function canPop(screenId: string): boolean {
  const place = placeOf(screenId);
  return (place !== undefined && place.stack !== null && place.index > 0) || coversColumn(screenId);
}

/** The screen is in a modal or an overlay. */
export function canDismiss(screenId: string): boolean {
  const place = placeOf(screenId);
  return place !== undefined && place.container !== 'root';
}

/**
 * What the back button would do on the screen: back to the screen under it in its stack, or else it closes the modal
 * or the overlay it is in.
 */
export function goBack(screenId: string): Promise<string> {
  if (canPop(screenId)) return pop(screenId);
  if (canDismiss(screenId)) return dismiss(screenId);
  return command('goBack', () => {
    const place = placeFor('goBack', screenId);
    throw new Error(
      `[navigation] goBack: ${place?.name ?? screenId} is the first screen of the root, there is nothing to go back to`
    );
  });
}

/** Closes the modal or the overlay the screen is in. */
export function dismiss(screenId: string): Promise<string> {
  return command('dismiss', () =>
    placeFor('dismiss', screenId, { dismissible: true })?.container === 'overlay'
      ? engine.dismissOverlay(screenId)
      : engine.dismissModal(screenId)
  );
}

export function dismissAllOverlays(): Promise<string> {
  return command('dismissAllOverlays', () => engine.dismissAllOverlays());
}

/** Selects a tab of the tabs the screen is in: by its index, or by the id of the tab. */
export function selectTab(screenId: string, tab: number | string): void {
  engine.mergeOptions(screenId, {
    bottomTabs: typeof tab === 'number' ? { currentTabIndex: tab } : { currentTabId: tab },
  });
}

/** The badge of the tab the screen is in, or of the tab with the id; `null` and `0` take it away. */
export function setTabBadge(screenId: string, badge: string | number | null, tab?: string): void {
  engine.mergeOptions(tab ?? screenId, { bottomTab: { badge: badgeText(badge) } });
}

/** A chain of the options gets the props of the screen and `{ theme, context }`, as in `defineScreen`. */
export function setOptions(screenId: string, options: ScreenOptionsInput<object>): void {
  engine.mergeOptions(
    screenId,
    takeButtonHandlers(screenId, buildScreenOptions(options, propsOf(screenId)))
  );
}

/**
 * The options a screen shows with: those of the kind of show, of the app's presentation, the screen's own and those
 * of this show. The engine merges the screen's own again under these, which changes nothing.
 */
function showOptions(
  name: string,
  props: object | undefined,
  base: Options,
  presentation: ScreenOptionsInput<object> | undefined,
  show: ScreenOptionsInput<object> | undefined,
  transitions: { app?: AnimationOptions; call?: AnimationOptions } = {}
): Options {
  const screenProps = props ?? {};
  // A transition is options too: the app's goes under the screen's own options, that of the command over them.
  return [
    buildScreenOptions(presentation, screenProps),
    animationsOf(transitions.app),
    screenOptions(name, screenProps),
    animationsOf(transitions.call),
    buildScreenOptions(show, screenProps),
  ].reduce(mergeOptions, base);
}

function animationsOf(animations: AnimationOptions | undefined): Options {
  return animations ? { animations } : {};
}

function screenLayout(
  name: string,
  props: object | undefined,
  options: Options,
  id?: string
): Layout {
  return { component: { ...(id && { id }), name, ...(props && { passProps: props }), options } };
}

// The screens that keep the user on them (`usePreventLeave`), with what a try to leave calls.
const leaveHolds = new Map<string, () => void>();

/** Keeps the user on the screen: the close button of its modal calls `onAttempt` instead. Returns what lets go. */
export function holdLeave(screenId: string, onAttempt: () => void): () => void {
  leaveHolds.set(screenId, onAttempt);
  return () => {
    if (leaveHolds.get(screenId) === onAttempt) leaveHolds.delete(screenId);
  };
}

/** The options with a close button first at the leading edge, which dismisses the modal of the screen. */
function withCloseButton(options: Options, screenId: string): Options {
  const close: NavigationButton = {
    id: CLOSE_BUTTON,
    testID: CLOSE_BUTTON,
    systemItem: 'close',
    onPress: () => {
      const held = leaveHolds.get(screenId);
      if (held) held();
      else dismissModal(screenId);
    },
  };
  const leftButtons = [close, ...(options.topBar?.leftButtons ?? [])];
  return mergeOptions(options, { topBar: { leftButtons } });
}

function presentationOptions(settings: ModalConfig): Options {
  const style =
    settings.style ?? (settings.popover ? 'popover' : settings.sheet ? 'pageSheet' : undefined);
  const modal: ModalOptions = defined({
    sheet: settings.sheet,
    contentSize: settings.contentSize,
    popover: settings.popover,
  });
  return {
    ...(style && { modalPresentationStyle: style as OptionsModalPresentationStyle }),
    ...(Object.keys(modal).length > 0 && { modal }),
  };
}
