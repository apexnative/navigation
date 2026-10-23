import { useContext, useMemo } from 'react';
import { setPressEvent } from '../../engine/buttons';
import * as commands from '../commands/commands';
import type { SplitColumn } from '../commands/commands';
import { command } from '../commands/errors';
import type {
  ColumnConfig,
  ModalConfig,
  OverlayConfig,
  PushConfig,
} from '../commands/presentation';
import type { StackEntry } from '../layout/Stack';
import { type AppNavigation, navigation, type CheckedEntries } from '../navigation';
import type { ScreenOptionsInput } from '../screens/defineScreen';
import { ScreenContext, type ScreenInfo } from '../screens/registerScreens';
import { whenTabShows } from '../state/navigationState';
import { placeOf } from '../../engine/places';
import { centerOf, closeMenu, openMenu } from '../commands/sideMenus';
import type { ScreenName, ShowArgs } from '../types';

/** The commands that act from the screen, so they take no ids. */
interface ScreenCommands {
  /** The screen the commands act from. */
  readonly screen: ScreenInfo;
  /** Shows a screen over this one in its stack; resolves with the id of the screen shown (for a pair, the pair's id). */
  push<Name extends ScreenName>(name: Name, ...args: ShowArgs<Name, PushConfig>): Promise<string>;
  /**
   * Goes back to the screen before this one in its stack. The first screen of a split view column shown over the list
   * (a phone) goes back to the list.
   */
  pop(): Promise<string>;
  /** Goes back to a screen of this stack, by its id. */
  popTo(screenId: string): Promise<string>;
  /** Goes back to the first screen of this stack. */
  popToRoot(): Promise<string>;
  /**
   * Shows a screen in place of the whole history of this stack: it comes in as a push does and is alone in the stack,
   * so there is no back. For a point the user shouldn't go back from, such as a payment once it is made. Resolves with
   * the id of the screen shown.
   */
  replace<Name extends ScreenName>(
    name: Name,
    ...args: ShowArgs<Name, PushConfig>
  ): Promise<string>;
  /** Replaces the screens of this stack: the last one shows, back goes to the one before. */
  setStack<const Entries extends readonly StackEntry[]>(
    ...entries: CheckedEntries<Entries>
  ): Promise<string>;
  /**
   * Shows a screen in a column of the split view this screen is in, in place of what the column shows: Details beside
   * a list. The column comes into view: a sidebar over it goes away; collapsed into one stack (iPhone, a narrow
   * window) the screen goes on top, and back comes here; on Android the secondary pane comes over the primary one.
   * With a `compact` layout on screen and outside a split view the screen is pushed over this one.
   *
   * @example
   * // Settings: sections in the sidebar, the picked one beside them.
   * navigation.switchTo(
   *   SplitView({ primary: Screen('Settings'), secondary: Screen('Details') })
   * );
   *
   * function Settings() {
   *   const navigation = useNavigation();
   *   const open = (section: string) =>
   *     navigation.showInColumn('Details', { props: { section } });
   *   return <Button title="Profile" onPress={() => open('profile')} />;
   * }
   */
  showInColumn<Name extends ScreenName>(
    name: Name,
    ...args: ShowArgs<Name, ColumnConfig>
  ): Promise<string>;
  /** Shows a screen over everything, in a stack of its own unless `stack: false`. */
  showModal<Name extends ScreenName>(
    name: Name,
    ...args: ShowArgs<Name, ModalConfig>
  ): Promise<string>;
  /** Closes the modal or the overlay this screen is in, with all its screens. */
  dismiss(): Promise<string>;
  /**
   * There is a screen under this one in its stack, or this one is the first of a split view column shown over the list
   * (a phone), so `pop()` has one to go back to. Answers at once.
   */
  canPop(): boolean;
  /** This screen is in a modal or an overlay, so `dismiss()` has one to close. Answers at once. */
  canDismiss(): boolean;
  /**
   * What the back button would do here: `pop()` when there is a screen under this one in its stack, `dismiss()` when it
   * is the first screen of a modal. For a screen that opens both ways, pushed in one place and in a modal in another.
   */
  goBack(): Promise<string>;
  /** `goBack()` has something to do: `canPop()` or `canDismiss()`. Answers at once. */
  canGoBack(): boolean;
  /** Shows a screen over everything, without taking the place of any. */
  showOverlay<Name extends ScreenName>(
    name: Name,
    ...args: ShowArgs<Name, OverlayConfig>
  ): Promise<string>;
  /**
   * Shows a column of the split view this screen is in, as the platform does for the split behavior: the sidebar
   * (`'primary'`), the middle column (`'supplementary'`), the inspector. Resolves once the columns have moved. On
   * Android the primary pane: beside the secondary one it comes back, under it the secondary pane goes away.
   */
  showColumn(column: SplitColumn): Promise<void>;
  /** Hides a column of the split view this screen is in: `hideColumn('primary')` leaves the item alone. */
  hideColumn(column: SplitColumn): Promise<void>;
  /** Opens a menu of the side menu this screen is in; resolves once it is open and `getState()` shows its screen. */
  openMenu(side: 'left' | 'right'): Promise<void>;
  /**
   * Closes the menu that is open in the side menu this screen is in; resolves once it is closed and `getState()` shows
   * the center.
   */
  closeMenu(): Promise<void>;
  /** Changes the options of this screen. */
  setOptions(options: ScreenOptionsInput<object>): void;
  /**
   * Selects a tab of the tabs this screen is in: by its index, or by the id of the tab (`Tab().id()`). Resolves once the
   * state shows it, as `navigation.selectTab` does.
   */
  selectTab(tab: number | string): Promise<void>;
  /** The badge of this screen's tab, or of the tab with the id; `null` and the number `0` take it away. */
  setTabBadge(badge: string | number | null, tab?: string): Promise<void>;
}

/**
 * The navigation of a screen: its own commands act from it and take no ids, and the commands of the whole app are
 * here too (`switchTo`, `dismissAllModals`, `getState`, `on`, the theme and the context), so a screen needs nothing
 * else.
 */
export interface ScreenNavigation
  extends Omit<AppNavigation, keyof ScreenCommands>, ScreenCommands {}

/** The navigation of the screen the component is in. */
export function useNavigation(): ScreenNavigation {
  const screen = useContext(ScreenContext);
  if (!screen) {
    throw new Error(
      '[navigation] useNavigation is for the screens of createNavigation, and this component is not in one'
    );
  }
  return useMemo(() => screenNavigation(screen), [screen]);
}

// The commands of a stack, which a screen makes from where it is.
type StackCommands = Pick<
  ScreenNavigation,
  | 'push'
  | 'pop'
  | 'popToRoot'
  | 'replace'
  | 'setStack'
  | 'showInColumn'
  | 'selectTab'
  | 'setTabBadge'
>;

function stackCommands(id: string): StackCommands {
  return {
    push: ((name: string, config?: PushConfig) =>
      commands.push(id, name, config)) as StackCommands['push'],
    pop: () => commands.pop(id),
    popToRoot: () => commands.popToRoot(id),
    replace: ((name: string, config?: PushConfig) =>
      commands.replace(id, name, config)) as StackCommands['replace'],
    setStack: ((...entries: StackEntry[]) =>
      commands.setStack(id, entries)) as StackCommands['setStack'],
    showInColumn: ((name: string, config?: ColumnConfig) =>
      commands.showInColumn(id, name, config)) as StackCommands['showInColumn'],
    // Resolves once the state shows the tab, so the next command acts in it.
    selectTab: (tab) =>
      command('selectTab', async () => {
        const shown = whenTabShows(tab);
        commands.selectTab(id, tab);
        await shown;
      }),
    setTabBadge: (badge, tab) =>
      command('setTabBadge', async () => commands.setTabBadge(id, badge, tab)),
  };
}

/**
 * The commands of the center for a menu screen: the menu closes as the center changes, and the command resolves once
 * both are done.
 */
function fromCenter(center: string, menuScreen: string): StackCommands {
  const centerCommands = stackCommands(center);
  // As in the system's apps, the menu starts to close at the pick and the center changes as it goes, so a push is seen
  // sliding in. The command settles after both, so the next one acts from the center; either one's error rejects it.
  const closing =
    <Args extends unknown[], Result>(run: (...args: Args) => Promise<Result>) =>
    async (...args: Args) => {
      const closed = closeMenu(menuScreen);
      const [result] = await Promise.all([run(...args), closed]);
      return result;
    };
  return {
    push: closing(
      centerCommands.push as (...args: unknown[]) => Promise<string>
    ) as StackCommands['push'],
    pop: closing(centerCommands.pop),
    popToRoot: closing(centerCommands.popToRoot),
    replace: closing(
      centerCommands.replace as (...args: unknown[]) => Promise<string>
    ) as StackCommands['replace'],
    setStack: closing(
      centerCommands.setStack as (...args: unknown[]) => Promise<string>
    ) as StackCommands['setStack'],
    showInColumn: closing(
      centerCommands.showInColumn as (...args: unknown[]) => Promise<string>
    ) as StackCommands['showInColumn'],
    selectTab: closing(centerCommands.selectTab),
    setTabBadge: centerCommands.setTabBadge,
  };
}

/** The navigation of a screen, for code outside React that knows the screen. */
export function screenNavigation(screen: ScreenInfo): ScreenNavigation {
  const id = screen.id;
  const own = stackCommands(id);
  // A screen without a stack of its own acts from elsewhere: an overlay from the screen the user sees (a banner opens a
  // chat), a menu from the center of its side menu.
  const stack = (): StackCommands => {
    const place = placeOf(id);
    if (place?.container === 'overlay') return navigation;
    if (place?.menu && place.menu.part !== 'center') {
      const center = centerOf(place.menu.id);
      if (center) return fromCenter(center, id);
    }
    return own;
  };
  return {
    ...navigation,
    screen,
    push: ((...args: unknown[]) =>
      (stack().push as (...args: unknown[]) => Promise<string>)(
        ...args
      )) as ScreenNavigation['push'],
    pop: () => stack().pop(),
    popTo: (screenId) => commands.popTo(screenId),
    popToRoot: () => stack().popToRoot(),
    replace: ((...args: unknown[]) =>
      (stack().replace as (...args: unknown[]) => Promise<string>)(
        ...args
      )) as ScreenNavigation['replace'],
    setStack: ((...args: unknown[]) =>
      (stack().setStack as (...args: unknown[]) => Promise<string>)(
        ...args
      )) as ScreenNavigation['setStack'],
    showInColumn: ((...args: unknown[]) =>
      (stack().showInColumn as (...args: unknown[]) => Promise<string>)(
        ...args
      )) as ScreenNavigation['showInColumn'],
    showModal: ((name: string, config?: ModalConfig) =>
      commands.showModal(name, config)) as ScreenNavigation['showModal'],
    dismiss: () => commands.dismiss(id),
    canPop: () => commands.canPop(id),
    canDismiss: () => commands.canDismiss(id),
    goBack: () => commands.goBack(id),
    canGoBack: () => commands.canPop(id) || commands.canDismiss(id),
    showOverlay: ((name: string, config?: OverlayConfig) =>
      commands.showOverlay(name, config)) as ScreenNavigation['showOverlay'],
    setOptions: (options) => commands.setOptions(id, options),
    showColumn: (column) => commands.setColumnShown(id, column, true),
    hideColumn: (column) => commands.setColumnShown(id, column, false),
    openMenu: (side) => openMenu(id, side),
    closeMenu: () => closeMenu(id),
    selectTab: (tab) => stack().selectTab(tab),
    setTabBadge: (badge, tab) => stack().setTabBadge(badge, tab),
  };
}

// A press of a button in the options gets the navigation of its screen.
setPressEvent((event) => ({ ...event, navigation: screenNavigation(event.screen) }));
