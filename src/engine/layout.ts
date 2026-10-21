import { Platform } from 'react-native';
import { mergeOptions } from './mergeOptions';
import type {
  ExternalComponent,
  Layout,
  LayoutBottomTabs,
  LayoutComponent,
  LayoutSideMenu,
  LayoutSplitView,
} from '../interfaces/Layout';
import type { Options } from '../interfaces/Options';
import { takeButtonHandlers } from './buttons';
import { nextId } from './ids';
import { showNativeScreen } from './nativeScreens';
import { toNativeOptions } from './options';
import { setProps } from './props';
import { getStartInfo } from './native';
import { noteAlone } from './places';
import {
  arrangementOf,
  checkArrangement,
  type EngineArrangement,
  isReactScreen,
  nativeScreenName,
  rendersInSlot,
  screenKey,
  screenOptions,
} from './screens';
import { slotName } from './slots';
import { showTabs, TAB_PRESS_HANDLERS, type WithTabPressHandlers } from './tabs';

export type LayoutType =
  | 'Component'
  | 'ExternalComponent'
  | 'Stack'
  | 'BottomTabs'
  | 'SideMenuRoot'
  | 'SideMenuLeft'
  | 'SideMenuCenter'
  | 'SideMenuRight'
  | 'SplitView'
  | 'Arrangement';

/** A node of the layout as the native side reads it. */
export interface LayoutNode {
  id: string;
  type: LayoutType;
  data: {
    name?: string;
    options?: Options;
    passProps?: object;
    /** The names of the functions in the props of a native screen, which it calls in JS. */
    functions?: string[];
    /** The key of a screen: a push of a screen with the same key goes back to it in its stack. */
    key?: string;
    columns?: string[];
    /** A pair: how it lies (`arrangement` of a screen, `defineArrangement`). */
    style?: 'split' | 'overlay';
    axes?: 'horizontal' | 'vertical' | 'both';
    fraction?: number;
    primaryEdge?: 'leading' | 'trailing' | 'top' | 'bottom';
    compact?: 'primary' | 'pair';
  };
  children: LayoutNode[];
}

// The Android bottom navigation bar can't hold more tabs; the native side would crash.
const MAX_ANDROID_BOTTOM_TABS = 5;

/**
 * The layout as the native side reads it. Each screen gets its id and its own options under those of the show; the
 * press handlers of its buttons stay here, a native screen goes as an external component, and in one tree a React
 * screen goes as a slot. The props of the React screens wait here for them. The layout passed stays as it was.
 */
export function toNativeLayout(layout: Layout): LayoutNode {
  if (layout.sideMenu) return sideMenu(layout.sideMenu);
  if (layout.bottomTabs) return bottomTabs(layout.bottomTabs);
  if (layout.stack) {
    const { id, options, children } = layout.stack;
    return parent('Stack', id, options, children);
  }
  if (layout.component) return screen(layout.component);
  if (layout.externalComponent) return external(layout.externalComponent);
  if (layout.splitView) return splitView(layout.splitView);
  throw new Error(
    `[navigation] The layout is of no known kind (keys: ${Object.keys(layout).join(', ')})`
  );
}

// `inPair`: a screen of a pair, whose own `arrangement` is not used; nor its key (`keyed`), as its place is the pair's,
// unless it shows alone.
function screen(component: LayoutComponent, inPair = false, keyed = !inPair): LayoutNode {
  const name = String(component.name);
  const pair = inPair ? undefined : arrangementOf(name);
  if (pair) return arrangement(component, name, pair);
  const id = component.id || nextId('Component');
  const props = component.passProps;
  const nativeName = nativeScreenName(name);
  // The native side would show an empty screen, or fail far from the command that named it.
  if (!nativeName && !isReactScreen(name)) {
    throw new Error(`[navigation] There is no screen '${name}' in screens`);
  }
  if (nativeName) {
    // A native screen: its props go to the native side as data, its own options over those of the show.
    const options = takeButtonHandlers(id, component.options ?? {});
    const node = external({
      id,
      name: nativeName,
      options: mergeOptions(screenOptions(name, props ?? {}), options),
    });
    showNativeScreen(node, name, props ?? {});
    return node;
  }
  // The screen's own options under those of the show (a command has them there already: merging again changes
  // nothing).
  const own = screenOptions(name, props ?? {});
  const options = takeButtonHandlers(id, mergeOptions(own, component.options ?? {}));
  const key = keyed ? screenKey(name, props ?? {}) : undefined;
  setProps(id, props);
  return {
    id,
    type: 'Component',
    data: {
      name: rendersInSlot(name) ? slotName(id, name) : name,
      options: toNativeOptions(options),
      ...(key !== undefined && { key }),
    },
    children: [],
  };
}

/**
 * Two screens as one screen of a stack. The props of the show go to the primary and, through `secondaryProps`, to the
 * secondary; the options of the show to the primary, whose bar and options are the pair's. Where the native side shows
 * no pair, the primary alone. An id given to the show is the primary's on every device, so what goes by it (the close
 * button of a modal, its `onDismiss`, a held leave) finds the same screen; the pair gets one of its own.
 */
function arrangement(
  component: LayoutComponent,
  name: string,
  pair: EngineArrangement
): LayoutNode {
  checkArrangement(name, pair);
  const props = component.passProps;
  const secondaryProps = props && pair.secondaryProps ? pair.secondaryProps(props) : props;
  const part = (partName: string, partProps?: object, options?: Options): LayoutComponent => ({
    name: partName,
    ...(partProps && { passProps: partProps }),
    ...(options && { options }),
  });
  const primary = {
    ...part(pair.primary, props, component.options),
    ...(component.id && { id: component.id }),
  };
  if (!getStartInfo().arrangements) {
    const alone = screen(primary, true, true);
    noteAlone(alone.id);
    return alone;
  }
  const { style, axes, fraction, primaryEdge, compact } = pair;
  return {
    id: nextId('Arrangement'),
    type: 'Arrangement',
    data: { name, style, axes, fraction, primaryEdge, compact },
    children: [screen(primary, true), screen(part(pair.secondary, secondaryProps), true)],
  };
}

function external(component: ExternalComponent): LayoutNode {
  return {
    id: component.id || nextId('ExternalComponent'),
    type: 'ExternalComponent',
    data: {
      name: String(component.name),
      options: toNativeOptions(component.options),
      passProps: component.passProps,
    },
    children: [],
  };
}

function parent(
  type: LayoutType,
  id: string | undefined,
  options: Options | undefined,
  children: Layout[] = []
): LayoutNode {
  return {
    id: id || nextId(type),
    type,
    data: { options: toNativeOptions(options) },
    children: children.map(toNativeLayout),
  };
}

function bottomTabs(tabs: LayoutBottomTabs): LayoutNode {
  const count = tabs.children?.length ?? 0;
  if (Platform.OS === 'android' && count > MAX_ANDROID_BOTTOM_TABS) {
    throw new Error(
      `[navigation] Tabs show at most ${MAX_ANDROID_BOTTOM_TABS} tabs on Android; the layout has ${count}`
    );
  }
  const node = parent('BottomTabs', tabs.id, tabs.options, tabs.children);
  // The press handlers of the tabs stay here, under the id the native side names the tabs with.
  showTabs(node, (tabs as WithTabPressHandlers)[TAB_PRESS_HANDLERS]);
  return node;
}

function sideMenu(menu: LayoutSideMenu): LayoutNode {
  const side = (type: LayoutType, content: Layout): LayoutNode => ({
    id: nextId(type),
    type,
    data: {},
    children: [toNativeLayout(content)],
  });
  return {
    id: menu.id || nextId('SideMenuRoot'),
    type: 'SideMenuRoot',
    data: { options: toNativeOptions(menu.options) },
    children: [
      ...(menu.left ? [side('SideMenuLeft', menu.left)] : []),
      side('SideMenuCenter', menu.center),
      ...(menu.right ? [side('SideMenuRight', menu.right)] : []),
    ],
  };
}

// The children with the column of each: `master` is the primary column, `detail` the secondary one. A split view
// needs both.
function splitView(split: LayoutSplitView): LayoutNode {
  const columns = (
    [
      ['primary', split.master],
      ['secondary', split.detail],
      ['supplementary', split.supplementary],
      ['compact', split.compact],
      ['inspector', split.inspector],
    ] as [string, Layout | undefined][]
  ).filter((column): column is [string, Layout] => column[1] !== undefined);
  const present = split.master && split.detail ? columns : [];
  return {
    id: split.id || nextId('SplitView'),
    type: 'SplitView',
    data: { options: toNativeOptions(split.options), columns: present.map(([column]) => column) },
    children: present.map(([, content]) => toNativeLayout(content)),
  };
}
