import type { Layout, LayoutBottomTabs } from '../../interfaces/Layout';
import type {
  BottomTabsCustomRowOptions,
  DotIndicatorOptions,
  ImageResource,
  Options,
  OptionsBottomTab,
  OptionsBottomTabs,
} from '../../interfaces/Options';
import {
  TAB_PRESS_HANDLERS,
  type TabPressHandler,
  type WithTabPressHandlers,
} from '../../engine/tabs';
import { mergeOptions } from '../../engine/mergeOptions';
import { isReactScreen, registerScreen } from '../../engine/screens';
import {
  BUILD,
  buildContent,
  type LayoutContent,
  type LayoutElement,
  mergeLayoutOptions,
  onPlatform,
  type ScreenUse,
  setLayoutId,
  type ShowsScreens,
  type UsesOf,
} from './element';

/**
 * The text of a tab badge as the native side takes it, an empty one for none: a count of `0` is nothing to show, as
 * with unread messages.
 */
export function badgeText(badge: string | number | null): string {
  return badge === null || badge === 0 ? '' : String(badge);
}

/** The look of a tab item; `Tabs().itemStyle()` gives it to every tab, a tab's own goes over it. */
export type TabItemStyle = Pick<
  OptionsBottomTab,
  | 'iconColor'
  | 'selectedIconColor'
  | 'textColor'
  | 'selectedTextColor'
  | 'badgeColor'
  | 'fontFamily'
  | 'fontStyle'
  | 'fontWeight'
  | 'fontSize'
  | 'selectedFontSize'
  | 'iconInsets'
  | 'iconWidth'
  | 'iconHeight'
  | 'disableIconTint'
  | 'disableSelectedIconTint'
>;

const BUILD_TAB = Symbol('apexnative.navigation.buildTab');

// A tab without content is a button: it holds a screen that renders nothing and never shows, as a tab needs one.
const BUTTON_TAB_SCREEN = 'apexnative.navigation.buttonTab';

function buttonTabContent(): Layout {
  if (!isReactScreen(BUTTON_TAB_SCREEN)) {
    registerScreen(BUTTON_TAB_SCREEN, { component: () => () => null, options: () => ({}) });
  }
  return { component: { name: BUTTON_TAB_SCREEN } };
}
const PRESS_HANDLER = Symbol('apexnative.navigation.tabPressHandler');

/**
 * A tab: a screen named alone gets a stack around it. The tab's item options go on the layout of the tab, not on its
 * first screen: the layout stays in the options a tab reads whatever screen is on top of it.
 */
export class TabElement<Uses extends ScreenUse = ScreenUse> {
  constructor(
    private readonly content: LayoutContent | undefined,
    private readonly item: OptionsBottomTab = {},
    private readonly tabId?: string,
    private readonly pressHandler?: TabPressHandler
  ) {}

  private withItem(item: OptionsBottomTab): TabElement<Uses> {
    return new TabElement(
      this.content,
      mergeOptions(this.item, item),
      this.tabId,
      this.pressHandler
    );
  }

  label(label: string): TabElement<Uses> {
    return this.withItem({ text: label });
  }

  icon(icon: ImageResource): TabElement<Uses> {
    return this.withItem({ icon });
  }

  /** #### (iOS specific) */
  selectedIcon(selectedIcon: ImageResource): TabElement<Uses> {
    return this.withItem({ selectedIcon });
  }

  /** The badge of the tab: a count or a short text. `null` and the number `0` show none; the text `'0'` shows "0". */
  badge(badge: string | number | null): TabElement<Uses> {
    return this.withItem({ badge: badgeText(badge) });
  }

  dot(dot: boolean | DotIndicatorOptions = true): TabElement<Uses> {
    return this.withItem({
      dotIndicator: typeof dot === 'boolean' ? { visible: dot } : { visible: true, ...dot },
    });
  }

  /** #### (iOS specific) */
  role(role: NonNullable<OptionsBottomTab['role']>): TabElement<Uses> {
    return this.withItem({ role });
  }

  /**
   * A tap on the tab while it is selected goes back to the first screen of its stack, and on the first screen scrolls
   * it to the top. On by default; `false` turns it off.
   */
  popToRootOnPress(popToRootOnPress = true): TabElement<Uses> {
    return this.withItem({ popToRootOnPress });
  }

  /** `false`: a tap does not select the tab, it only sends `bottomTabPressed`. */
  selectOnPress(selectTabOnPress: boolean): TabElement<Uses> {
    return this.withItem({ selectTabOnPress });
  }

  /**
   * Called on every press of the tab, a second one too (`wasSelected`). It runs after the native side has acted on
   * the press: with `.selectOnPress(false)` the tab stays unselected and the handler decides what to do.
   */
  onPress(handler: TabPressHandler): TabElement<Uses> {
    return new TabElement(this.content, this.item, this.tabId, handler);
  }

  /** When the user leaves the tab, its stack goes back to its first screen, so the tab opens on it next time. */
  popToRootOnBlur(popToRootOnBlur = true): TabElement<Uses> {
    return this.withItem({ popToRootOnBlur });
  }

  itemStyle(style: TabItemStyle): TabElement<Uses> {
    return this.withItem(style);
  }

  /** #### (iOS specific, iOS 18+) With `Tabs().customizable()`. */
  customization(
    customization: NonNullable<OptionsBottomTab['customization']>,
    more: Pick<OptionsBottomTab, 'shownByDefault' | 'allowsHiding'> = {}
  ): TabElement<Uses> {
    return this.withItem({ customization, ...more });
  }

  testID(testID: string): TabElement<Uses> {
    return this.withItem({ testID });
  }

  /** Modifiers for iOS only. */
  ios(build: (tab: TabElement<Uses>) => TabElement<Uses>): TabElement<Uses> {
    return onPlatform<TabElement<Uses>>('ios', this, build);
  }

  /** Modifiers for Android only. */
  android(build: (tab: TabElement<Uses>) => TabElement<Uses>): TabElement<Uses> {
    return onPlatform<TabElement<Uses>>('android', this, build);
  }

  /** The id of the tab, for `Tabs().selected()` and to address it. */
  id(id: string): TabElement<Uses> {
    return new TabElement(this.content, this.item, id, this.pressHandler);
  }

  /** Options of the tab item no modifier covers, such as `disableIconTint`. The options of screens go on the screens. */
  options(options: OptionsBottomTab): TabElement<Uses> {
    return this.withItem(options);
  }

  get [PRESS_HANDLER](): TabPressHandler | undefined {
    return this.pressHandler;
  }

  [BUILD_TAB](style: TabItemStyle): Layout {
    const button = this.content === undefined;
    // A button tab is never selected: its press is all it does.
    const item = mergeOptions<OptionsBottomTab>(
      mergeOptions<OptionsBottomTab>(style, this.item),
      button ? { selectTabOnPress: false } : {}
    );
    const content = button ? buttonTabContent() : buildContent(this.content!, 'stack');
    const layout = mergeLayoutOptions(content, { bottomTab: item });
    return this.tabId ? setLayoutId(layout, this.tabId) : layout;
  }
}

/** Bottom tabs. */
export class TabsElement<Uses extends ScreenUse = ScreenUse> implements LayoutElement<Uses> {
  constructor(
    private readonly tabs: readonly TabElement<any>[],
    private readonly built: Options = {},
    private readonly style: TabItemStyle = {},
    private readonly tabsId?: string
  ) {}

  private withOptions(options: Options): TabsElement<Uses> {
    return new TabsElement(this.tabs, mergeOptions(this.built, options), this.style, this.tabsId);
  }

  private withTabs(bottomTabs: OptionsBottomTabs): TabsElement<Uses> {
    return this.withOptions({ bottomTabs });
  }

  /** The tab to show first: its index, or its id. */
  selected(tab: number | string): TabsElement<Uses> {
    return this.withTabs(
      typeof tab === 'number' ? { currentTabIndex: tab } : { currentTabId: tab }
    );
  }

  /** Each tab is created the first time it shows. */
  lazy(): TabsElement<Uses> {
    return this.withTabs({ tabsAttachMode: 'onSwitchToTab' });
  }

  /** The look of every tab item; a tab's own goes over it. */
  itemStyle(style: TabItemStyle): TabsElement<Uses> {
    return new TabsElement(this.tabs, this.built, mergeOptions(this.style, style), this.tabsId);
  }

  /** #### (iOS specific, iOS 26+) */
  minimize(
    minimizeBehavior: NonNullable<OptionsBottomTabs['minimizeBehavior']>
  ): TabsElement<Uses> {
    return this.withTabs({ minimizeBehavior });
  }

  /** #### (iOS specific, iOS 26+) A screen of `screens` above the tab bar, as the mini player of Apple Music. */
  accessory<Name extends string, const Props extends object | undefined = undefined>(
    name: Name,
    props?: Props
  ): TabsElement<Uses | { name: Name; props: Props }> {
    return this.withTabs({
      bottomAccessory: {
        component: { name, ...(props && { passProps: props }) },
      },
    });
  }

  /** #### (iOS specific, iOS 18+) How the tabs show on iPad. */
  sidebar(
    sidebar: NonNullable<OptionsBottomTabs['sidebar']>,
    sidebarLayout?: OptionsBottomTabs['sidebarLayout']
  ): TabsElement<Uses> {
    return this.withTabs({ sidebar, sidebarLayout });
  }

  /** #### (iOS specific, iOS 18+) The user arranges the tabs; the id keeps the arrangement. */
  customizable(customizationId: string): TabsElement<Uses> {
    return this.withTabs({ customizationId });
  }

  /** The row of tab items drawn by components (`bottomTab.component`). */
  customRow(customRow: BottomTabsCustomRowOptions): TabsElement<Uses> {
    return this.withTabs({ customRow });
  }

  /** #### (Android specific) What back does on the first screen of a tab. */
  backBehavior(bottomTabsOnPress: 'exit' | 'first' | 'previous'): TabsElement<Uses> {
    return this.withOptions({ hardwareBackButton: { bottomTabsOnPress } });
  }

  /** Modifiers for iOS only. */
  ios(build: (tabs: TabsElement<Uses>) => TabsElement<Uses>): TabsElement<Uses> {
    return onPlatform<TabsElement<Uses>>('ios', this, build);
  }

  /** Modifiers for Android only. */
  android(build: (tabs: TabsElement<Uses>) => TabsElement<Uses>): TabsElement<Uses> {
    return onPlatform<TabsElement<Uses>>('android', this, build);
  }

  /**
   * The id of the tabs: the `bottomTabsId` of the tab events (`tabSelected`, `tabPressed`, `tabLongPressed`), to tell
   * two sets of tabs apart, such as the root's and those of a modal. Without it the id is generated. The commands take
   * the id of a tab (`Tab().id()`), not this one.
   */
  id(id: string): TabsElement<Uses> {
    return new TabsElement(this.tabs, this.built, this.style, id);
  }

  /** Options of the tab bar, such as `titleDisplayMode`. The options of screens go on the screens. */
  options(options: OptionsBottomTabs): TabsElement<Uses> {
    return this.withTabs(options);
  }

  [BUILD](): Layout {
    const handlers = new Map<number, TabPressHandler>();
    this.tabs.forEach((tab, index) => {
      const handler = tab[PRESS_HANDLER];
      if (handler) handlers.set(index, handler);
    });
    // The press handlers go with the layout: the engine keeps them under the id the tabs are shown with, while they are.
    const bottomTabs: LayoutBottomTabs & WithTabPressHandlers = {
      ...(this.tabsId && { id: this.tabsId }),
      children: this.tabs.map((tab) => tab[BUILD_TAB](this.style)),
      options: this.built,
      ...(handlers.size > 0 && { [TAB_PRESS_HANDLERS]: handlers }),
    };
    return { bottomTabs };
  }
}

// The type of an element carries the screens it shows.
export interface TabElement<Uses extends ScreenUse = ScreenUse> extends ShowsScreens<Uses> {}
export interface TabsElement<Uses extends ScreenUse = ScreenUse> extends ShowsScreens<Uses> {}

type UsesOfTab<Tab> = Tab extends TabElement<infer Uses> ? Uses : never;

/**
 * A tab with its content: a screen by name, `Screen(…)`, or a layout. Without content the tab is a button: it never
 * becomes current, and `onPress` decides what it does, such as a New tab that opens a modal.
 */
export function Tab(): TabElement<never>;
export function Tab<const Content extends LayoutContent>(
  content: Content
): TabElement<UsesOf<Content>>;
export function Tab(content?: LayoutContent): TabElement<any> {
  return new TabElement(content);
}

export function Tabs<const Items extends readonly TabElement<any>[]>(
  ...tabs: Items
): TabsElement<UsesOfTab<Items[number]>> {
  return new TabsElement(tabs);
}
