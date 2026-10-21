import type { SplitViewColumnsStackedEvent } from '../interfaces/Events';
import type { LayoutNode } from './layout';
import { onNativeEvent } from './native';
import { screenNameOf } from './slots';

// Where each screen is, noted as the engine builds the layouts of the commands: what it shows in and its place in its
// stack. A screen keeps its place while it lives (a command can only put screens over it or take it away), so the
// navigation answers about it at once, without asking the native side.

/** Where a screen is. */
export interface ScreenPlace {
  /** Its name, as the native side knows it. */
  name: string;
  /** What it shows in: the root (with its tabs, menus and columns), a modal, or an overlay. */
  container: 'root' | 'modal' | 'overlay';
  /** The id of its stack, null outside a stack. */
  stack: string | null;
  /** Its index in its stack: 0 for the first screen and outside a stack. */
  index: number;
  /** The side menu it is in, and its part there: a menu or the center. */
  menu?: { id: string; part: 'left' | 'center' | 'right' };
  /**
   * The layer it shows in: `'root'`, or the id of the layout of its modal or overlay. A screen of a layer under the one
   * the user sees is covered, by a sheet too, though it doesn't disappear then.
   */
  layer: string;
  /** The pair it is a view of; `alone`: its primary, shown alone where the native side shows no pairs. */
  arrangement?: { id: string; placement: 'primary' | 'secondary'; alone?: boolean };
}

const MENU_PARTS: Partial<Record<LayoutNode['type'], 'left' | 'center' | 'right'>> = {
  SideMenuLeft: 'left',
  SideMenuCenter: 'center',
  SideMenuRight: 'right',
};

/** A column of a split view: the split view's id and the column's name. */
export interface SplitColumn {
  split: string;
  column: string;
}

const places = new Map<string, ScreenPlace>();
// The column each stack of a split view shows, by the stack's id, and the column of a screen alone in one; the stack
// of each column, by the split view's id. A screen pushed in a column's stack is in that column too.
const columnOfStack = new Map<string, SplitColumn>();
const columnOfScreen = new Map<string, SplitColumn>();
const stacksOfSplit = new Map<string, Partial<Record<string, string>>>();
// The split views whose columns show as one stack, one over another, as the native side tells.
const stackedSplits = new Set<string>();
let listeningToSplitViews = false;
// The primaries of pairs shown alone, noted as their node is built, until their place is noted.
const alone = new Set<string>();
// The last screens that went, so a command from one of them can say so; a screen the engine never saw is not one.
const gone = new Set<string>();
const GONE_KEPT = 200;

export function placeOf(screenId: string): ScreenPlace | undefined {
  return places.get(screenId);
}

/** Notes that a screen is the primary of a pair shown alone, where the native side shows no pairs. */
export function noteAlone(screenId: string): void {
  alone.add(screenId);
}

/** The screen was shown and is gone. */
export function isGone(screenId: string): boolean {
  return gone.has(screenId);
}

/**
 * Notes the screens of a layout, the first of a stack at `index`. A modal or an overlay given as a whole is a layer of
 * its own; screens added to one, such as a push, take the layer of the screen they come from.
 */
export function notePlaces(
  node: LayoutNode,
  container: ScreenPlace['container'],
  stack: string | null = null,
  index = 0,
  menu?: ScreenPlace['menu'],
  layer: string = container === 'root' ? 'root' : node.id
): void {
  if (node.type === 'Component' || node.type === 'ExternalComponent') {
    const name = node.data.name ?? '';
    gone.delete(node.id);
    columnOfScreen.delete(node.id);
    places.set(node.id, {
      name: screenNameOf(name),
      container,
      stack,
      index,
      ...(menu && { menu }),
      layer,
      ...(alone.delete(node.id)
        ? { arrangement: { id: node.id, placement: 'primary' as const, alone: true } }
        : {}),
    });
  } else if (node.type === 'Arrangement') {
    // One entry of its stack: both screens have its place there, and each knows the pair.
    node.children.forEach((child, childIndex) => {
      notePlaces(child, container, stack, index, menu, layer);
      const place = places.get(child.id);
      if (place) {
        const placement = childIndex === 0 ? 'primary' : 'secondary';
        places.set(child.id, { ...place, arrangement: { id: node.id, placement } });
      }
    });
  } else if (node.type === 'Stack') {
    columnOfStack.delete(node.id);
    node.children.forEach((child, childIndex) =>
      notePlaces(child, container, node.id, childIndex, menu, layer)
    );
  } else if (node.type === 'SplitView') {
    // A split view sent again with its id starts anew: its columns are noted again, and it isn't one stack until the
    // native side says so.
    forgetSplit(node.id);
    const columns = node.data.columns ?? [];
    const stacks: Partial<Record<string, string>> = {};
    node.children.forEach((child, childIndex) => {
      notePlaces(child, container, null, 0, menu, layer);
      const column = columns[childIndex];
      if (!column) return;
      if (child.type === 'Stack') stacks[column] = child.id;
      noteColumn(child, { split: node.id, column });
    });
    stacksOfSplit.set(node.id, stacks);
  } else if (node.type === 'SideMenuRoot') {
    for (const part of node.children) {
      const side = MENU_PARTS[part.type];
      part.children.forEach((child) =>
        notePlaces(child, container, null, 0, side ? { id: node.id, part: side } : menu, layer)
      );
    }
  } else {
    node.children.forEach((child) => notePlaces(child, container, null, 0, menu, layer));
  }
}

// The column of every stack and every screen out of a stack under a column of a split view, also in its tabs or menus.
function noteColumn(node: LayoutNode, column: SplitColumn): void {
  if (node.type === 'Stack') columnOfStack.set(node.id, column);
  else if (node.type === 'Component' || node.type === 'ExternalComponent') {
    columnOfScreen.set(node.id, column);
  } else node.children.forEach((child) => noteColumn(child, column));
}

function forgetSplit(split: string): void {
  stackedSplits.delete(split);
  stacksOfSplit.delete(split);
  for (const columns of [columnOfStack, columnOfScreen]) {
    for (const [id, column] of columns) if (column.split === split) columns.delete(id);
  }
}

/** The column of a split view the screen shows in, through its stack or alone; undefined outside a split view. */
export function columnOf(screenId: string): SplitColumn | undefined {
  const place = places.get(screenId);
  if (!place) return undefined;
  return place.stack !== null ? columnOfStack.get(place.stack) : columnOfScreen.get(screenId);
}

/** The id of the stack a column of the split view shows, when it shows one. */
export function stackOfColumn(split: string, column: string): string | undefined {
  return stacksOfSplit.get(split)?.[column];
}

/**
 * Whether the screen is the first of its column's stack and that column is over the column before it: the columns of
 * its split view show as one stack (a phone), so back goes to the column before.
 */
export function coversColumn(screenId: string): boolean {
  const place = places.get(screenId);
  const column = columnOf(screenId);
  return (
    place !== undefined &&
    place.stack !== null &&
    place.index === 0 &&
    column !== undefined &&
    (column.column === 'secondary' || column.column === 'supplementary') &&
    stackOfColumn(column.split, column.column) === place.stack &&
    stackedSplits.has(column.split)
  );
}

/** Listens to the split views telling whether their columns show as one stack, once for the app. */
export function listenToSplitViews(): void {
  if (listeningToSplitViews) return;
  listeningToSplitViews = true;
  onNativeEvent<SplitViewColumnsStackedEvent>(
    'SplitViewColumnsStacked',
    ({ componentId, stacked }) => {
      if (stacked) stackedSplits.add(componentId);
      else stackedSplits.delete(componentId);
    }
  );
}

/** The screen is gone. */
export function forgetPlace(screenId: string): void {
  places.delete(screenId);
  columnOfScreen.delete(screenId);
  gone.add(screenId);
  if (gone.size > GONE_KEPT) gone.delete(gone.values().next().value!);
}
