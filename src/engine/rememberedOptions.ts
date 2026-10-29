import type { Options } from '../interfaces/Options';
import type { LayoutNode } from './layout';
import { mergeOptions } from './mergeOptions';
import { mergeNativeOptions, toNativeOptions } from './options';

// The options every mergeOptions sent to a node, merged over one another, by the node's id. Android destroys the
// activity with every screen while JavaScript lives on, and the last root, set again for the next activity, shows them
// again. Only the nodes of that root keep them, until another root goes out: the activity takes the screens of the
// root with it before the root is set again, so they keep them past their unmount.

/** The nodes of the root the native side got last, as it read them. */
export interface SentRoot {
  nodes: LayoutNode[];
  ids: Set<string>;
}

const remembered = new Map<string, Options>();
let lastRoot: SentRoot | undefined;

/**
 * Remembers the options a mergeOptions sent to the node with the id, over those remembered for it before. Only the
 * nodes of the last root are shown again, so only theirs are kept: a screen pushed or shown in a modal later is not
 * in that root. The options that act once (a tab to select, a menu to open) stay in no node and are not remembered.
 */
export function rememberMergedOptions(id: string, options: Options): void {
  if (!lastRoot?.ids.has(id)) return;
  const kept = withoutOneTimeOptions(options);
  const before = remembered.get(id);
  remembered.set(id, before ? mergeOptions(before, kept) : kept);
}

/** Takes the options at the paths, such as `['topBar', 'subtitle']`, out of those remembered for the node with the id. */
export function forgetMergedPaths(id: string, paths: readonly string[][]): void {
  const own = ownOptions.get(id);
  if (own) ownOptions.set(id, withoutPaths(own, paths));
  const options = remembered.get(id);
  if (options) remembered.set(id, withoutPaths(options, paths));
}

// A copy of the options without the values at the paths.
function withoutPaths(options: Options, paths: readonly string[][]): Options {
  const kept = structuredCopy(options);
  for (const path of paths) {
    const parent = path
      .slice(0, -1)
      .reduce<Record<string, unknown> | undefined>(
        (group, key) => group?.[key] as Record<string, unknown> | undefined,
        kept
      );
    if (parent) delete parent[path[path.length - 1]!];
  }
  return kept as Options;
}

// A copy of the plain objects of options, the other values shared.
function structuredCopy(options: Options): Record<string, unknown> {
  return Object.fromEntries(
    Object.entries(options).map(([key, value]) => [
      key,
      isGroup(value) ? structuredCopy(value as Options) : value,
    ])
  );
}

function isGroup(value: unknown): boolean {
  return (
    typeof value === 'object' && value !== null && Object.getPrototypeOf(value) === Object.prototype
  );
}

/** Forgets the merged options of a node that went; a node of the last root keeps them until another root goes out. */
export function forgetMergedOptions(id: string): void {
  if (!lastRoot?.ids.has(id)) remembered.delete(id);
  ownOptions.delete(id);
}

// The own options of each screen as JavaScript sent them, in development: those it showed with, and every merge and
// clear since. An edit of a screen's options (Fast Refresh) changes only what still has the old definition's value.
const ownOptions = new Map<string, Options>();

/** Notes the own options a screen shows with (development). */
export function noteOwnOptions(id: string, options: Options): void {
  if (__DEV__) ownOptions.set(id, options);
}

/** Notes a merge into a screen's own options (development). */
export function noteMergedOwnOptions(id: string, options: Options): void {
  const before = __DEV__ ? ownOptions.get(id) : undefined;
  if (before) ownOptions.set(id, mergeOptions(before, options));
}

/** The own options of a screen as JavaScript sent them (development); undefined for a screen it didn't note. */
export function ownOptionsOf(id: string): Options | undefined {
  return ownOptions.get(id);
}

/** The options merged into the node with the id, as remembered; undefined without any. */
export function mergedOptionsOf(id: string): Options | undefined {
  return remembered.get(id);
}

/** The root the native side got last; undefined before the first one. */
export function sentRoot(): SentRoot | undefined {
  return lastRoot;
}

/**
 * Notes the layouts of a root going out to the native side now: the root, its modals and its overlays. `again` is the
 * root the native side had when the last root was set again for a new launch of the native side (Android: a new
 * activity), undefined for any other root. While it is still the last one, each node of the new layout in the place of
 * one of its nodes takes the options merged into that one over its own, and keeps them under its own id. Otherwise the
 * nodes of the root before drop theirs: they all go.
 */
export function rootGoesOut(nodes: LayoutNode[], again: SentRoot | undefined): void {
  const before = lastRoot;
  lastRoot = { nodes, ids: new Set(nodes.flatMap(idsOf)) };
  if (!before) return;
  const moved = new Map<string, Options>();
  if (again === before) {
    pairNodes(before.nodes, nodes, (old, node) => {
      const options = remembered.get(old.id);
      if (!options) return;
      node.data.options = mergeNativeOptions(
        node.data.options ?? {},
        toNativeOptions(options, node.id)
      );
      moved.set(node.id, options);
    });
  }
  before.ids.forEach((id) => remembered.delete(id));
  moved.forEach((options, id) => remembered.set(id, options));
}

function idsOf(node: LayoutNode): string[] {
  return [node.id, ...node.children.flatMap(idsOf)];
}

// The same root built again has the same nodes in the same places, the same kind with the same name; those the app gave
// no id to get new ids.
function pairNodes(
  before: LayoutNode[],
  after: LayoutNode[],
  pair: (old: LayoutNode, node: LayoutNode) => void
): void {
  before.forEach((old, index) => {
    const node = after[index];
    if (!node || node.type !== old.type || node.data.name !== old.data.name) return;
    pair(old, node);
    pairNodes(old.children, node.children, pair);
  });
}

// Selecting a tab and opening or closing a menu act when they arrive and stay in no node's options: the root set again
// neither selects the tab nor opens the menu once more.
function withoutOneTimeOptions(options: Options): Options {
  let result = options;
  if (options.bottomTabs) {
    const bottomTabs = { ...options.bottomTabs };
    delete bottomTabs.currentTabIndex;
    delete bottomTabs.currentTabId;
    result = { ...result, bottomTabs };
  }
  if (options.sideMenu) {
    const sideMenu = { ...options.sideMenu };
    for (const side of ['left', 'right'] as const) {
      const settings = sideMenu[side] && { ...sideMenu[side] };
      if (!settings) continue;
      delete settings.visible;
      sideMenu[side] = settings;
    }
    result = { ...result, sideMenu };
  }
  return result;
}
