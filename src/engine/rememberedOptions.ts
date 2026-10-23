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

/** Forgets the merged options of a node that went; a node of the last root keeps them until another root goes out. */
export function forgetMergedOptions(id: string): void {
  if (!lastRoot?.ids.has(id)) remembered.delete(id);
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
