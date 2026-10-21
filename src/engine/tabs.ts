import type { BottomTabPressedEvent } from '../interfaces/Events';
import type { LayoutNode } from './layout';
import { onNativeEvent } from './native';

/** A press of a tab. */
export interface TabPressEvent {
  /** The index of the tab. */
  index: number;
  /** The tab was selected already: a second press, which apps use to scroll to the top. */
  wasSelected: boolean;
  /** The screen the tab shows: the top of its stack. */
  screenId?: string;
}

export type TabPressHandler = (event: TabPressEvent) => void;

/** Where a tabs layout keeps the press handlers of its tabs, by tab index, until a command shows it. */
export const TAB_PRESS_HANDLERS = Symbol('apexnative.navigation.tabPressHandlers');

/** A tabs layout with press handlers. */
export interface WithTabPressHandlers {
  [TAB_PRESS_HANDLERS]?: Map<number, TabPressHandler>;
}

// Tabs shown with press handlers, and the React screens shown in them. The tabs are gone once every screen shown in
// them unmounted, those pushed into their stacks later too; the end of a native screen isn't heard, so tabs with one
// keep their handlers.
interface ShownTabs {
  id: string;
  handlers: Map<number, TabPressHandler>;
  screens: Set<string>;
  native: boolean;
}

const tabsById = new Map<string, ShownTabs>();
const tabsOfScreen = new Map<string, Set<ShownTabs>>();
let listening = false;

/** Tabs a command shows, as the native side reads them, with the press handlers their layout kept. */
export function showTabs(
  node: LayoutNode,
  handlers: Map<number, TabPressHandler> | undefined
): void {
  if (!handlers || handlers.size === 0) return;
  listen();
  const tabs: ShownTabs = { id: node.id, handlers, screens: new Set(), native: false };
  tabsById.set(node.id, tabs);
  node.children.forEach((child) => addScreens(child, [tabs]));
}

/** Screens a command shows from a screen are in the tabs that screen is in: a push into the stack of a tab. */
export function showScreensFrom(fromId: string, node: LayoutNode): void {
  const tabs = tabsOfScreen.get(fromId);
  if (tabs) addScreens(node, [...tabs]);
}

/** A React screen unmounted: tabs with no screen left are gone, and so are their press handlers. */
export function forgetTabsScreen(screenId: string): void {
  const tabs = tabsOfScreen.get(screenId);
  if (!tabs) return;
  tabsOfScreen.delete(screenId);
  tabs.forEach((shown) => {
    shown.screens.delete(screenId);
    // Tabs shown again under the same id have handlers of their own.
    if (shown.screens.size === 0 && !shown.native && tabsById.get(shown.id) === shown) {
      tabsById.delete(shown.id);
    }
  });
}

function addScreens(node: LayoutNode, tabs: ShownTabs[]) {
  if (node.type === 'Component') {
    const own = tabsOfScreen.get(node.id) ?? new Set<ShownTabs>();
    tabs.forEach((shown) => {
      shown.screens.add(node.id);
      own.add(shown);
    });
    tabsOfScreen.set(node.id, own);
  } else if (node.type === 'ExternalComponent') {
    tabs.forEach((shown) => (shown.native = true));
  }
  node.children.forEach((child) => addScreens(child, tabs));
}

function listen() {
  if (listening) return;
  listening = true;
  onNativeEvent<BottomTabPressedEvent>('BottomTabPressed', (event) => {
    tabsById.get(event.bottomTabsId)?.handlers.get(event.tabIndex)?.({
      index: event.tabIndex,
      wasSelected: event.wasSelected,
      ...(event.screenId && { screenId: event.screenId }),
    });
  });
}
