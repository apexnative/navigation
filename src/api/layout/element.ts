import { Platform } from 'react-native';
import type { Layout, LayoutComponent } from '../../interfaces/Layout';
import type { Options } from '../../interfaces/Options';
import { mergeOptions } from '../../engine/mergeOptions';

/** Builds the layout of an element; the library calls it when a command takes the element. */
export const BUILD = Symbol('apexnative.navigation.build');

/**
 * A screen a layout shows, for the types: its name, and its props (`undefined` for a screen named alone). The
 * commands that take a layout check them against the screens of the app, as they check their own.
 */
export interface ScreenUse {
  name: string;
  props: object | undefined;
}

declare const screensShown: unique symbol;

/** Carries the screens a layout shows, for the types. */
export interface ShowsScreens<Uses extends ScreenUse> {
  /** Never set. */
  readonly [screensShown]?: Uses;
}

/** An element of a layout: a screen, a stack, tabs, a side menu, a split view. */
export interface LayoutElement<Uses extends ScreenUse = ScreenUse> extends ShowsScreens<Uses> {
  [BUILD](): Layout;
}

/**
 * What a place in a layout takes: a screen by name, an element (`Screen(name, props)` for a screen with props), or a
 * layout as JSON.
 */
export type LayoutContent = string | LayoutElement<any> | Layout;

/** The screens a place of a layout shows; a layout as JSON is not checked. */
export type UsesOf<Content> = Content extends string
  ? { name: Content; props: undefined }
  : Content extends LayoutElement<infer Uses>
    ? Uses
    : never;

export function isLayoutElement(value: unknown): value is LayoutElement<any> {
  return typeof value === 'object' && value !== null && BUILD in value;
}

export function screenComponent(name: string): { component: LayoutComponent } {
  return { component: { name } };
}

/**
 * The layout of a place. A screen named alone becomes a stack with it where it navigates on (`'stack'`: the root, a
 * tab, the center of a side menu, a column), and stays a screen where it does not (`'screen'`: a side menu, an
 * overlay).
 */
export function buildContent(content: LayoutContent, screenAlone: 'stack' | 'screen'): Layout {
  if (isLayoutElement(content)) return content[BUILD]();
  if (typeof content === 'string') {
    const screen = screenComponent(content);
    return screenAlone === 'stack' ? { stack: { children: [screen] } } : screen;
  }
  return content;
}

const NODES = [
  'component',
  'externalComponent',
  'stack',
  'bottomTabs',
  'sideMenu',
  'splitView',
] as const;

/** The options of the layout's top node. */
export function layoutOptions(layout: Layout): Options | undefined {
  for (const key of NODES) {
    const node = layout[key];
    if (node) return node.options;
  }
  return undefined;
}

/** The layout with options merged into its top node. */
export function mergeLayoutOptions(layout: Layout, options: Options): Layout {
  for (const key of NODES) {
    const node = layout[key];
    if (node) {
      return {
        ...layout,
        [key]: { ...node, options: mergeOptions(node.options ?? {}, options) },
      };
    }
  }
  return layout;
}

/** The layout with an id on its top node. */
export function setLayoutId(layout: Layout, id: string): Layout {
  for (const key of NODES) {
    const node = layout[key];
    if (node) return { ...layout, [key]: { ...node, id } };
  }
  return layout;
}

/** The element with modifiers for one platform only. */
export function onPlatform<Element>(
  os: 'ios' | 'android',
  element: Element,
  build: (element: Element) => Element
): Element {
  return Platform.OS === os ? build(element) : element;
}
