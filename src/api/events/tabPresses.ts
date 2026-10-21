import { onNativeEvent } from '../../engine/native';
import type { TabPressEvent, TabPressHandler } from '../../engine/tabs';
import type { BottomTabPressedEvent } from '../../interfaces/Events';

export type { TabPressEvent, TabPressHandler } from '../../engine/tabs';

// The handlers of `useTabPress`, by the id of their screen. Those of `Tab().onPress()` go with the tabs to the engine.
const screenHandlers = new Map<string, Set<TabPressHandler>>();
let listening = false;

/** Calls the handler when the tab of the screen is pressed while it shows the screen; returns the function to stop. */
export function onScreenTabPress(screenId: string, handler: TabPressHandler): () => void {
  listen();
  const handlers = screenHandlers.get(screenId) ?? new Set<TabPressHandler>();
  handlers.add(handler);
  screenHandlers.set(screenId, handlers);
  return () => {
    handlers.delete(handler);
    if (handlers.size === 0) screenHandlers.delete(screenId);
  };
}

function listen() {
  if (listening) return;
  listening = true;
  onNativeEvent<BottomTabPressedEvent>('BottomTabPressed', (event) => {
    if (!event.screenId) return;
    const press: TabPressEvent = {
      index: event.tabIndex,
      wasSelected: event.wasSelected,
      screenId: event.screenId,
    };
    screenHandlers.get(event.screenId)?.forEach((handler) => handler(press));
  });
}
