import type { NavigationButtonPressedEvent } from '../interfaces/ComponentEvents';
import type {
  Options,
  OptionsFab,
  OptionsTopBar,
  OptionsTopBarButton,
} from '../interfaces/Options';
import { toScreenEvent } from './events';
import { onNativeEvent } from './native';

// What a press of a button or of an item of its menu calls; the API gives the event its form (see `setPressEvent`).
type PressHandler = (event: any) => void;

type WithPress = {
  id?: string;
  onPress?: PressHandler;
  menu?: { items?: unknown[] };
  items?: unknown[];
  actions?: unknown[];
};

// The press handlers of the buttons of each screen, and of the items of their menus: screen id → id → handler.
const handlers = new Map<string, Map<string, PressHandler>>();
let pressEvent: (
  event: ReturnType<typeof toScreenEvent<NavigationButtonPressedEvent>>
) => unknown = (event) => event;
let listening = false;

/** What a press handler gets: the API adds the navigation of the screen to the event. */
export function setPressEvent(make: typeof pressEvent): void {
  pressEvent = make;
}

/** The options without the press handlers of their buttons, menu items and FAB, which the screen keeps. */
export function takeButtonHandlers(screenId: string, options: Options): Options {
  const found = new Map<string, PressHandler>();
  // A menu item sends a press with its own id, as a button does.
  const stripItems = (items: unknown[] | undefined): unknown[] | undefined =>
    items?.map((item) => {
      const { onPress, ...rest } = item as WithPress;
      if (onPress && rest.id) found.set(rest.id, onPress);
      return rest.items ? { ...rest, items: stripItems(rest.items) } : onPress ? rest : item;
    });
  const strip = (buttons: unknown[] | undefined) =>
    buttons?.map((item) => {
      const { onPress, ...button } = item as WithPress;
      if (onPress && button.id) found.set(button.id, onPress);
      const menu = button.menu?.items
        ? { ...button.menu, items: stripItems(button.menu.items) }
        : button.menu;
      return onPress || menu !== button.menu ? { ...button, menu } : item;
    });
  let result = options;
  if (options.topBar) {
    const topBar: OptionsTopBar = { ...options.topBar };
    for (const side of ['leftButtons', 'rightButtons', 'centerButtons'] as const) {
      if (topBar[side]) topBar[side] = strip(topBar[side]) as OptionsTopBarButton[];
    }
    result = { ...result, topBar };
  }
  if (options.toolbar?.buttons) {
    const buttons = strip(options.toolbar.buttons) as NonNullable<Options['toolbar']>['buttons'];
    result = { ...result, toolbar: { ...options.toolbar, buttons } };
  }
  // The FAB and its actions send a press with their own id, as a button does.
  const stripFab = (fab: unknown): OptionsFab => {
    const { onPress, ...rest } = fab as WithPress & OptionsFab;
    if (onPress && rest.id) found.set(rest.id, onPress);
    return rest.actions ? { ...rest, actions: rest.actions.map(stripFab) } : rest;
  };
  if (options.fab) result = { ...result, fab: stripFab(options.fab) };
  if (found.size > 0) {
    listen();
    const own = handlers.get(screenId) ?? new Map<string, PressHandler>();
    found.forEach((handler, id) => own.set(id, handler));
    handlers.set(screenId, own);
  }
  return result;
}

export function clearButtonHandlers(screenId: string) {
  handlers.delete(screenId);
}

function listen() {
  if (listening) return;
  listening = true;
  onNativeEvent<NavigationButtonPressedEvent>('NavigationButtonPressed', (event) => {
    handlers.get(event.componentId)?.get(event.buttonId)?.(pressEvent(toScreenEvent(event)));
  });
}
