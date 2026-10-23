import { Platform } from 'react-native';
import { clearButtonHandlers } from './buttons';
import { rememberDefaultOptions, rememberRoot, restoresRoot } from './launchState';
import type { Layout, LayoutRoot } from '../interfaces/Layout';
import type { Options } from '../interfaces/Options';
import { nextId } from './ids';
import { type LayoutNode, toNativeLayout } from './layout';
import { whenNativeScreensReady } from './nativeScreens';
import { commands as native } from './native';
import { toNativeOptions } from './options';
import { dropReactRenderMeasures } from './reactMeasures';
import { columnOf, forgetPlace, notePlaces, placeOf, stackOfColumn } from './places';
import { forgetProps } from './props';
import {
  forgetMergedOptions,
  rememberMergedOptions,
  rootGoesOut,
  sentRoot,
} from './rememberedOptions';
import { afterLoading } from './screens';
import { holdSlot, isSlotName, removeSlots, showSlot, type SlotFrame } from './slots';
import { forgetTabsScreen, showScreensFrom } from './tabs';

// The commands of the native side, with the layouts and options as it reads them, each with an id of its own. A
// command that shows or closes screens also drops React's measures of the renders before it (development only); one
// that shows lazy screens goes once their modules have loaded, and one that shows native screens with `useProps` once
// their hooks have returned. A mergeOptions made while a show waits goes after it.

const NODE_KINDS = [
  'component',
  'externalComponent',
  'stack',
  'bottomTabs',
  'sideMenu',
  'splitView',
];

/**
 * Fails when the layouts give one id twice: an id names one screen, and the second one would take the props and the
 * commands of the first. The ids the app gives are the only ones that can meet; those the engine makes never do. With
 * `stack`, the screens go to that stack: they can take the id of a screen of it, which they replace, not that of a
 * screen shown elsewhere. That is told before anything is shown, as the screen with the id keeps its slot.
 */
function checkIds(layouts: unknown, stack?: string | null): void {
  const seen = new Set<string>();
  const visit = (value: unknown) => {
    if (Array.isArray(value)) {
      value.forEach(visit);
    } else if (typeof value === 'object' && value !== null) {
      for (const [key, item] of Object.entries(value)) {
        if (key === 'options' || key === 'passProps') continue;
        const id = NODE_KINDS.includes(key) ? (item as { id?: unknown } | null)?.id : undefined;
        if (typeof id === 'string') {
          if (seen.has(id)) throw new Error(`[navigation] The id '${id}' goes to two screens`);
          const shown = stack === undefined ? undefined : placeOf(id);
          if (shown && (stack === null || shown.stack !== stack)) {
            throw new Error(
              `[navigation] the screen '${id}' shows already: an id goes to one screen`
            );
          }
          seen.add(id);
        }
        visit(item);
      }
    }
  };
  visit(layouts);
}

/** The names of the screens and components a layout shows: in its nodes and in their options. */
function screenNames(value: unknown, names: string[] = []): string[] {
  if (Array.isArray(value)) {
    value.forEach((item) => screenNames(item, names));
  } else if (typeof value === 'object' && value !== null) {
    for (const [key, item] of Object.entries(value)) {
      // The props are the app's own data, never a layout.
      if (key === 'passProps') continue;
      const named = item as { name?: unknown } | null;
      if ((key === 'component' || key === 'resultsComponent') && typeof named?.name === 'string') {
        names.push(named.name);
      }
      screenNames(item, names);
    }
  }
  return names;
}

/**
 * Every root passes here, so a root set with the low-level API comes back too when Android launches again. The last
 * root set again so comes with the options merged into its nodes since it was set.
 */
export function setRoot(root: LayoutRoot): Promise<string> {
  dropReactRenderMeasures();
  // The root the native side had, when this sets the last root again for a new launch.
  const again = restoresRoot(root) ? sentRoot() : undefined;
  rememberRoot(root);
  const commandId = nextId('setRoot');
  return show((goneOut) =>
    afterLoading(screenNames(root), () => {
      checkIds(root);
      const layout = {
        root: toNativeLayout(root.root),
        modals: (root.modals ?? []).map(toNativeLayout),
        overlays: (root.overlays ?? []).map(toNativeLayout),
      };
      notePlaces(layout.root, 'root');
      layout.modals.forEach((modal) => notePlaces(modal, 'modal'));
      layout.overlays.forEach((overlay) => notePlaces(overlay, 'overlay'));
      const nodes = [layout.root, ...layout.modals, ...layout.overlays];
      return whenNativeScreensReady(nodes, () =>
        inOrder(() =>
          sendShow(goneOut, () => {
            // As it goes, so that it takes every mergeOptions that went before it.
            rootGoesOut(nodes, again);
            return native.setRoot(commandId, layout);
          })
        )
      );
    })
  );
}

export function setDefaultOptions(options: Options): void {
  rememberDefaultOptions(options);
  native.setDefaultOptions(toNativeOptions(options, 'defaultOptions'));
}

// The show commands that wait in JS, by number: for lazy screens to load, for the first props of native screens, for a
// prepared push to go out (the push itself until it goes, the commands after it in `inOrder`). A mergeOptions made
// while one waits goes once every show made before it went out, so that it finds the screens they show; the held ones
// go in the order they were made. With none waiting it goes at once, so `useScreenOptions` lands before the frame.
let shows = 0;
const waitingShows = new Set<number>();
const heldMerges: { id: string; after: number; send: () => void }[] = [];
// The screens of prepared pushes, laid out by the native side before their push: it keeps what is merged into them
// for the push, so their own options, from their first render, go at once (after those held for them before).
const preparedScreens = new Set<string>();

/**
 * Merges options into a node: a screen, or any node of a layout. Made while a show waits in JS, it goes after it. The
 * engine remembers them by the node's id, so the root set again for a new launch of the native side shows them too.
 */
export function mergeOptions(componentId: string, options: Options): void {
  const nativeOptions = toNativeOptions(options, componentId);
  const send = () => {
    rememberMergedOptions(componentId, options);
    native.mergeOptions(componentId, nativeOptions);
  };
  const prepared =
    preparedScreens.has(componentId) && !heldMerges.some(({ id }) => id === componentId);
  if (waitingShows.size === 0 || prepared) send();
  else heldMerges.push({ id: componentId, after: shows, send });
}

// Runs a show command; the function `run` gets lets the mergeOptions made after the show go, once it went out. It is
// called again when the command ends, so a command that fails before it goes out lets them go too.
function show<Result>(run: (goneOut: () => void) => Promise<Result>): Promise<Result> {
  const number = ++shows;
  waitingShows.add(number);
  const goneOut = () => {
    if (waitingShows.delete(number)) sendHeldMerges();
  };
  let result: Promise<Result>;
  try {
    result = run(goneOut);
  } catch (error) {
    goneOut();
    throw error;
  }
  result.then(goneOut, goneOut);
  return result;
}

// Sends the command of a show to the native side, then the mergeOptions that waited for it.
function sendShow<Result>(goneOut: () => void, send: () => Promise<Result>): Promise<Result> {
  try {
    return send();
  } finally {
    goneOut();
  }
}

function sendHeldMerges(): void {
  for (let merge = heldMerges[0]; merge && !showWaitsUntil(merge.after); merge = heldMerges[0]) {
    heldMerges.shift();
    try {
      merge.send();
    } catch {
      // The native side reports the options it can't read itself; a merge must not fail the show it waited for.
    }
  }
}

// Whether a show made no later than the one with the number still waits.
function showWaitsUntil(number: number): boolean {
  for (const waiting of waitingShows) if (waiting <= number) return true;
  return false;
}

/**
 * Pushes a layout over the screen. A push that is no longer `wanted` when it is about to go out (a preview whose
 * component unmounted while its screen loaded or its native screen got its first props) is dropped: its
 * `dismissPreview` already went, and it would come after it.
 */
export function push(
  componentId: string,
  layout: Layout,
  wanted: () => boolean = () => true
): Promise<string> {
  dropReactRenderMeasures();
  const commandId = nextId('push');
  return show((goneOut) =>
    afterLoading(screenNames(layout), () => {
      checkIds(layout);
      const node = toNativeLayout(layout);
      // Held in the same task as it was added, so React never renders it before it is laid out.
      const prepares = preparesPush(node);
      if (prepares) holdSlot(node.id);
      showScreensFrom(componentId, node);
      // Over the screen it is pushed from, in its stack.
      const from = placeOf(componentId);
      notePlaces(
        node,
        from?.container ?? 'root',
        from?.stack ?? null,
        (from?.index ?? 0) + 1,
        from?.menu,
        from?.layer
      );
      return whenNativeScreensReady([node], () =>
        sendPush(commandId, componentId, node, prepares, wanted, goneOut).then((shownId) => {
          // A screen with a key went back to the one with the same key: the new screen never shows.
          if (shownId !== node.id) forgetNeverShown(node.id);
          return shownId;
        })
      );
    })
  );
}

// Everything a push noted for a screen that never mounts, which an unmount would clear.
function forgetNeverShown(screenId: string): void {
  forgetProps(screenId);
  forgetPlace(screenId);
  forgetMergedOptions(screenId);
  removeSlots([screenId]);
  clearButtonHandlers(screenId);
  forgetTabsScreen(screenId);
}

// Android, one React tree: the native side lays the screen out in its stack first, React renders it at that size with
// its content, and the push starts with the screen drawn, without waiting for it.
function preparesPush(node: LayoutNode): boolean {
  return Platform.OS === 'android' && node.type === 'Component' && isSlotName(node.data.name);
}

// Resolves once every prepared push made so far reached the native side: they go out after their screen rendered.
let preparedPushesSent: Promise<void> | undefined;

// The commands reach the native side in the order the app made them: those after a prepared push wait for it to go
// out (a pop right after a push pops the pushed screen, a new root comes after it).
function inOrder<Result>(send: () => Promise<Result>): Promise<Result> {
  return preparedPushesSent ? preparedPushesSent.then(send) : send();
}

function sendPush(
  commandId: string,
  componentId: string,
  node: LayoutNode,
  prepares: boolean,
  wanted: () => boolean,
  goneOut: () => void
): Promise<string> {
  // Previews are iOS only, where no push is prepared: the check is on the usual way.
  if (!prepares) {
    return inOrder(() => {
      if (wanted()) return sendShow(goneOut, () => native.push(commandId, componentId, node));
      forgetNeverShown(node.id);
      return Promise.reject(new Error('[navigation] push: the preview went before it was shown'));
    });
  }
  const before = preparedPushesSent;
  let sent!: () => void;
  const mine = new Promise<void>((resolve) => {
    sent = resolve;
  });
  const queued = before ? before.then(() => mine) : mine;
  preparedPushesSent = queued;
  queued.then(() => {
    if (preparedPushesSent === queued) preparedPushesSent = undefined;
  });
  return prepareAndPush(commandId, componentId, node, before, sent, goneOut);
}

async function prepareAndPush(
  commandId: string,
  componentId: string,
  node: LayoutNode,
  before: Promise<void> | undefined,
  sent: () => void,
  goneOut: () => void
): Promise<string> {
  // Released as soon as the push went out (or failed), not when it completes.
  try {
    const frame = (await native.preparePush(commandId, componentId, node)) as SlotFrame | null;
    if (frame) preparedScreens.add(node.id);
    try {
      // Not prepared (`waitForRender: true`, a key that goes back, …): the usual push.
      await showSlot(node.id, frame ?? undefined);
      await before;
    } catch (error) {
      if (frame) native.cancelPreparedPush(node.id);
      throw error;
    }
    return native.push(commandId, componentId, node);
  } finally {
    preparedScreens.delete(node.id);
    sent();
    goneOut();
  }
}

/**
 * Takes away a preview that waits on the native side (iOS). A preview whose push hasn't gone out yet isn't there: the
 * `wanted` of that push drops it.
 */
export function dismissPreview(previewId: string): void {
  inOrder(async () => native.dismissPreview(previewId)).catch(() => {});
}

// The pop commands take no options: the transition is the one the leaving top screen's `animations.pop` gives.
export function pop(componentId: string): Promise<string> {
  dropReactRenderMeasures();
  const commandId = nextId('pop');
  return inOrder(() => native.pop(commandId, componentId));
}

export function popTo(componentId: string): Promise<string> {
  dropReactRenderMeasures();
  const commandId = nextId('popTo');
  return inOrder(() => native.popTo(commandId, componentId));
}

export function popToRoot(componentId: string): Promise<string> {
  dropReactRenderMeasures();
  const commandId = nextId('popToRoot');
  return inOrder(() => native.popToRoot(commandId, componentId));
}

export function setStackRoot(componentId: string, children: Layout[]): Promise<string> {
  dropReactRenderMeasures();
  const commandId = nextId('setStackRoot');
  return show((goneOut) =>
    afterLoading(screenNames(children), () => {
      checkIds(children, placeOf(componentId)?.stack);
      const nodes = children.map(toNativeLayout);
      nodes.forEach((node) => showScreensFrom(componentId, node));
      const from = placeOf(componentId);
      nodes.forEach((node, index) =>
        notePlaces(
          node,
          from?.container ?? 'root',
          from?.stack ?? null,
          index,
          from?.menu,
          from?.layer
        )
      );
      return whenNativeScreensReady(nodes, () =>
        inOrder(() => sendShow(goneOut, () => native.setStackRoot(commandId, componentId, nodes)))
      );
    })
  );
}

export function showInColumn(componentId: string, column: string, layout: Layout): Promise<string> {
  dropReactRenderMeasures();
  const commandId = nextId('showInColumn');
  return show((goneOut) =>
    afterLoading(screenNames(layout), () => {
      checkIds(layout);
      const node = toNativeLayout(layout);
      showScreensFrom(componentId, node);
      const from = placeOf(componentId);
      const at = columnPlace(componentId, column);
      notePlaces(node, from?.container ?? 'root', at.stack, at.index, from?.menu, from?.layer);
      return whenNativeScreensReady([node], () =>
        inOrder(() =>
          sendShow(goneOut, () => native.showInColumn(commandId, componentId, column, node))
        )
      );
    })
  );
}

// Where `showInColumn` puts the screen, as the native side does: over the screen that asks when that one is in the
// stack of the compact column (what a phone shows) or in a stack out of a split view; otherwise first in the stack of
// the column, in place of what it shows (Android shows it in the secondary pane, whatever the column).
function columnPlace(fromId: string, column: string): { stack: string | null; index: number } {
  const from = placeOf(fromId);
  const fromColumn = columnOf(fromId);
  if (from && from.stack !== null && (!fromColumn || fromColumn.column === 'compact')) {
    return { stack: from.stack, index: from.index + 1 };
  }
  const target = Platform.OS === 'android' ? 'secondary' : column;
  return { stack: (fromColumn && stackOfColumn(fromColumn.split, target)) ?? null, index: 0 };
}

/** Shows or hides a column of the split view the screen is in, as the platform does it. */
export function setColumnShown(componentId: string, column: string, shown: boolean): Promise<void> {
  const commandId = nextId(shown ? 'showColumn' : 'hideColumn');
  return inOrder(() => native.setColumnShown(commandId, componentId, column, shown));
}

export function showModal(layout: Layout): Promise<string> {
  dropReactRenderMeasures();
  const commandId = nextId('showModal');
  return show((goneOut) =>
    afterLoading(screenNames(layout), () => {
      checkIds(layout);
      const node = toNativeLayout(layout);
      notePlaces(node, 'modal');
      return whenNativeScreensReady([node], () =>
        inOrder(() => sendShow(goneOut, () => native.showModal(commandId, node)))
      );
    })
  );
}

export function dismissModal(componentId: string, options?: Options): Promise<string> {
  dropReactRenderMeasures();
  const commandId = nextId('dismissModal');
  return inOrder(() =>
    native.dismissModal(commandId, componentId, options && toNativeOptions(options, componentId))
  );
}

export function dismissAllModals(options?: Options): Promise<string> {
  dropReactRenderMeasures();
  const commandId = nextId('dismissAllModals');
  return inOrder(() => native.dismissAllModals(commandId, options && toNativeOptions(options)));
}

export function showOverlay(layout: Layout): Promise<string> {
  dropReactRenderMeasures();
  const commandId = nextId('showOverlay');
  return show((goneOut) =>
    afterLoading(screenNames(layout), () => {
      checkIds(layout);
      const node = toNativeLayout(layout);
      notePlaces(node, 'overlay');
      return whenNativeScreensReady([node], () =>
        inOrder(() => sendShow(goneOut, () => native.showOverlay(commandId, node)))
      );
    })
  );
}

export function dismissOverlay(componentId: string): Promise<string> {
  dropReactRenderMeasures();
  const commandId = nextId('dismissOverlay');
  return inOrder(() => native.dismissOverlay(commandId, componentId));
}

export function dismissAllOverlays(): Promise<string> {
  dropReactRenderMeasures();
  const commandId = nextId('dismissAllOverlays');
  return inOrder(() => native.dismissAllOverlays(commandId));
}
